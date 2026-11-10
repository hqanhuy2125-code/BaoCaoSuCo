// C01-SRV v2 CANDIDATE ONLY. NEVER import an unverified identity here.
// Official endpoint: https://firebase.google.com/docs/reference/rest/auth#section-get-account-info
// A Firebase-signed token is checked BEFORE this module is invoked.
const ENDPOINT = "https://identitytoolkit.googleapis.com/v1/accounts:lookup";
const MAX_API_KEY = 256;
const LOOKUP_TIMEOUT_MS = 7000;

export class FirebaseAuthStatusError extends Error {
  constructor(status, code) {
    super(code);
    this.name = "FirebaseAuthStatusError";
    this.status = status;
    this.code = code;
  }
}

function denied() {
  return new FirebaseAuthStatusError(401, "FIREBASE_SESSION_NOT_ACTIVE");
}

function unavailable() {
  return new FirebaseAuthStatusError(503, "FIREBASE_AUTH_STATUS_UNAVAILABLE");
}

function validSinceSeconds(value) {
  // Firebase Auth REST represents validSince as a decimal seconds string.
  // Missing/malformed timestamps MUST NOT turn into an implicit allow.
  if (typeof value !== "string" || !/^(0|[1-9][0-9]{0,12})$/.test(value)) {
    throw unavailable();
  }
  const seconds = Number(value);
  if (!Number.isSafeInteger(seconds)) throw unavailable();
  return seconds;
}

/**
 * Checks current Firebase Auth account state *on every authorized operation*.
 * Input identity MUST be the previously RS256/issuer/audience/exp-verified JWT.
 * Firebase Web API key is project configuration, not an Admin/service-role key.
 * No successful-response caching. No token, PII, or API key logging.
 * A network/API/response anomaly denies access ("fail closed").
 */
export async function assertFirebaseAccountActive(identity, apiKey, options = {}) {
  if (!identity || typeof identity.uid !== "string" || !identity.uid ||
      typeof identity.token !== "string" || !identity.token ||
      !Number.isSafeInteger(identity.authTime) || identity.authTime < 0) {
    throw denied();
  }
  if (typeof apiKey !== "string" || apiKey.length < 8 ||
      apiKey.length > MAX_API_KEY || /[&?#\s]/.test(apiKey)) {
    throw unavailable();
  }
  const fetchImpl = options.fetchImpl || fetch;
  let response;
  try {
    response = await fetchImpl(ENDPOINT + "?key=" + encodeURIComponent(apiKey), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken: identity.token }),
      signal: AbortSignal.timeout(LOOKUP_TIMEOUT_MS),
      redirect: "error"
    });
  } catch {
    throw unavailable();
  }
  // Wrong/expired/revoked token may be rejected by Identity Toolkit itself.
  if (response.status === 400 || response.status === 401) throw denied();
  if (!response.ok) throw unavailable();

  let data;
  try {
    data = await response.json();
  } catch {
    throw unavailable();
  }
  if (!data || !Array.isArray(data.users) || data.users.length !== 1 ||
      !data.users[0] || typeof data.users[0] !== "object") {
    throw unavailable();
  }
  const user = data.users[0];
  if (user.localId !== identity.uid) throw denied();
  if (user.disabled === true) throw denied();
  // The REST API may omit disabled=false; never accept malformed values.
  if (user.disabled !== undefined && user.disabled !== false) throw unavailable();
  const validSince = validSinceSeconds(user.validSince);
  if (identity.authTime < validSince) throw denied();
  return { uid: identity.uid };
}
