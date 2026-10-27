import { before, beforeEach, after, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment }
  from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp,
  setDoc, Timestamp, updateDoc, where
} from 'firebase/firestore';

// Test the ACTUAL tracked root Rules with the local emulator only.
// No Firebase app credentials, live project names or Admin SDK.
const projectId = 'demo-baocaosuco-sec02a';
const ruleSource = readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8');
if (!ruleSource.includes('match /users/{userId}') ||
    !ruleSource.includes('match /{document=**}') ||
    !ruleSource.includes('allow read, write: if false')) {
  throw new Error('Tracked root Rules changed: re-review baseline tests; never use open defaults');
}

let environment;
const firestore = ctx => ctx.firestore();
const anonymous = () => environment.unauthenticatedContext();
const alice = () => environment.authenticatedContext('alice', { email: 'alice@example.test' });
const bob = () => environment.authenticatedContext('bob', { email: 'bob@example.test' });
const manager = () => environment.authenticatedContext('manager', { email: 'manager@example.test' });

const profile = (uid, role = 'RESIDENT') => ({
  uid,
  email: uid + '@example.test',
  displayName: 'Demo ' + uid,
  role,
  createdAt: Timestamp.fromMillis(1000)
});
const incident = () => ({
  residentId: 'alice',
  title: 'Mock broken light',
  description: 'Mock detail',
  location: 'Demo corridor',
  priority: 'NORMAL',
  status: 'NEW',
  assignedTo: null,
  createdAt: Timestamp.fromMillis(1000),
  dueAt: Timestamp.fromMillis(259201000),
  updatedAt: Timestamp.fromMillis(1000)
});
const historyEvent = () => ({
  fromStatus: 'NEW', toStatus: 'ASSIGNED', actorId: 'manager',
  createdAt: Timestamp.fromMillis(2000), note: null
});

before(async () => {
  environment = await initializeTestEnvironment({
    projectId,
    firestore: { host: '127.0.0.1', port: 8087, rules: ruleSource }
  });
});
after(async () => {
  if (environment) await environment.cleanup();
});
beforeEach(async () => {
  await environment.clearFirestore();
  await environment.withSecurityRulesDisabled(async ctx => {
    const db = firestore(ctx);
    await setDoc(doc(db, 'users/alice'), profile('alice'));
    await setDoc(doc(db, 'users/bob'), profile('bob'));
    // This profile is a fixture CREATED WITH RULES DISABLED.
    // This does NOT assert that an Android user may self-provision MANAGER.
    await setDoc(doc(db, 'users/manager'), profile('manager', 'MANAGER'));
    await setDoc(doc(db, 'incidents/i1'), incident());
    await setDoc(doc(db, 'incidents/i1/history/e1'), historyEvent());
    await setDoc(doc(db, 'history/e1'), { ...historyEvent(), incidentId: 'i1' });
  });
});

test('profile: unauthenticated read is denied', async () => {
  await assertFails(getDoc(doc(firestore(anonymous()), 'users/alice')));
});
test('profile: resident can read their own user document', async () => {
  await assertSucceeds(getDoc(doc(firestore(alice()), 'users/alice')));
});
test('profile: resident cannot read another resident profile', async () => {
  await assertFails(getDoc(doc(firestore(bob()), 'users/alice')));
});
test('profile: trusted preseeded manager role can read user profiles', async () => {
  await assertSucceeds(getDoc(doc(firestore(manager()), 'users/alice')));
});
test('profile: resident may update their displayName only', async () => {
  await assertSucceeds(updateDoc(doc(firestore(alice()), 'users/alice'), {
    displayName: 'Updated resident demo'
  }));
});
test('profile: resident role escalation is denied', async () => {
  await assertFails(updateDoc(doc(firestore(alice()), 'users/alice'), { role: 'MANAGER' }));
});
test('profile: resident may not rewrite UID or createdAt', async () => {
  await assertFails(updateDoc(doc(firestore(alice()), 'users/alice'), { uid: 'manager' }));
  await assertFails(updateDoc(doc(firestore(alice()), 'users/alice'), {
    createdAt: Timestamp.fromMillis(5000)
  }));
});
test('profile: resident may create their own RESIDENT profile using Auth email', async () => {
  const chris = environment.authenticatedContext('chris', { email: 'chris@example.test' });
  await assertSucceeds(setDoc(doc(firestore(chris), 'users/chris'), {
    uid: 'chris', email: 'chris@example.test', displayName: 'Chris',
    role: 'RESIDENT', createdAt: serverTimestamp()
  }));
});
test('profile: client cannot self-provision MANAGER on create', async () => {
  const chris = environment.authenticatedContext('chris', { email: 'chris@example.test' });
  await assertFails(setDoc(doc(firestore(chris), 'users/chris'), {
    uid: 'chris', email: 'chris@example.test', displayName: 'Chris',
    role: 'MANAGER', createdAt: serverTimestamp()
  }));
});
test('profile: resident cannot create document for another UID', async () => {
  await assertFails(setDoc(doc(firestore(bob()), 'users/other-uid'), profile('other-uid')));
});
test('profile: authenticated user cannot delete their own profile', async () => {
  await assertFails(deleteDoc(doc(firestore(alice()), 'users/alice')));
});
test('incident: unauthenticated get is denied under CURRENT rules', async () => {
  await assertFails(getDoc(doc(firestore(anonymous()), 'incidents/i1')));
});
test('incident: owning resident get is denied under CURRENT rules', async () => {
  await assertFails(getDoc(doc(firestore(alice()), 'incidents/i1')));
});
test('incident: preseeded manager get is denied under CURRENT rules', async () => {
  await assertFails(getDoc(doc(firestore(manager()), 'incidents/i1')));
});
test('incident: owning resident create is denied under CURRENT rules', async () => {
  await assertFails(setDoc(doc(firestore(alice()), 'incidents/new'), incident()));
});
test('incident: manager create is denied under CURRENT rules', async () => {
  await assertFails(setDoc(doc(firestore(manager()), 'incidents/new'), incident()));
});
test('incident: resident and manager update are denied under CURRENT rules', async () => {
  await assertFails(updateDoc(doc(firestore(alice()), 'incidents/i1'), { status: 'CLOSED' }));
  await assertFails(updateDoc(doc(firestore(manager()), 'incidents/i1'), {
    status: 'ASSIGNED', assignedTo: 'Team A'
  }));
});
test('incident: resident and manager delete are denied under CURRENT rules', async () => {
  await assertFails(deleteDoc(doc(firestore(alice()), 'incidents/i1')));
  await assertFails(deleteDoc(doc(firestore(manager()), 'incidents/i1')));
});
test('incident: list query scoped to resident is still denied under CURRENT rules', async () => {
  await assertFails(getDocs(query(collection(firestore(alice()), 'incidents'),
    where('residentId', '==', 'alice'))));
});
test('incident: manager list query is still denied under CURRENT rules', async () => {
  await assertFails(getDocs(collection(firestore(manager()), 'incidents')));
});
test('nested history: owner and manager read denied', async () => {
  await assertFails(getDoc(doc(firestore(alice()), 'incidents/i1/history/e1')));
  await assertFails(getDoc(doc(firestore(manager()), 'incidents/i1/history/e1')));
});
test('nested history: owner and manager create denied', async () => {
  await assertFails(setDoc(doc(firestore(alice()), 'incidents/i1/history/e2'), historyEvent()));
  await assertFails(setDoc(doc(firestore(manager()), 'incidents/i1/history/e2'), historyEvent()));
});
test('nested history: updates and deletes denied', async () => {
  await assertFails(updateDoc(doc(firestore(manager()), 'incidents/i1/history/e1'), {
    actorId: 'forged'
  }));
  await assertFails(deleteDoc(doc(firestore(alice()), 'incidents/i1/history/e1')));
});
test('top-level history alternative is denied until CON-01 chooses a path', async () => {
  await assertFails(getDoc(doc(firestore(alice()), 'history/e1')));
  await assertFails(setDoc(doc(firestore(manager()), 'history/e2'),
    { ...historyEvent(), incidentId: 'i1' }));
});
