/**
 * G0/G1 isolated HTTP integration harness.
 * Runs actual v2 candidate handler in a Node VM, replacing *only* platform
 * Supabase and Deno.serve with local test adapters. JOSE uses the same pinned
 * version as the candidate with a local test-only trust root.
 * Auth and Firestore REST operations traverse real loopback HTTP, never cloud.
 * No credentials, production URL or remote mutation.
 * Write integrity cases explicitly allow an in-memory fake Storage.
 * Crypto fixture signs RS256 JWTs locally. This does not run Supabase Edge Runtime.
 */
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { generateKeyPairSync, sign } from 'node:crypto';
import { createLocalJWKSet, jwtVerify } from 'jose';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import vm from 'node:vm';
import { stripTypeScriptTypes } from 'node:module';
import { fileURLToPath } from 'node:url';
import { assertFirebaseAccountActive, FirebaseAuthStatusError } from '../revocation-check.mjs';

const candidateURL = new URL('../evidence-gateway.v2.candidate.ts', import.meta.url);
const source = await readFile(candidateURL, 'utf8');
const project = 'baocaosuco-c4ccc';
const fakeApiKey = 'ISOLATED_FAKE_FIREBASE_KEY';
const now = Math.floor(Date.now() / 1000);
const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const keyId = 'test-only-local-rsa-key';
const localJwks = createLocalJWKSet({ keys: [{ ...publicKey.export({ format: 'jwk' }), kid: keyId, alg: 'RS256' }] });
const nativeFetch = globalThis.fetch;
let fakeServer;
let gatewayServer;
let fakePort;
let gatewayPort;
let handler;
let gateEnabled = false;
const productionSupabase = 'https://xnmlnqaetcaawvsbmhjy.supabase.co';
const qaSupabase = 'https://toqmwniuwqinielydgnp.supabase.co';
let supabaseUrl, explicitFirebase, qaUsers, qaIncidents;
let lookupStatus = 200;
let badAccountShape = false;
let firestoreStatus = 200;
let writeFixture = false;
let mode = '';
let status = 'NEW';
let records, objects, uploadBarrier;
let stats = {};
let accounts;
let cases = [];
let sandboxEscapes = 0;

const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
const decode = token => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
function jwt(uid, opts = {}) {
  const header = encode({ alg: opts.alg || 'RS256', typ: 'JWT', kid: keyId });
  const payload = encode({ iss: `https://securetoken.google.com/${project}`,
    aud: project, sub: uid, iat: now - 30, auth_time: now - 30, exp: now + 1800,
    ...opts.payload });
  const data = `${header}.${payload}`;
  const signature = sign('RSA-SHA256', Buffer.from(data), privateKey).toString('base64url');
  return `${data}.${signature}`;
}
const profile = (uid, role) => ({ uid, role, validSince: String(now - 3600), disabled: false });
function reset() {
  accounts = new Map([
    ['residentA', profile('residentA', 'RESIDENT')],
    ['residentB', profile('residentB', 'RESIDENT')],
    ['demoManager', profile('demoManager', 'MANAGER')],
  ]);
  lookupStatus = 200;
  badAccountShape = false;
  firestoreStatus = 200;
  gateEnabled = false;
  supabaseUrl = productionSupabase;
  explicitFirebase = undefined;
  qaUsers = undefined;
  qaIncidents = undefined;
  writeFixture = false;
  mode = '';
  status = 'NEW';
  records = new Map();
  objects = new Set();
  uploadBarrier = null;
  stats = { lookups: 0, firestore: 0, admin: 0, external: 0, uploads: 0 };
}
reset();
function respond(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(data);
}
async function readJson(req) {
  const parts = [];
  for await (const c of req) parts.push(c);
  return JSON.parse(Buffer.concat(parts).toString('utf8'));
}
async function firebaseHttp(req, res) {
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname === '/identitytoolkit/v1/accounts:lookup') {
    stats.lookups++;
    if (req.method !== 'POST' || url.searchParams.get('key') !== fakeApiKey) {
      return respond(res, 403, { error: 'LOCAL_KEY_DENIED' });
    }
    const input = await readJson(req);
    if (lookupStatus !== 200) return respond(res, lookupStatus, { error: 'LOCAL_AUTH_OUTAGE' });
    if (badAccountShape) return respond(res, 200, { users: [{ localId: 5 }] });
    let decoded;
    try { decoded = decode(input.idToken); } catch { return respond(res, 400, { error: 'INVALID_ID_TOKEN' }); }
    const acc = accounts.get(decoded.sub);
    if (!acc) return respond(res, 400, { error: 'USER_NOT_FOUND' });
    return respond(res, 200, { users: [{ localId: acc.uid, validSince: acc.validSince, disabled: acc.disabled }] });
  }
  if (url.pathname.startsWith('/firestore/v1/projects/')) {
    stats.firestore++;
    if (firestoreStatus !== 200) return respond(res, firestoreStatus, { error: 'LOCAL_FIRESTORE_FAILURE' });
    const match = url.pathname.match(/^\/firestore\/v1\/projects\/baocaosuco-c4ccc\/databases\/\(default\)\/documents\/(users|incidents)\/([A-Za-z0-9_-]+)$/);
    if (!match) return respond(res, 404, { error: 'UNKNOWN_ROUTE' });
    const [, collection, id] = match;
    let uid;
    try { uid = decode((req.headers.authorization || '').slice(7)).sub; }
    catch { return respond(res, 401, { error: 'INVALID_TOKEN' }); }
    const caller = accounts.get(uid);
    if (!caller) return respond(res, 403, { error: 'NO_CALLER' });
    if (collection === 'users') {
      const found = accounts.get(id);
      if (!found || (caller.role !== 'MANAGER' && caller.uid !== id)) {
        return respond(res, 403, { error: 'PERMISSION_DENIED' });
      }
      return respond(res, 200, { fields: { uid: { stringValue: found.uid }, role: { stringValue: found.role } } });
    }
    const ownerId = { incidentA: 'residentA', incidentB: 'residentB' }[id];
    if (!ownerId || (caller.role !== 'MANAGER' && ownerId !== uid)) {
      return respond(res, 403, { error: 'PERMISSION_DENIED' });
    }
    return respond(res, 200, { fields: { residentId: { stringValue: ownerId }, status: { stringValue: status } } });
  }
  return respond(res, 404, { error: 'LOCAL_UNKNOWN_HOST' });
}
function fakeAdminClient(url, secret) {
  assert.equal(url, supabaseUrl);
  assert.equal(secret, 'LOCAL_FAKE_SERVICE_ROLE');
  stats.admin++;
  return {
    from(table) {
      assert.equal(table, 'incident_evidence');
      let row;
      const filters = {};
      const matching = () => [...records.values()].filter(
        item => Object.entries(filters).every(([k, v]) => item[k] === v));
      return {
        select() { return this; },
        eq(key, value) { filters[key] = value; return this; },
        order() { return Promise.resolve({ data: matching(), error: null }); },
        maybeSingle() { return Promise.resolve({ data: matching()[0] ?? null, error: null }); },
        insert(value) { assert.ok(writeFixture); row = value; return this; },
        async single() {
          if (mode === 'known' || mode === 'cleanup-fails') return { data: null, error: { code: '23514' } };
          if (mode === 'unknown') return { data: null, error: { code: '' } };
          if ([...records.values()].some(x => x.incident_id === row.incident_id && x.kind === row.kind)) {
            return { data: null, error: { code: '23505' } };
          }
          records.set(row.id, { ...row, created_at: '2026-01-01T00:00:00Z' });
          if (mode === 'lost-ack') throw Error('Local connection dropped after fake commit');
          return { data: records.get(row.id), error: null };
        }
      };
    },
    storage: {
      from(bucket) {
        assert.ok(writeFixture, 'No storage in original G0/G1 cases');
        assert.equal(bucket, 'incident-evidence');
        return {
          async upload(path) {
            stats.uploads++;
            objects.add(path);
            if (mode === 'state-change') status = 'CLOSED';
            if (mode === 'lost-put') throw Error('Local upload acknowledgement lost');
            if (mode === 'concurrent') {
              if (stats.uploads === 1) await new Promise(resolve => { uploadBarrier = resolve; });
              else uploadBarrier();
            }
            return { error: null };
          },
          async remove(paths) {
            if (mode === 'cleanup-fails') return { error: new Error('Local Storage outage') };
            for (const path of paths) objects.delete(path);
            return { error: null };
          }
        };
      }
    }
  };
}
async function localFetch(input, options) {
  const target = new URL(String(input));
  let path;
  if (target.hostname === 'identitytoolkit.googleapis.com' && target.pathname === '/v1/accounts:lookup') {
    path = `/identitytoolkit${target.pathname}${target.search}`;
  } else if (target.hostname === 'firestore.googleapis.com' && target.pathname.startsWith('/v1/projects/')) {
    path = `/firestore${target.pathname}${target.search}`;
  } else {
    sandboxEscapes++;
    throw Error(`G0/G1 NETWORK GUARD: unexpected outbound hostname ${target.hostname}`);
  }
  // The only external-looking URLs in the candidate are diverted to 127.0.0.1.
  stats.external++;
  return nativeFetch(`http://127.0.0.1:${fakePort}${path}`, options);
}
async function nodeGateway(req, res) {
  const parts = [];
  for await (const c of req) parts.push(c);
  const body = Buffer.concat(parts);
  const url = `http://127.0.0.1:${gatewayPort}${req.url}`;
  const webReq = new Request(url, { method: req.method,
    headers: req.headers, ...(body.length ? { body } : {}) });
  let result;
  try { result = await handler(webReq); }
  catch { return respond(res, 503, { error: 'LOCAL_HANDLER_CRASH' }); }
  const headers = Object.fromEntries(result.headers);
  res.writeHead(result.status, headers);
  res.end(Buffer.from(await result.arrayBuffer()));
}
async function listen(server) {
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  return server.address().port;
}
function loadActualCandidate() {
  const importLines = source.split('\n').filter(x => x.startsWith('import '));
  assert.equal(importLines.length, 3, 'Candidate imports changed: update adapter and review');
  assert.ok(importLines.some(x => x.includes('jose@5.10.0')));
  assert.ok(importLines.some(x => x.includes('supabase-js@2.57.0')));
  assert.ok(importLines.some(x => x.includes('revocation-check.mjs')));
  assert.match(source, /Deno\.serve\(async \(req\) => \{/);
  assert.match(source, /const identity = await verifyFirebase\(req\);/);
  assert.match(source, /await assertFirebaseAccountActive\(identity, Deno\.env\.get\("FIREBASE_WEB_API_KEY"\)\);/);
  assert.match(source, /const access = await authorizeIncident\(identity, incidentId\);/);
  const script = source.replace(/^import [^\n]+;\s*$/gm, '');
  const ctx = vm.createContext({
    Deno: {
      serve(fn) { handler = fn; },
      env: { get(key) {
        if (key === 'EVIDENCE_ACCESS_ENABLED') return gateEnabled ? 'true' : 'false';
        if (key === 'FIREBASE_WEB_API_KEY') return fakeApiKey;
        if (key === 'SUPABASE_URL') return supabaseUrl;
        if (key === 'EVIDENCE_FIREBASE_PROJECT_ID') return explicitFirebase;
        if (key === 'EVIDENCE_QA_USER_IDS') return qaUsers;
        if (key === 'EVIDENCE_QA_INCIDENT_IDS') return qaIncidents;
        if (key === 'SUPABASE_SECRET_KEYS') return JSON.stringify({ default: 'LOCAL_FAKE_SERVICE_ROLE' });
        return undefined;
      } }
    },
    createRemoteJWKSet(url) {
      assert.equal(String(url), 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com');
      return localJwks;
    },
    jwtVerify,
    createClient: fakeAdminClient,
    assertFirebaseAccountActive,
    FirebaseAuthStatusError,
    fetch: localFetch,
    Request, Response, URL, AbortSignal, Uint8Array,
    crypto: globalThis.crypto,
    console: { error() { /* avoid leaking internal test fixture data */ } }
  });
  new vm.Script(stripTypeScriptTypes(script, { mode: 'strip' }), { filename: fileURLToPath(candidateURL) }).runInContext(ctx, { timeout: 1500 });
  assert.equal(typeof handler, 'function', 'Gateway entrypoint not registered');
}
before(async () => {
  fakeServer = createServer((req, res) => { firebaseHttp(req, res).catch(() => respond(res, 503, { error: 'LOCAL_FAILURE' })); });
  fakePort = await listen(fakeServer);
  globalThis.fetch = localFetch;
  loadActualCandidate();
  gatewayServer = createServer((req, res) => { nodeGateway(req, res).catch(() => respond(res, 503, { error: 'LOCAL_GATEWAY_ERROR' })); });
  gatewayPort = await listen(gatewayServer);
});
after(async () => {
  globalThis.fetch = nativeFetch;
  await Promise.all([new Promise(resolve => fakeServer?.close(resolve)),
    new Promise(resolve => gatewayServer?.close(resolve))]);
  const ok = cases.filter(x => x.status === 'PASS').length;
  const failed = cases.filter(x => x.status === 'FAIL').length;
  const folder = process.env.G0G1_REPORT_DIR || join(tmpdir(), 'baocaosuco-g0g1-report');
  await mkdir(folder, { recursive: true });
  const summary = { suite: 'C01 G0/G1 isolated integration', source: process.env.G0G1_SOURCE_SHA || 'local-unpinned',
    environment: 'localhost only / pinned JOSE / mock Firebase / in-memory fake Supabase',
    productionCalls: 0, cloudWrites: 0, passed: ok, failed, cases };
  await writeFile(join(folder, 'result.json'), JSON.stringify(summary, null, 2) + '\n');
  const lines = [
    '# Báo cáo kiểm thử tự động G0/G1 — BaoCaoSuCo', '',
    `**Kết quả:** ${ok}/${cases.length} PASS — ${failed} FAIL`,
    '', `**Commit được kiểm thử:** \`${summary.source}\``,
    '', '**Môi trường:** localhost với Firebase Auth/Firestore giả lập, Supabase in-memory; không có kết nối tới production.',
    '', '**Phạm vi:** G0 (JWT/revocation) + G1 (HTTP khi gateway khóa và quyền đọc trong môi trường cô lập). Chưa phải staging Supabase Edge Runtime hoặc G2 tải ảnh.',
    '', '| # | Ca kiểm thử | Kết quả |', '|---|---|---|',
    ...cases.map((item, i) => `| ${i+1} | ${item.name.replaceAll('|', '/') } | ${item.status} |`),
    '', '**Không triển khai:** không kích hoạt Supabase gateway, không sửa Firebase Rules/role, không tạo ảnh hoặc báo cáo production.',
    '', '**Điều kiện tiếp:** xác nhận Deno/Edge Runtime, Firebase Auth REST với tài khoản thử nghiệm riêng và review có thẩm quyền trước khi cân nhắc G2.', ''
  ];
  await writeFile(join(folder, 'BAO_CAO_G0_G1.md'), lines.join('\n'));
  assert.equal(sandboxEscapes, 0, 'Outbound network escape attempted');
});
function caseTest(name, fn) {
  test(name, async () => {
    reset();
    try {
      await fn();
      assert.equal(sandboxEscapes, 0, 'Never allow outbound external network');
      if (!writeFixture) assert.equal(stats.uploads, 0, 'No storage uploads in G0/G1');
      cases.push({ name, status: 'PASS' });
    } catch (error) {
      cases.push({ name, status: 'FAIL', reason: String(error.message).slice(0, 180) });
      throw error;
    }
  });
}
async function request(uid, incidentId = 'incidentA', opts = {}) {
  const rawToken = opts.token ?? (uid ? jwt(uid, opts.jwtOpts || {}) : null);
  const headers = rawToken === null ? {} : { Authorization: `Bearer ${rawToken}` };
  if (opts.body) headers['Content-Type'] = 'image/jpeg';
  const response = await nativeFetch(`http://127.0.0.1:${gatewayPort}/?incidentId=${incidentId}`,
    { method: opts.method || 'GET', headers, body: opts.body,
      ...(opts.body instanceof ReadableStream ? { duplex: 'half' } : {}) });
  const data = await response.json();
  return { code: response.status, error: data.error, data };
}
const expectResult = (result, code, error = undefined) => {
  assert.equal(result.code, code, JSON.stringify(result));
  if (error !== undefined) assert.equal(result.error, error);
};

caseTest('G1 gate khóa: JWT hợp lệ chỉ nhận 503, không gọi Firebase Auth/Firestore', async () => {
  expectResult(await request('residentA'), 503, 'EVIDENCE_GATE_NOT_APPROVED');
  assert.equal(stats.lookups, 0); assert.equal(stats.firestore, 0); assert.equal(stats.admin, 0);
});
caseTest('G1 gate khóa: không token bị chặn 401', async () => {
  expectResult(await request(null), 401, 'FIREBASE_AUTH_REQUIRED');
});
caseTest('G1 gate khóa: chữ ký JWT giả mạo bị chặn trước khi lookup', async () => {
  const token = jwt('residentA'); const parts = token.split('.');
  parts[2] = 'INVALID_SIGNATURE';
  expectResult(await request(null, 'incidentA', { token: parts.join('.') }), 401, 'INVALID_FIREBASE_TOKEN');
  assert.equal(stats.lookups, 0);
});
caseTest('G1 gate khóa: JWT hết hạn bị chặn', async () => {
  expectResult(await request('residentA', 'incidentA', { jwtOpts: { payload: { exp: now - 120 } } }), 401, 'INVALID_FIREBASE_TOKEN');
});
caseTest('G1 gate khóa: JWT sai Firebase project bị chặn', async () => {
  expectResult(await request('residentA', 'incidentA', { jwtOpts: { payload: { aud: 'not-our-project' } } }), 401, 'INVALID_FIREBASE_TOKEN');
});
function assertNoProtectedCalls() {
  for (const key of ['lookups', 'firestore', 'admin', 'uploads']) assert.equal(stats[key], 0, key);
}
function configureSharedPhotoQa() {
  supabaseUrl = qaSupabase;
  explicitFirebase = project;
  qaUsers = 'residentA,residentB,demoManager';
  qaIncidents = 'incidentA';
}

caseTest('Shared Photo QA / locked gateway does not use account or storage services', async () => {
  configureSharedPhotoQa();
  expectResult(await request('residentA'), 503, 'EVIDENCE_GATE_NOT_APPROVED');
  assertNoProtectedCalls();
});

caseTest('Shared Photo QA / allowlisted Resident and Manager use existing authorization', async () => {
  configureSharedPhotoQa();
  gateEnabled = true;
  expectResult(await request('residentA'), 200);
  expectResult(await request('demoManager'), 200);
  expectResult(await request('residentB'), 403, 'INCIDENT_ACCESS_DENIED');
  assert.equal(stats.admin, 2);
});

caseTest('Shared Photo QA / omitted UID and incident are denied before backend calls', async () => {
  configureSharedPhotoQa();
  gateEnabled = true;
  qaUsers = 'residentA,demoManager';
  expectResult(await request('residentB'), 403, 'QA_SCOPE_DENIED');
  expectResult(await request('demoManager', 'incidentB'), 403, 'QA_SCOPE_DENIED');
  assertNoProtectedCalls();
});

caseTest('Shared Photo QA / missing and malformed allowlists fail closed', async () => {
  configureSharedPhotoQa();
  gateEnabled = true;
  qaUsers = undefined;
  expectResult(await request('residentA'), 503, 'QA_SCOPE_NOT_CONFIGURED');
  qaUsers = 'residentA';
  qaIncidents = 'incidentA,';
  expectResult(await request('residentA'), 503, 'QA_SCOPE_NOT_CONFIGURED');
  assertNoProtectedCalls();
});

caseTest('Shared Photo QA / requires the explicit Firebase project', async () => {
  configureSharedPhotoQa();
  gateEnabled = true;
  explicitFirebase = undefined;
  expectResult(await request('residentA'), 503, 'QA_SCOPE_NOT_CONFIGURED');
  assertNoProtectedCalls();
});

caseTest('Shared Photo QA / an unapproved Supabase URL fails closed', async () => {
  configureSharedPhotoQa();
  gateEnabled = true;
  supabaseUrl = 'https://example.invalid';
  expectResult(await request('residentA'), 503, 'QA_SCOPE_NOT_CONFIGURED');
  assertNoProtectedCalls();
});

for (const [name, payload] of [
  ['missing exp', { exp: undefined }],
  ['malformed exp', { exp: 'not-a-date' }],
  ['fractional exp', { exp: now + 1800.5 }],
  ['missing auth_time', { auth_time: undefined }],
  ['fractional iat', { iat: now - 0.5 }],
  ['wrong issuer', { iss: 'https://invalid.example.test' }],
  ['wrong audience', { aud: 'isolated-wrong-project' }],
  ['expired', { exp: now - 120 }],
]) {
  caseTest('Real JOSE / ' + name + ' is denied before protected operations', async () => {
    gateEnabled = true;
    expectResult(await request('residentA', 'incidentA', { jwtOpts: { payload } }),
      401, 'INVALID_FIREBASE_TOKEN');
    assertNoProtectedCalls();
  });
}
caseTest('Real JOSE / invalid signature is denied before protected operations', async () => {
  gateEnabled = true;
  const parts = jwt('residentA').split('.');
  parts[2] = (parts[2][0] === 'A' ? 'B' : 'A') + parts[2].slice(1);
  expectResult(await request(null, 'incidentA', { token: parts.join('.') }),
    401, 'INVALID_FIREBASE_TOKEN');
  assertNoProtectedCalls();
});

caseTest('G0 gate giả lập mở: Resident A và Manager đọc incident A', async () => {
  gateEnabled = true;
  expectResult(await request('residentA'), 200);
  expectResult(await request('demoManager'), 200);
  assert.equal(stats.lookups, 2); assert.equal(stats.admin, 2);
});
caseTest('G0 gate giả lập mở: Resident B không đọc được incident A', async () => {
  gateEnabled = true;
  expectResult(await request('residentB'), 403, 'INCIDENT_ACCESS_DENIED');
  assert.equal(stats.admin, 0);
});
caseTest('G0 gate giả lập mở: Resident B đọc incident B và A bị chặn', async () => {
  gateEnabled = true;
  expectResult(await request('residentB', 'incidentB'), 200);
  expectResult(await request('residentA', 'incidentB'), 403, 'INCIDENT_ACCESS_DENIED');
});
caseTest('G0 token bị thu hồi: auth_time cũ hơn validSince trả 401 trước Firestore', async () => {
  gateEnabled = true;
  accounts.get('residentA').validSince = String(now - 3);
  expectResult(await request('residentA'), 401, 'FIREBASE_SESSION_NOT_ACTIVE');
  assert.equal(stats.firestore, 0); assert.equal(stats.admin, 0);
});
caseTest('G0 tài khoản disabled: 401 trước khi dùng service role', async () => {
  gateEnabled = true;
  accounts.get('residentA').disabled = true;
  expectResult(await request('residentA'), 401, 'FIREBASE_SESSION_NOT_ACTIVE');
  assert.equal(stats.firestore, 0); assert.equal(stats.admin, 0);
});
caseTest('G0 tài khoản bị xóa: Firebase Auth lookup 400 dẫn đến 401', async () => {
  gateEnabled = true; accounts.delete('residentA');
  expectResult(await request('residentA'), 401, 'FIREBASE_SESSION_NOT_ACTIVE');
  assert.equal(stats.firestore, 0);
});
caseTest('G0 Firebase Auth 503: fail-closed trước Firestore', async () => {
  gateEnabled = true; lookupStatus = 503;
  expectResult(await request('residentA'), 503, 'FIREBASE_AUTH_STATUS_UNAVAILABLE');
  assert.equal(stats.firestore, 0); assert.equal(stats.admin, 0);
});
caseTest('G0 Firebase Auth phản hồi sai schema: fail-closed', async () => {
  gateEnabled = true; badAccountShape = true;
  expectResult(await request('residentA'), 401, 'FIREBASE_SESSION_NOT_ACTIVE');
  assert.equal(stats.admin, 0);
});
caseTest('G0 Firestore 403: gateway giữ nguyên nguyên tắc deny', async () => {
  gateEnabled = true; firestoreStatus = 403;
  expectResult(await request('residentA'), 403, 'INCIDENT_ACCESS_DENIED');
  assert.equal(stats.admin, 0);
});
caseTest('G0 Firestore 503: gateway không cấp quyền ngầm', async () => {
  gateEnabled = true; firestoreStatus = 503;
  expectResult(await request('residentA'), 503, 'FIRESTORE_UNAVAILABLE');
  assert.equal(stats.admin, 0);
});
caseTest('G1 yêu cầu POST khi gate khóa: không ghi dữ liệu hoặc ảnh', async () => {
  expectResult(await request('residentA', 'incidentA', { method: 'POST' }), 503, 'EVIDENCE_GATE_NOT_APPROVED');
  assert.equal(stats.lookups, 0); assert.equal(stats.uploads, 0);
});
const syntheticJpeg = Buffer.from('/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/2wBDAQMEBAUEBQkFBQkUDQsNFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBT/wAARCAAIAAgDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD8qqKKKAP/2Q==', 'base64');

function startWriteFixture(testMode = '') {
  gateEnabled = true;
  writeFixture = true;
  mode = testMode;
}
function upload(body = syntheticJpeg, uid = 'residentA', kind = 'REPORTED') {
  return request(uid, 'incidentA&kind=' + kind, { method: 'POST', body });
}
caseTest('Synthetic JPEG inserts once; duplicate does not write Storage', async () => {
  startWriteFixture();
  const result = await upload();
  expectResult(result, 201);
  assert.equal(result.data.item.incidentId, 'incidentA');
  expectResult(await upload(), 409, 'EVIDENCE_ALREADY_EXISTS');
  assert.equal(stats.uploads, 1);
});
caseTest('Evidence roles and allowed incident statuses are preserved', async () => {
  startWriteFixture();
  expectResult(await upload(syntheticJpeg, 'demoManager'), 403, 'EVIDENCE_WRITE_DENIED');
  expectResult(await upload(syntheticJpeg, 'residentB'), 403, 'INCIDENT_ACCESS_DENIED');
  expectResult(await upload(syntheticJpeg, 'residentA', 'RESOLUTION'), 403, 'EVIDENCE_WRITE_DENIED');
  status = 'IN_PROGRESS';
  expectResult(await upload(syntheticJpeg, 'demoManager', 'RESOLUTION'), 201);
  assert.equal(objects.size, 1);
});
caseTest('Marker-only and over-limit uploads never reach Storage', async () => {
  startWriteFixture();
  expectResult(await upload(Buffer.from([255,216,255,0,255,217])), 415, 'INVALID_JPEG');
  expectResult(await upload(Buffer.alloc(2 * 1024 * 1024 + 1)), 413, 'PHOTO_TOO_LARGE');
  assert.equal(stats.uploads, 0);
});
caseTest('Streaming size limit rejects a chunked request without Content-Length', async () => {
  startWriteFixture();
  const body = new ReadableStream({ start(controller) {
    controller.enqueue(new Uint8Array(1024 * 1024));
    controller.enqueue(new Uint8Array(1024 * 1024));
    controller.enqueue(new Uint8Array(1));
    controller.close();
  } });
  expectResult(await upload(body), 413, 'PHOTO_TOO_LARGE');
  assert.equal(stats.uploads, 0);
});
caseTest('A concurrent losing insert cannot delete the winning object', async () => {
  startWriteFixture('concurrent');
  const results = await Promise.all([upload(), upload()]);
  assert.deepEqual(results.map(x => x.code).sort(), [201, 409]);
  assert.equal(stats.uploads, 2, 'Both contenders reached fake Storage before either DB insert');
  assert.equal(records.size, 1);
  assert.equal(objects.size, 1);
  assert.ok(objects.has([...records.values()][0].storage_path));
});
for (const [fixture, http, code, recordCount, objectCount] of [
  ['known', 503, 'EVIDENCE_METADATA_FAILED', 0, 0],
  ['lost-ack', 201, undefined, 1, 1],
  ['unknown', 503, 'EVIDENCE_METADATA_UNCONFIRMED', 0, 1],
  ['cleanup-fails', 503, 'EVIDENCE_METADATA_FAILED', 0, 1],
  ['lost-put', 503, 'PHOTO_UPLOAD_FAILED', 0, 0],
  ['state-change', 403, 'EVIDENCE_WRITE_DENIED', 0, 0],
]) {
  caseTest('Synthetic upload integrity / ' + fixture, async () => {
    startWriteFixture(fixture);
    expectResult(await upload(), http, code);
    assert.equal(records.size, recordCount);
    assert.equal(objects.size, objectCount);
    assert.equal((await request('residentA')).data.items.length, recordCount);
  });
}
