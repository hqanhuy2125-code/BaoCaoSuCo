# C01-G0/G1 isolated HTTP integration — TEST ONLY

**Owner:** integration test task; separate Draft PR stacked on G0 candidate PR #64.

## What really runs

`node --test security/gateway/integration/isolated.test.mjs`

- Loads the **exact tracked v2 candidate handler** and intercepts only its runtime imports with VM adapters (`jose`, `Supabase JS` and `Deno.serve`).
- Generates short-lived **fake RS256 JWTs** using an in-memory RSA key, verifies signatures independently in the adapter and routes actual HTTP requests to the real gateway handler.
- Mocks Firebase Auth account lookup and Firestore GET through two HTTP listeners bound **only to 127.0.0.1**. All outgoing `fetch` requests are redirected to the loopback stub or rejected. No Firebase/Supabase host is ever contacted.
- Uses an in-memory fake Supabase query chain for authorization-success GETs. Storage is deliberately unavailable, no POST image success path is exercised (G2 scope).
- Has both gate-closed tests and **test-process-only** `EVIDENCE_ACCESS_ENABLED=true` scenarios to exercise revocation and role authorization; this does **not** set the real Supabase secret.
- Reports Vietnamese `BAO_CAO_G0_G1.md` and machine-readable `result.json` in `G0G1_REPORT_DIR` or the OS temp directory. CI uploads the files as an artifact.
- An independent CI job executes `deno check` with real Deno/npm imports for compatibility. It does not serve a real Supabase Edge Runtime.

## Limits (DO NOT MISLABEL)

This is **isolated integration with test doubles**, not a production/staging cloud HTTP PASS and not a full Supabase Edge Runtime execution. The RS256 signature is real but the jose verifier is replaced by an independent crypto adapter. Firebase Auth/Firestore responses and Supabase metadata reads are synthetic. The `G0/G1` report must never be interpreted as proof that a production `EVIDENCE_ACCESS_ENABLED` flag has been switched on, or that revoked/disabled identities have been tested with the actual Firebase Auth backend.

Real Edge Runtime and disposable Firebase account revocation checks remain a further acceptance gate. G2 real image uploads and G3 two-device Android run are excluded. No `google-services.json`, credentials, Supabase project refs or secrets are needed to run these tests.

## Runtime and safety

Requires Node.js 22+ and, for CI Deno check, Deno v2. No `npm install`, no Firebase CLI, no Docker, no cloud project, and no access to secrets. Do not use the existing Resident A/B/Manager accounts to test disabling or token revocation. This workflow never deploys or merges and may run safely from an untrusted PR fork with read-only repository permissions.

References: https://supabase.com/docs/guides/functions/unit-test ; https://firebase.google.com/docs/reference/rest/auth#section-get-account-info .