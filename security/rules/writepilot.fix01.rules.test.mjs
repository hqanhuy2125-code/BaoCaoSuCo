import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { assertSucceeds, assertFails, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection, doc, getDoc, getDocs, limit, orderBy, query, serverTimestamp,
  setDoc, Timestamp, where, writeBatch
} from 'firebase/firestore';

// FIX01: NEW, tracked adversarial cases. Separate from the 24+14+28 preserved tests.
// Identical candidate Rules file; emulator-only demo project. NEVER DEPLOY.
const rules = readFileSync(new URL('./incidents.writepilot.candidate.rules', import.meta.url), 'utf8');
const projectId = 'demo-baocaosuco-sec02a';
let env;
const db = (uid) => env.authenticatedContext(uid, { email: uid + '@example.test' }).firestore();
const profile = (uid, role='RESIDENT') => ({
  uid, email: uid + '@example.test', displayName: 'Demo ' + uid, role,
  createdAt: Timestamp.fromMillis(1000)
});
const root = (eventId, extra={}) => ({
  residentId: 'alice', title: 'Working light', description: 'Hall light is out',
  location: 'Hallway', priority: 'NORMAL', status: 'NEW', assignedTo: null,
  revision: 0, lastEventId: eventId,
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...extra
});
const event = (action, fromStatus, toStatus, actorId, revision, note=null) => ({
  action, fromStatus, toStatus, actorId, revision,
  createdAt: serverTimestamp(), note
});
let counter=0;
const nextId = () => 'fix01-' + (++counter);
function pairedCreate(ownerDb, fields={}, eventFields={}) {
  const id=nextId(), eid='create-'+id, b=writeBatch(ownerDb);
  b.set(doc(ownerDb, 'incidents', id),root(eid,fields));
  b.set(doc(ownerDb, 'incidents',id,'history',eid),
    {...event('CREATE',null,'NEW','alice',0),...eventFields});
  return {id, commit:()=>b.commit()};
}
function pairedAssign(managerDb, fields={}, eventFields={}) {
  const eid=nextId(), b=writeBatch(managerDb);
  b.update(doc(managerDb,'incidents','existing'), {
    status:'ASSIGNED', assignedTo:'Team A', revision:1,
    lastEventId:eid, updatedAt:serverTimestamp(), ...fields
  });
  b.set(doc(managerDb,'incidents','existing','history',eid), {
    ...event('ASSIGN','NEW','ASSIGNED','mgr',1),...eventFields
  });
  return b.commit();
}
before(async()=>{
  env=await initializeTestEnvironment({
    projectId,firestore:{host:'127.0.0.1',port:8087,rules}
  });
});
after(async()=>{if(env)await env.cleanup();});
beforeEach(async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx)=>{
    const admin=ctx.firestore();
    for(const uid of ['alice','bob','mgr'])
      await setDoc(doc(admin,'users',uid),profile(uid,uid==='mgr'?'MANAGER':'RESIDENT'));
    await setDoc(doc(admin,'incidents','existing'),{
      residentId:'alice',title:'Working light',description:'Hall light is out',
      location:'Hallway',priority:'NORMAL',status:'NEW',assignedTo:null,
      revision:0,lastEventId:'old-id',
      createdAt:Timestamp.fromMillis(1000),updatedAt:Timestamp.fromMillis(1000)
    });
  });
});

// Explicit failures from expanded Windows acceptance: strict, trimmed, nonblank text.
test('FIX01-01 whitespace-only title is denied',async()=>{
  await assertFails(pairedCreate(db('alice'),{title:'   '}).commit());
});
test('FIX01-02 padded title is denied',async()=>{
  await assertFails(pairedCreate(db('alice'),{title:'  Padded title '}).commit());
});
test('FIX01-03 whitespace-only description is denied',async()=>{
  await assertFails(pairedCreate(db('alice'),{description:' \t '}).commit());
});
test('FIX01-04 padded location is denied',async()=>{
  await assertFails(pairedCreate(db('alice'),{location:'  Hallway'}).commit());
});
test('FIX01-05 whitespace-only assignee is denied',async()=>{
  await assertFails(pairedAssign(db('mgr'),{assignedTo:'   '}));
});
test('FIX01-06 padded assignee is denied',async()=>{
  await assertFails(pairedAssign(db('mgr'),{assignedTo:'Team A '}));
});
test('FIX01-07 whitespace-only unnormalized note is denied',async()=>{
  await assertFails(pairedAssign(db('mgr'),{}, {note:'  '}));
});
test('FIX01-08 padded note is denied',async()=>{
  await assertFails(pairedAssign(db('mgr'),{}, {note:' note '}));
});

// Java's codePointCount, not UTF-16 code-unit length. No contract downgrade.
test('FIX01-09 120 non-BMP title code points accepted',async()=>{
  await assertSucceeds(pairedCreate(db('alice'),{title:'🦊'.repeat(120)}).commit());
});
test('FIX01-10 121 non-BMP title code points denied',async()=>{
  await assertFails(pairedCreate(db('alice'),{title:'🦊'.repeat(121)}).commit());
});
test('FIX01-11 120 ASCII title code points accepted',async()=>{
  await assertSucceeds(pairedCreate(db('alice'),{title:'A'.repeat(120)}).commit());
});
test('FIX01-12 121 ASCII title code points denied',async()=>{
  await assertFails(pairedCreate(db('alice'),{title:'A'.repeat(121)}).commit());
});
test('FIX01-13 2000 mixed non-BMP description code points accepted',async()=>{
  await assertSucceeds(pairedCreate(db('alice'),{description:'a🦊'.repeat(1000)}).commit());
});
test('FIX01-14 2001 description code points denied',async()=>{
  await assertFails(pairedCreate(db('alice'),{description:'a🦊'.repeat(1000)+'x'}).commit());
});
test('FIX01-15 160 non-BMP location code points accepted',async()=>{
  await assertSucceeds(pairedCreate(db('alice'),{location:'🦊'.repeat(160)}).commit());
});
test('FIX01-16 161 non-BMP location code points denied',async()=>{
  await assertFails(pairedCreate(db('alice'),{location:'🦊'.repeat(161)}).commit());
});
test('FIX01-17 canonical Resident owner + createdAt DESC allowed',async()=>{
  const a=db('alice');
  await assertSucceeds(getDocs(query(collection(a,'incidents'),
    where('residentId','==','alice'),orderBy('createdAt','desc'),limit(20))));
});
test('FIX01-18 Resident owner + createdAt ASC denied',async()=>{
  const a=db('alice');
  await assertFails(getDocs(query(collection(a,'incidents'),
    where('residentId','==','alice'),orderBy('createdAt','asc'),limit(20))));
});
test('FIX01-19 Manager createdAt ASC denied',async()=>{
  const m=db('mgr');
  await assertFails(getDocs(query(collection(m,'incidents'),
    orderBy('createdAt','asc'),limit(50))));
});
test('FIX01-20 Manager createdAt DESC allowed',async()=>{
  const m=db('mgr');
  await assertSucceeds(getDocs(query(collection(m,'incidents'),
    orderBy('createdAt','desc'),limit(50))));
});
test('FIX01-21 owner-filtered query for another resident denied',async()=>{
  const a=db('alice');
  await assertFails(getDocs(query(collection(a,'incidents'),
    where('residentId','==','bob'),orderBy('createdAt','desc'),limit(20))));
});

// Atomicity and budget smoke, NOT a replacement for access-call instrumentation or cloud indexes.
test('FIX01-22 CREATE plus second unreferenced history denied atomically',async()=>{
  const a=db('alice'),id=nextId(),eid='create-'+id,b=writeBatch(a);
  b.set(doc(a,'incidents',id),root(eid));
  b.set(doc(a,'incidents',id,'history',eid),event('CREATE',null,'NEW','alice',0));
  b.set(doc(a,'incidents',id,'history','extra-'+eid),event('CREATE',null,'NEW','alice',0));
  await assertFails(b.commit());
  await env.withSecurityRulesDisabled(async(ctx)=>{
    const actual=await getDoc(doc(ctx.firestore(),'incidents',id));
    assert.equal(actual.exists(),false,'failed batch must not persist root');
  });
});
test('FIX01-23 two competing same-revision ASSIGN batches have only one winner',async()=>{
  const [a,b]=await Promise.allSettled([
    pairedAssign(db('mgr')),pairedAssign(db('mgr'))
  ]);
  assert.equal([a,b].filter(x=>x.status==='fulfilled').length,1);
  await env.withSecurityRulesDisabled(async(ctx)=>{
    const d=await getDoc(doc(ctx.firestore(),'incidents','existing'));
    assert.equal(d.data().revision,1);
  });
});
