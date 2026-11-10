// BaoCaoSuCo C01 shared evidence gateway (default DENY; security sign-off required).
// This function intentionally does NOT rely on Supabase JWT verification because
// the mobile client carries Firebase Auth ID tokens. It verifies RS256 signature
// against Google's Firebase public JWK set, issuer, audience, expiry and subject.
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.10.0";
import { createClient } from "npm:@supabase/supabase-js@2.57.0";
import { assertFirebaseAccountActive, FirebaseAuthStatusError } from "./revocation-check.mjs";

const FIREBASE_PROJECT_ID = "baocaosuco-c4ccc";
const BUCKET = "incident-evidence";
const MAX_BYTES = 2 * 1024 * 1024;
const ALLOWED_ID = /^[A-Za-z0-9_-]{1,128}$/;
const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i;
const JWKS = createRemoteJWKSet(new URL(
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
));

class ApiError extends Error {
  status: number;
  code: string;
  constructor(status: number, code: string) {
    super(code);
    this.status = status;
    this.code = code;
  }
}

function json(status: number, object: Record<string, unknown>) {
  return new Response(JSON.stringify(object), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

async function verifyFirebase(req: Request) {
  const authorization = req.headers.get("authorization") || "";
  if (!authorization.startsWith("Bearer ")) throw new ApiError(401, "FIREBASE_AUTH_REQUIRED");
  const token = authorization.slice(7);
  if (token.length < 100 || token.length > 8192 || token.split(".").length !== 3) {
    throw new ApiError(401, "INVALID_FIREBASE_TOKEN");
  }
  try {
    const verified = await jwtVerify(token, JWKS, {
      algorithms: ["RS256"],
      issuer: "https://securetoken.google.com/" + FIREBASE_PROJECT_ID,
      audience: FIREBASE_PROJECT_ID,
      clockTolerance: 15
    });
    const payload = verified.payload;
    const uid = payload.sub;
    const now = Math.floor(Date.now() / 1000);
    if (typeof uid !== "string" || !ALLOWED_ID.test(uid) ||
        typeof payload.auth_time !== "number" ||
        payload.auth_time > now + 15 ||
        typeof payload.iat !== "number" || payload.iat > now + 15) {
      throw new Error("Invalid Firebase identity");
    }
    return { token, uid, authTime: payload.auth_time };
  } catch {
    throw new ApiError(401, "INVALID_FIREBASE_TOKEN");
  }
}

async function firestoreDocument(token: string, collection: 'users' | 'incidents', documentId: string) {
  // No arbitrary URL/collection is accepted: prevents proxying Firebase data.
  if (!["users", "incidents"].includes(collection)) throw new ApiError(500, "CONFIG_ERROR");
  if (!ALLOWED_ID.test(documentId)) throw new ApiError(400, "INVALID_ID");
  const url = "https://firestore.googleapis.com/v1/projects/" +
    FIREBASE_PROJECT_ID + "/databases/(default)/documents/" +
    collection + "/" + encodeURIComponent(documentId);
  let res;
  try {
    res = await fetch(url, {
      headers: { "Authorization": "Bearer " + token },
      signal: AbortSignal.timeout(10000)
    });
  } catch {
    throw new ApiError(503, "FIRESTORE_UNAVAILABLE");
  }
  if (res.status === 401 || res.status === 403 || res.status === 404) {
    throw new ApiError(403, "INCIDENT_ACCESS_DENIED");
  }
  if (!res.ok) throw new ApiError(503, "FIRESTORE_UNAVAILABLE");
  const body = await res.json();
  if (!body || typeof body !== "object" || !body.fields) {
    throw new ApiError(503, "FIRESTORE_INVALID_RESPONSE");
  }
  return body.fields;
}

function firestoreString(fields: Record<string, { stringValue?: string }> | null, name: string) {
  const value = fields?.[name]?.stringValue;
  return typeof value === "string" ? value : null;
}

async function authorizeIncident(identity: {token: string; uid: string; authTime: number}, incidentId: string) {
  const profile = await firestoreDocument(identity.token, "users", identity.uid);
  const role = firestoreString(profile, "role");
  if (firestoreString(profile, "uid") !== identity.uid ||
      (role !== "RESIDENT" && role !== "MANAGER")) {
    throw new ApiError(403, "PROFILE_ACCESS_DENIED");
  }
  const incident = await firestoreDocument(identity.token, "incidents", incidentId);
  const residentId = firestoreString(incident, "residentId");
  const status = firestoreString(incident, "status");
  if (!residentId || !status ||
      (role === "RESIDENT" && residentId !== identity.uid)) {
    throw new ApiError(403, "INCIDENT_ACCESS_DENIED");
  }
  return { role, status };
}

function adminClient() {
  const url = Deno.env.get("SUPABASE_URL");
  let secret = "";
  try {
    const keys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") || "{}");
    if (typeof keys.default === "string") secret = keys.default;
  } catch {
    // Legacy key fallback below.
  }
  if (!secret) secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  if (!url || !secret) throw new ApiError(503, "SERVER_KEY_NOT_CONFIGURED");
  return createClient(url, secret, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { "X-Client-Info": "baocaosuco-evidence-gateway" } }
  });
}

async function readJpeg(req: Request) {
  const contentType = (req.headers.get("content-type") || "").split(";")[0].trim().toLowerCase();
  if (contentType !== "image/jpeg") throw new ApiError(415, "JPEG_REQUIRED");
  const len = Number(req.headers.get("content-length"));
  if (Number.isFinite(len) && len > MAX_BYTES) throw new ApiError(413, "PHOTO_TOO_LARGE");
  if (!req.body) throw new ApiError(400, "PHOTO_REQUIRED");
  const reader = req.body.getReader();
  const parts = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > MAX_BYTES) {
        await reader.cancel();
        throw new ApiError(413, "PHOTO_TOO_LARGE");
      }
      parts.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  if (total < 5) throw new ApiError(415, "INVALID_JPEG");
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    bytes.set(part, offset);
    offset += part.byteLength;
  }
  if (bytes[0] !== 255 || bytes[1] !== 216 || bytes[2] !== 255 ||
      bytes[total - 2] !== 255 || bytes[total - 1] !== 217) {
    throw new ApiError(415, "INVALID_JPEG");
  }
  return bytes;
}

function publicMetadata(row: {id: string; incident_id: string; kind: string; uploaded_by: string; size_bytes: number; created_at: string}) {
  return {
    id: row.id,
    incidentId: row.incident_id,
    kind: row.kind,
    uploadedBy: row.uploaded_by,
    bytes: row.size_bytes,
    createdAt: row.created_at
  };
}

async function listEvidence(admin: ReturnType<typeof createClient>, incidentId: string) {
  const { data, error } = await admin.from("incident_evidence")
    .select("id,incident_id,kind,uploaded_by,size_bytes,created_at")
    .eq("incident_id", incidentId).order("created_at", { ascending: true });
  if (error) throw new ApiError(503, "EVIDENCE_DATABASE_UNAVAILABLE");
  return json(200, { items: (data || []).map(publicMetadata) });
}

async function getPhoto(admin: ReturnType<typeof createClient>, incidentId: string, photoId: string) {
  if (!UUID.test(photoId)) throw new ApiError(400, "INVALID_PHOTO_ID");
  const { data, error } = await admin.from("incident_evidence")
    .select("storage_path,content_type,size_bytes")
    .eq("incident_id", incidentId).eq("id", photoId).maybeSingle();
  if (error) throw new ApiError(503, "EVIDENCE_DATABASE_UNAVAILABLE");
  if (!data) throw new ApiError(404, "PHOTO_NOT_FOUND");
  const result = await admin.storage.from(BUCKET).download(data.storage_path);
  if (result.error || !result.data) throw new ApiError(503, "PHOTO_UNAVAILABLE");
  return new Response(result.data, {
    status: 200,
    headers: {
      "Content-Type": "image/jpeg",
      "Content-Length": String(data.size_bytes),
      "Cache-Control": "private, no-store",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

async function createEvidence(req: Request, admin: ReturnType<typeof createClient>, identity: {token: string; uid: string; authTime: number}, incidentId: string, kind: string, access: {role: 'RESIDENT' | 'MANAGER'; status: string}) {
  if (kind !== "REPORTED" && kind !== "RESOLUTION") {
    throw new ApiError(400, "INVALID_EVIDENCE_KIND");
  }
  if (kind === "REPORTED") {
    if (access.role !== "RESIDENT" ||
        !["NEW", "ASSIGNED", "IN_PROGRESS"].includes(access.status)) {
      throw new ApiError(403, "EVIDENCE_WRITE_DENIED");
    }
  } else if (access.role !== "MANAGER" ||
             !["IN_PROGRESS", "COMPLETED"].includes(access.status)) {
    throw new ApiError(403, "EVIDENCE_WRITE_DENIED");
  }

  // Avoid pointless Storage writes on duplicate kind; DB uniqueness still
  // arbitrates concurrent uploads after this read.
  const prior = await admin.from("incident_evidence")
    .select("id").eq("incident_id", incidentId).eq("kind", kind).maybeSingle();
  if (prior.error) throw new ApiError(503, "EVIDENCE_DATABASE_UNAVAILABLE");
  if (prior.data) throw new ApiError(409, "EVIDENCE_ALREADY_EXISTS");

  const bytes = await readJpeg(req);
  const id = crypto.randomUUID();
  const path = incidentId + "/" + id + ".jpg";
  const uploaded = await admin.storage.from(BUCKET).upload(path, bytes, {
    contentType: "image/jpeg",
    cacheControl: "0",
    upsert: false
  });
  if (uploaded.error) throw new ApiError(503, "PHOTO_UPLOAD_FAILED");

  const inserted = await admin.from("incident_evidence").insert({
    id,
    incident_id: incidentId,
    kind,
    storage_path: path,
    uploaded_by: identity.uid,
    content_type: "image/jpeg",
    size_bytes: bytes.byteLength
  }).select("id,incident_id,kind,uploaded_by,size_bytes,created_at").single();

  if (inserted.error || !inserted.data) {
    // Best-effort compensation: avoid leaving orphaned private objects.
    const cleanup = await admin.storage.from(BUCKET).remove([path]);
    if (cleanup.error) console.error("evidence-orphan-cleanup-required");
    throw new ApiError(503, "EVIDENCE_METADATA_FAILED");
  }
  return json(201, { item: publicMetadata(inserted.data) });
}

Deno.serve(async (req) => {
  try {
    if (req.method !== "GET" && req.method !== "POST") {
      return json(405, { error: "METHOD_NOT_ALLOWED" });
    }
    const url = new URL(req.url);
    const incidentId = url.searchParams.get("incidentId") || "";
    if (!ALLOWED_ID.test(incidentId)) throw new ApiError(400, "INVALID_INCIDENT_ID");

    // Verify the token BEFORE any privileged operation. verify_jwt=false in
    // Supabase is required to support Firebase-signed tokens, not open access.
    const identity = await verifyFirebase(req);

    // The cross-cloud privacy contract requires an independent L3 reviewer.
    // Keep all actual data operations disabled until the release owner enables
    // EVIDENCE_ACCESS_ENABLED=true in Supabase Function secrets AFTER review.
    if (Deno.env.get("EVIDENCE_ACCESS_ENABLED") !== "true") {
      throw new ApiError(503, "EVIDENCE_GATE_NOT_APPROVED");
    }

    // G0: disabled/revoked-session check is mandatory before any privileged access.
    try {
      await assertFirebaseAccountActive(identity, Deno.env.get("FIREBASE_WEB_API_KEY"));
    } catch (error) {
      if (error instanceof FirebaseAuthStatusError) {
        throw new ApiError(error.status, error.code);
      }
      throw new ApiError(503, "FIREBASE_AUTH_STATUS_UNAVAILABLE");
    }
    const access = await authorizeIncident(identity, incidentId);
    const admin = adminClient();
    if (req.method === "GET") {
      const photoId = url.searchParams.get("photoId");
      return photoId ? await getPhoto(admin, incidentId, photoId)
                     : await listEvidence(admin, incidentId);
    }
    if (url.searchParams.has("photoId")) throw new ApiError(400, "INVALID_UPLOAD_PATH");
    return await createEvidence(req, admin, identity, incidentId,
      url.searchParams.get("kind") || "", access);
  } catch (error) {
    if (error instanceof ApiError) return json(error.status, { error: error.code });
    console.error("evidence-gateway-unexpected-error");
    return json(503, { error: "EVIDENCE_SERVICE_UNAVAILABLE" });
  }
});
