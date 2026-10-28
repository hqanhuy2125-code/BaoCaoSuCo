// Local Emulator fixtures only. No cloud endpoints, Admin credentials or role grants.
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const project = 'demo-baocaosuco-smoke';
const output = resolve(process.argv[2] ?? '.gradle/backend-smoke/backend-smoke.properties');
const suffix = randomUUID();
const password = randomUUID();
const values = { projectId: project, emulatorHost: 'localhost' };

async function request(url, body, headers = {}) {
  const response = await fetch(url, {
    method: headers.Authorization ? 'PATCH' : 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(10000)
  });
  if (!response.ok) throw new Error(`Local fixture request failed: HTTP ${response.status}`);
  return response.json();
}

for (const [identity, role] of [
  ['resident', 'RESIDENT'], ['manager', 'MANAGER'], ['otherResident', 'RESIDENT']
]) {
  const email = `${identity}-${suffix}@example.test`;
  const created = await request(
    'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator-only',
    { email, password, returnSecureToken: true });
  // The owner token is an Emulator-only testing facility, never a cloud credential.
  await request(`http://127.0.0.1:8087/v1/projects/${project}/databases/(default)/documents/users/${created.localId}`,
    { fields: {
      uid: { stringValue: created.localId }, displayName: { stringValue: `Smoke ${identity}` },
      email: { stringValue: email }, role: { stringValue: role },
      createdAt: { timestampValue: new Date().toISOString() }
    } }, { Authorization: 'Bearer owner' });
  values[`${identity}Email`] = email;
  values[`${identity}Password`] = password;
}
values.signupEmail = `signup-${suffix}@example.test`;
values.signupPassword = randomUUID();
await mkdir(dirname(output), { recursive: true });
// Refuse to overwrite a fixture that might contain real account credentials.
await writeFile(output, Object.entries(values).map(([key, value]) => `${key}=${value}\n`).join(''),
  { flag: 'wx', mode: 0o600 });
console.log('Created demo-only Auth/profile fixtures. Credentials were not printed.');
