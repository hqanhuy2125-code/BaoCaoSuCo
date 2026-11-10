import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { assertFirebaseAccountActive, FirebaseAuthStatusError } from "./revocation-check.mjs";

const identity = Object.freeze({
  token: "fake.signed.jwt-for-offline-tests-only",
  uid: "demoUid",
  authTime: 2000
});
const key = "firebaseWebApiKeyForTestsOnly";
function reply(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() { return body; }
  };
}
function record(overrides = {}) {
  return { users: [{ localId: identity.uid, validSince: "1900", disabled: false, ...overrides }] };
}
function fake(result, calls = []) {
  return async (url, options) => {
    calls.push({ url, options });
    return typeof result === "function" ? result(url, options) : result;
  };
}
async function pass(response, customIdentity = identity) {
  return assertFirebaseAccountActive(customIdentity, key, { fetchImpl: fake(response) });
}
async function rejectsAs(response, status, customIdentity = identity) {
  await assert.rejects(
    pass(response, customIdentity),
    e => e instanceof FirebaseAuthStatusError && e.status === status &&
      (status === 401 ? e.code === "FIREBASE_SESSION_NOT_ACTIVE" :
        e.code === "FIREBASE_AUTH_STATUS_UNAVAILABLE")
  );
}

test("valid active session accepted; exact identity and validSince checked", async () => {
  assert.deepEqual(await pass(reply(record())), { uid: "demoUid" });
});
test("auth_time equals validSince boundary is allowed", async () => {
  assert.deepEqual(await pass(reply(record({ validSince: "2000" }))), { uid: "demoUid" });
});
test("auth_time below tokens-valid-after boundary is revoked (401)", async () => {
  await rejectsAs(reply(record({ validSince: "2001" })), 401);
});
test("disabled account denied even if validSince is old", async () => {
  await rejectsAs(reply(record({ disabled: true })), 401);
});
test("lookup for another UID denied", async () => {
  await rejectsAs(reply(record({ localId: "attackerUid" })), 401);
});
test("malformed validSince denied, never treated as zero", async () => {
  for (const value of [undefined, "", null, 1900, "NaN", "-1", "1e3", "1000.1", "9999999999999999999999"]) {
    await rejectsAs(reply(record({ validSince: value })), 503);
  }
});
test("missing and multiple users denied", async () => {
  await rejectsAs(reply({ users: [] }), 503);
  await rejectsAs(reply({ users: [record().users[0], record().users[0]] }), 503);
  await rejectsAs(reply({ error: "BAD_RESPONSE" }), 503);
});
test("malformed disabled flag denied", async () => {
  await rejectsAs(reply(record({ disabled: "false" })), 503);
});
test("absent disabled flag accepted when validSince is present", async () => {
  const rec = record();
  delete rec.users[0].disabled;
  assert.deepEqual(await pass(reply(rec)), { uid: "demoUid" });
});
test("Firebase account lookup rejects bad token (400/401)", async () => {
  await rejectsAs(reply({}, 400), 401);
  await rejectsAs(reply({}, 401), 401);
});
test("Firebase API failures 403/404/429/500/503 deny without bypass", async () => {
  for (const status of [403, 404, 429, 500, 503]) {
    await rejectsAs(reply({}, status), 503);
  }
});
test("JSON/network/timeout failures deny", async () => {
  await rejectsAs({ ok: true, status: 200, async json() { throw Error("malformed"); } }, 503);
  await assert.rejects(assertFirebaseAccountActive(identity, key, {
    fetchImpl: async () => { throw Error("offline"); }
  }), e => e.status === 503);
  await assert.rejects(assertFirebaseAccountActive(identity, key, {
    fetchImpl: async () => { throw Error("timeout"); }
  }), e => e.status === 503);
});
test("missing key and malformed identity deny before outbound request", async () => {
  const calls = [];
  for (const invalidKey of [null, "", "abc", "a&evil=true", "a".repeat(257)]) {
    await assert.rejects(assertFirebaseAccountActive(identity, invalidKey, {
      fetchImpl: fake(reply(record()), calls)
    }), e => e.status === 503);
  }
  for (const invalidIdentity of [{...identity, uid: ""}, {...identity, token: null},
      {...identity, authTime: "2000"}, {...identity, authTime: -1}]) {
    await assert.rejects(assertFirebaseAccountActive(invalidIdentity, key, {
      fetchImpl: fake(reply(record()), calls)
    }), e => e.status === 401);
  }
  assert.equal(calls.length, 0);
});
test("only configured Google endpoint receives verified ID token, POST and timeout", async () => {
  const calls = [];
  const r = await assertFirebaseAccountActive(identity, key, {
    fetchImpl: fake(reply(record()), calls)
  });
  assert.equal(r.uid, identity.uid);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" + key);
  assert.equal(calls[0].options.method, "POST");
  assert.equal(calls[0].options.redirect, "error");
  assert.deepEqual(JSON.parse(calls[0].options.body), { idToken: identity.token });
  assert.equal(calls[0].options.headers["Content-Type"], "application/json");
  assert.ok(calls[0].options.signal);
});
test("candidate gate checks token signature, gate flag, revocation, then Firestore", () => {
  const source = readFileSync(new URL("./evidence-gateway.v2.candidate.ts", import.meta.url), "utf8");
  const order = [
    "const identity = await verifyFirebase(req);",
    'if (Deno.env.get("EVIDENCE_ACCESS_ENABLED") !== "true")',
    "await assertFirebaseAccountActive(",
    "const access = await authorizeIncident(identity, incidentId);",
    "const admin = adminClient();"
  ];
  let last = -1;
  for (const piece of order) {
    const next = source.indexOf(piece);
    assert.ok(next > last, "missing or misplaced guard: " + piece);
    last = next;
  }
  assert.match(source, /FIREBASE_WEB_API_KEY/);
  assert.match(source, /FIREBASE_AUTH_STATUS_UNAVAILABLE/);
  assert.ok(!source.includes("REVOCATION_CHECK_DISABLED"));
  assert.ok(!source.includes("SERVICE_ACCOUNT_PRIVATE_KEY"));
});
