import { before, beforeEach, after, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, Timestamp,
  updateDoc, where
} from 'firebase/firestore';

const rules = readFileSync(new URL('./incidents.readonly.candidate.rules', import.meta.url), 'utf8');
if (!rules.includes('allow create, update, delete: if false') ||
    !rules.includes('match /incidents/{incidentId}') ||
    !rules.includes('match /history/{eventId}')) {
  throw new Error('Candidate Rules not read-only; abort tests');
}
let env;
const ctx = id => env.authenticatedContext(id, { email: id + '@example.test' });
const db = id => ctx(id).firestore();
const anon = () => env.unauthenticatedContext().firestore();
const profile = (id, role='RESIDENT') => ({
  uid: id, email: id+'@example.test', displayName:'Demo '+id, role,
  createdAt: Timestamp.fromMillis(1000)
});
const incident = (id) => ({
  residentId:id, title:'Test', description:'Test', location:'Demo',
  priority:'LOW', status:'NEW', assignedTo:null,
  createdAt:Timestamp.fromMillis(1000), dueAt:Timestamp.fromMillis(604801000),
  updatedAt:Timestamp.fromMillis(1000)
});
const history = () => ({
  actorId:'mgr', fromStatus:'NEW', toStatus:'ASSIGNED',
  createdAt:Timestamp.fromMillis(2000), note:null
});

before(async()=>{
  env = await initializeTestEnvironment({
    projectId:'demo-baocaosuco-sec02a',
    firestore:{host:'127.0.0.1',port:8087,rules}
  });
});
after(async()=>{if(env)await env.cleanup()});
beforeEach(async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c=>{
    const f=c.firestore();
    await setDoc(doc(f,'users/alice'),profile('alice'));
    await setDoc(doc(f,'users/bob'),profile('bob'));
    await setDoc(doc(f,'users/mgr'),profile('mgr','MANAGER'));
    await setDoc(doc(f,'incidents/a1'),incident('alice'));
    await setDoc(doc(f,'incidents/b1'),incident('bob'));
    await setDoc(doc(f,'incidents/a1/history/e1'),history());
    await setDoc(doc(f,'history/e1'),{...history(),incidentId:'a1'});
  });
});

test('candidate resident can get OWN incident',async()=>{
  await assertSucceeds(getDoc(doc(db('alice'),'incidents/a1')));
});
test('candidate resident cannot get other resident incident',async()=>{
  await assertFails(getDoc(doc(db('bob'),'incidents/a1')));
});
test('candidate unauthenticated user cannot get incident',async()=>{
  await assertFails(getDoc(doc(anon(),'incidents/a1')));
});
test('candidate verified manager can get all resident incidents',async()=>{
  await assertSucceeds(getDoc(doc(db('mgr'),'incidents/a1')));
  await assertSucceeds(getDoc(doc(db('mgr'),'incidents/b1')));
});
test('candidate resident query scoped to auth uid succeeds',async()=>{
  await assertSucceeds(getDocs(query(collection(db('alice'),'incidents'),
    where('residentId','==','alice'))));
});
test('candidate unscoped resident list is rejected (Rules are not filters)',async()=>{
  await assertFails(getDocs(collection(db('alice'),'incidents')));
});
test('candidate resident query for someone else is rejected',async()=>{
  await assertFails(getDocs(query(collection(db('alice'),'incidents'),
    where('residentId','==','bob'))));
});
test('candidate manager all-incidents query is accepted',async()=>{
  await assertSucceeds(getDocs(collection(db('mgr'),'incidents')));
});
test('candidate owner may read nested history but other resident cannot',async()=>{
  await assertSucceeds(getDoc(doc(db('alice'),'incidents/a1/history/e1')));
  await assertFails(getDoc(doc(db('bob'),'incidents/a1/history/e1')));
});
test('candidate manager may read nested history and owner may list it',async()=>{
  await assertSucceeds(getDoc(doc(db('mgr'),'incidents/a1/history/e1')));
  await assertSucceeds(getDocs(collection(db('alice'),'incidents/a1/history')));
});
test('candidate denies ALL client incident writes (even manager)',async()=>{
  await assertFails(setDoc(doc(db('alice'),'incidents/new'),incident('alice')));
  await assertFails(updateDoc(doc(db('mgr'),'incidents/a1'),{status:'COMPLETED'}));
  await assertFails(deleteDoc(doc(db('mgr'),'incidents/a1')));
});
test('candidate denies ALL client history writes, updates and deletes',async()=>{
  await assertFails(setDoc(doc(db('mgr'),'incidents/a1/history/e2'),history()));
  await assertFails(updateDoc(doc(db('alice'),'incidents/a1/history/e1'),{actorId:'forged'}));
  await assertFails(deleteDoc(doc(db('mgr'),'incidents/a1/history/e1')));
});
test('candidate top-level history stays denied',async()=>{
  await assertFails(getDoc(doc(db('alice'),'history/e1')));
  await assertFails(getDoc(doc(db('mgr'),'history/e1')));
});
test('candidate preserves user-role anti-escalation',async()=>{
  await assertFails(updateDoc(doc(db('alice'),'users/alice'),{role:'MANAGER'}));
});
