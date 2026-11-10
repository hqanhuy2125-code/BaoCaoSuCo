/**
 * G0/G1 isolated HTTP integration harness.
 * Runs actual v2 candidate handler in a Node VM, replacing *only* platform
 * imports (jose, Supabase JS, Deno.serve) with local test adapters.
 * Auth and Firestore REST operations traverse real loopback HTTP, never cloud.
 * No credentials, no production URL, no uploads or remote mutation.
 * Crypto fixture signs RS256 JWTs locally; jwtVerify adapter verifies RSA
 * independently, but this is NOT a real jose/Deno runtime test.
 */
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { generateKeyPairSync, sign, verify } from 'node:crypto';
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
const nativeFetch = globalThis.fetch;
let fakeServer;
let gatewayServer;
let fakePort;
let gatewayPort;
let handler;
let gateEnabled = false;
let lookupStatus = 200;
let badAccountShape = false;
let firestoreStatus = 200;
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
function verifyLocalJwt(token, jwks, options) {
  if (!jwks || jwks.marker !== 'isolated-local-jwks') throw Error('Unrecognized JWKS source');
  const [h, p, sig, excess] = token.split('.');
  if (!h || !p || !sig || excess) throw Error('Malformed JWT');
  const header = JSON.parse(Buffer.from(h, 'base64url').toString());
  const claims = JSON.parse(Buffer.from(p, 'base64url').toString());
  if (header.alg !== 'RS256' || !options.algorithms.includes(header.alg) || header.kid !== keyId) {
    throw Error('Untrusted JWT algorithm/key');
  }
  if (!verify('RSA-SHA256', Buffer.from(`${h}.${p}`), publicKey, Buffer.from(sig, 'base64url'))) {
    throw Error('Invalid JWT signature');
  }
  if (claims.iss !== options.issuer || claims.aud !== options.audience ||
      !Number.isInteger(claims.exp) || claims.exp <= now - options.clockTolerance ||
      (typeof claims.nbf === 'number' && claims.nbf > now + options.clockTolerance)) {
    throw Error('JWT claims invalid');
  }
  return { payload: claims };
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
    return respond(res, 200, { fields: { residentId: { stringValue: ownerId }, status: { stringValue: 'NEW' } } });
  }
  return respond(res, 404, { error: 'LOCAL_UNKNOWN_HOST' });
}
function fakeAdminClient(url, secret) {
  assert.equal(url, `http://127.0.0.1:${fakePort}/supabase-test-only`);
  assert.equal(secret, 'LOCAL_FAKE_SERVICE_ROLE');
  stats.admin++;
  return {
    from(table) {
      assert.equal(table, 'incident_evidence');
      return {
        select() { return this; },
        eq() { return this; },
        order() { return Promise.resolve({ data: [], error: null }); },
        maybeSingle() { return Promise.resolve({ data: null, error: null }); }
      };
    },
    storage: {
      from() { stats.uploads++; throw Error('G2 storage forbidden in G0/G1 test environment'); }
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
        if (key === 'SUPABASE_URL') return `http://127.0.0.1:${fakePort}/supabase-test-only`;
        if (key === 'SUPABASE_SECRET_KEYS') return JSON.stringify({ default: 'LOCAL_FAKE_SERVICE_ROLE' });
        return undefined;
      } }
    },
    createRemoteJWKSet(url) {
      assert.equal(String(url), 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com');
      return { marker: 'isolated-local-jwks' };
    },
    async jwtVerify(token, jwks, opts) { return verifyLocalJwt(token, jwks, opts); },
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
    environment: 'localhost only / independent mock Firebase Auth + Firestore / in-memory fake Supabase',
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
      assert.equal(stats.uploads, 0, 'No storage uploads in G0/G1');
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
  const response = await nativeFetch(`http://127.0.0.1:${gatewayPort}/?incidentId=${incidentId}`,
    { method: opts.method || 'GET', headers });
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