# C01-G0 — Firebase revoked-token and disabled-user protection (DRAFT)

**Status: SOURCE-ONLY / NOT DEPLOYED / INDEPENDENT L3 REVIEW REQUIRED**

This is a proposed C01 server-side patch, based on the deployed Supabase evidence-gateway
v1 source from project xnmlnqaetcaawvsbmhjy (active v1 checked on 2026-11-10).
All files live in the isolated security/gateway/ directory. They do NOT alter
the deployed Edge Function, its production secret configuration, Firestore Rules,
Android app, existing Draft PR #62 or #63, or the current Supabase bucket.

## Why change?

A valid RS256 Firebase ID token (signature, issuer, audience, expiry, auth_time)
is a signed but stateless JWT. Checking its signature alone is not sufficient to
detect a Firebase disabled account or session revoked before JWT expiry.

The candidate adds one Firebase Authentication account lookup on every active-gate
request using the **already signed-and-verified end-user ID token** via:

https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=<Firebase-Web-API-Key>

Official reference: https://firebase.google.com/docs/reference/rest/auth#section-get-account-info

The response provides users[0].localId, validSince (seconds), and optional
disabled. The candidate **denies** an explicit disabled=true, mismatching uid,
or auth_time < validSince. It **fails closed** on missing/invalid validSince,
network failures, HTTP errors, invalid/missing response or config. It performs
no cached positive authorization. This matches the security *intent* of
Firebase Admin verifyIdToken(idToken, true) in Firebase's guidance:
https://firebase.google.com/docs/auth/admin/manage-sessions

Unlike a service-account-based Firebase Admin call, this approach uses the
Firebase Web API key and the caller's ID token. It does not require a privileged
service-account key, nor send privileged secrets to the Android APK.
**Independent reviewer must verify that account lookup returns the documented
validSince and disabled fields in this project's actual Firebase runtime.**
If lookup refuses revoked tokens directly, 400/401 is intentionally denied.

## Exact intended code delta and ordering

The candidate is a full staged copy of the deployed v1 source, with:
1. import of local revocation-check.mjs (same directory, deploy as a unit if approved)
2. verifyFirebase() retaining payload.auth_time in identity.authTime
3. after Firebase signature verification and existing gate-enabled check,
   before Firestore access and before constructing service-role admin client,
   await assertFirebaseAccountActive(identity, Deno.env.get("FIREBASE_WEB_API_KEY"));
4. fail-closed mapping of FirebaseAuthStatusError to 401 or 503.

Do not add an opt-out such as REVOCATION_CHECK_DISABLED. Do not silently fall
back to signature-only verification if the Firebase account API is offline.
The gate-closed response remains EVIDENCE_GATE_NOT_APPROVED for valid signed
tokens because the check is intentionally invoked only when the gateway is
enabled; disabled-gate requests cannot reach any data operation.

## Test commands (no production credentials)

    node --test security/gateway/revocation-check.test.mjs

The test mocks the Google Auth REST endpoint. It covers active, exact boundary,
revoked, disabled, wrong UID, missing/malformed validSince/disabled, malformed
response, non-2xx Auth API, timeout and offline fail-closed, missing key,
invalid identity, endpoint/input handling and guard ordering.

These offline tests alone are **not** a production security PASS.

## Independent L3 review checklist

- Confirm every request verifies Firebase RS256 signature, token expiry, issuer
  and audience, then current Firebase Auth state, then Firestore ownership/role,
  *before* any service_role storage/metadata operation. No bypass.
- Confirm auth_time < validSince semantics and disabled/missing fields against
  a dedicated disposable test account, including revoke-refresh-token,
  disable and deleted user; do not mutate existing Resident A/B/Demo Manager.
- Review API key restrictions and quota, rate limiting, Auth API latency and
  error mapping (401 for invalid/revoked/disabled, 503 for backend outage).
- Confirm dependency support in target Supabase Deno Edge runtime.
- Confirm default-deny secret EVIDENCE_ACCESS_ENABLED remains not enabled.
- Do not place Firebase idToken, Web API key, service_role or profile data in
  GitHub/PRs/CI logs. Web API key is app configuration, never a service-account key.
- Review TOCTOU between Firestore status check and Storage upload, JPEG parser
  limitations, orphan object compensation and operational logging separately.
- Record a named independent APPROVED review, exact reviewed commit SHA, and
  owner approval before any stage/production deployment.
- Only after G0 signoff: owner-approved gate-closed HTTP negative tests (G1),
  separately approved bounded G2 image uploads with three identities, then
  G3 Android Camera/Gallery across two devices. No existing closed incident
  should be used for successful REPORTED/RESOLUTION uploads.

## Source fidelity and safety

This PR is review-only. The reference v1 Edge Function remains untouched.
The Firebase Admin SDK checkRevoked variant is an acceptable alternative if
the reviewer chooses it and secure server-only credentials are approved;
it is not assumed Deno-compatible without an actual integration check.
