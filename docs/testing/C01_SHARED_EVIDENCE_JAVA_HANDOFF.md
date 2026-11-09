# C01-SHARED — SupabaseEvidenceClient Java handoff (Draft)

Owner: Trần Quốc Huy, Resident Java + technical coordination. Separate from
Draft PR #62 (Camera/Gallery local-only); no edits to UI/XML/Auth/LOG/Firestore
repository, Firestore Rules, SDK dependencies or Gradle.

## Purpose

`SupabaseEvidenceClient` is the Java-only HTTPS adapter for the Supabase
`evidence-gateway`, which accepts the current Firebase user's ID token.
It does NOT access Supabase Storage directly and never embeds a secret/role key.
It does NOT change `NewIncidentInput`, `IncidentSnapshot` or Firestore
incident/history contracts. **Do not ship or enable until independently approved.**

Pinned endpoints:
- Firebase project expected by gateway and client: `baocaosuco-c4ccc`
  (from GitHub backend-smoke documentation, NOT confirmed against an actual
  `app/google-services.json` runtime configuration yet).
- Supabase project: `xnmlnqaetcaawvsbmhjy` in Singapore (Free).
- HTTPS gateway: `https://xnmlnqaetcaawvsbmhjy.supabase.co/functions/v1/evidence-gateway`.

## API and integration order

Construct `new SupabaseEvidenceClient(context)` after Firebase initialization.
`upload(incidentId, Kind.REPORTED, preparedJpegContentUri, callback)`,
`list(incidentId, callback)` and `download(incidentId, evidenceId, callback)`
run network I/O off the UI thread and invoke callbacks on the main thread.
`download` produces a private app-cache file URI and should only be previewed
within this app; call `close()` at screen close/logout.

**Important:** `upload()` returning `true` means queued, never "uploaded".
Only `Callback.onSuccess` confirms that gateway upload and metadata creation
both succeeded. Distinguish `GATE_DISABLED`, `AUTH_REQUIRED`,
`PERMISSION_DENIED`, `CONFLICT`, `NETWORK`, etc.

Integration after prerequisites:
1. B01 form calls existing `IncidentRepository.Resident.create` to persist a
   report and receives its actual server-created incident ID.
2. Existing C01 G2 `IncidentPhotoUiBridge.currentPhoto().getUri()` supplies
   a processed local JPEG. `upload(id, REPORTED, uri, callback)` attaches it.
   On failure the report remains saved; explicitly show "image not attached"
   and offer retry with the retained JPEG, **never fake attachment success**.
3. Manager detail lists/downloads REPORTED evidence; Manager authorized
   Java flow adds RESOLUTION evidence during handling (one per incident).
4. Resident detail lists/downloads both images. B01/B03 real XML/IDs and Java
   screen integration remain separate handoffs; this PR creates no UI IDs.
5. If Android code uses the client before the remote L3 security gate, the
   server intentionally returns 503 EVIDENCE_GATE_NOT_APPROVED.

## Security / release gates

- Supabase private bucket JPEG/2MiB and metadata RLS are configured, no
  direct anonymous/authenticated metadata or Storage object policies.
- Edge Function has verify_jwt=false to accept Firebase JWT but validates
  RS256 against Firebase JWKS and verifies the user and incident through
  Firebase Auth identity + Firestore REST Security Rules.
- Supabase gateway data access is **closed by default** via
  `EVIDENCE_ACCESS_ENABLED` (not set). A production security reviewer and
  group lead must approve any permission/flag changes. Nothing in this Java
  client grants backend rights or tests live Firebase token auth.
- Need actual runtime `google-services.json` project ID and provisioned
  RESIDENT/MANAGER Firebase Auth profiles with the trusted role field; do not
  send tokens, passwords or service-account JSON into GitHub.
- Need independent LIVE two-account allow/deny probes: own RESIDENT report,
  manager read/upload, unrelated resident blocked, invalid/expired token
  blocked, retry after network interruption, cross-device persistence.
  Verify exact current source SHA and genuine test device before app acceptance.
- Local JUnit checks only request ID/JPEG bounds. CI compile/JUnit cannot prove
  the remote gateway or Android emulator actually works.
