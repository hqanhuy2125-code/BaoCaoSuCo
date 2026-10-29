import { before, beforeEach, after, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment }
  from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, limit, query, serverTimestamp,
  setDoc, Timestamp, updateDoc, where, writeBatch
} from 'firebase/firestore';

// SEC-02B pilot: a separate, NON-DEPLOYABLE experimental Rules file.
// NOTE: the script does not change root firestore.rules or touch a live project.
const rules = readFileSync(new URL('./incidents.writepilot.candidate.rules', import.meta.url), 'utf8');
if (!rules.includes('getAfter(') || !rules.includes('lastEventId') ||
    !rules.includes('SEC-02B EXPERIMENTAL TEST-ONLY WRITE PILOT')) {
  throw new Error('Missing or unexpected SEC-02B candidate Rules source');
}
let env;
const user = (id) => env.authenticatedContext(id, {email:id + '@example.test'}).firestore();
const unauth = () => env.unauthenticatedContext().firestore();
const profile = (id, role='RESIDENT') => ({
  uid:id, displayName:'Demo '+id, email:id+'@example.test', role,
  createdAt:Timestamp.fromMillis(1000)
});
const incident = (owner='alice', status='NEW', revision=0, assignedTo=null, lastEventId='e0') => ({
  residentId:owner, title:'Light broken', description:'Demo description',
  location:'Hallway A', priority:'NORMAL', status, assignedTo,
  createdAt:Timestamp.fromMillis(1000), updatedAt:Timestamp.fromMillis(1000),
  revision, lastEventId
});
const entry = (action, fromStatus, toStatus, actor, revision, timestamp=serverTimestamp()) => ({
  action, fromStatus, toStatus, actorId:actor, revision, createdAt:timestamp, note:null
});
const createIncident = (db, incidentId, owner='alice', eventId='create-1') => {
  const batch=writeBatch(db);
  batch.set(doc(db,'incidents',incidentId), {
    ...incident(owner,'NEW',0,null,eventId),
    createdAt:serverTimestamp(), updatedAt:serverTimestamp()
  });
  batch.set(doc(db,'incidents',incidentId,'history',eventId),
    entry('CREATE',null,'NEW',owner,0));
  return batch.commit();
};
const updateWithEvent = (db, id, from, to, action, actor, rev, eventId,
                         handler=undefined) => {
  const batch=writeBatch(db);
  const patch={status:to,revision:rev,lastEventId:eventId,updatedAt:serverTimestamp()};
  if (handler !== undefined) patch.assignedTo=handler;
  batch.update(doc(db,'incidents',id),patch);
  batch.set(doc(db,'incidents',id,'history',eventId),
    entry(action,from,to,actor,rev));
  return batch.commit();
};
const seed = async (id,status,rev,handler='Team A',owner='alice') => {
  await env.withSecurityRulesDisabled(async c => {
    await setDoc(doc(c.firestore(),'incidents',id),
      incident(owner,status,rev,status==='NEW'?null:handler,'old-event'));
  });
};

before(async()=>{
  env=await initializeTestEnvironment({
    projectId:'demo-baocaosuco-sec02a',
    firestore:{host:'127.0.0.1',port:8087,rules}
  });
});
after(async()=>{if(env)await env.cleanup();});
beforeEach(async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c=>{
    for (const id of ['alice','bob']) {
      await setDoc(doc(c.firestore(),'users',id),profile(id));
    }
    await setDoc(doc(c.firestore(),'users','mgr'),profile('mgr','MANAGER'));
    await setDoc(doc(c.firestore(),'incidents','i1'),incident());
    await setDoc(doc(c.firestore(),'incidents','i2'),incident('bob'));
  });
});

test('paired CREATE + history permitted for authenticated resident', async()=>{
  await assertSucceeds(createIncident(user('alice'),'new-created'));
  await assertSucceeds(getDoc(doc(user('alice'),'incidents','new-created')));
});
test('CREATE incident WITHOUT history is denied', async()=>{
  await assertFails(setDoc(doc(user('alice'),'incidents','no-history'),{
    ...incident('alice','NEW',0,null,'missing'),
    createdAt:serverTimestamp(),updatedAt:serverTimestamp()
  }));
});
test('standalone fake history CREATE is denied', async()=>{
  await assertFails(setDoc(doc(user('alice'),'incidents','i1','history','fake'),
    entry('CREATE',null,'NEW','alice',0)));
});
test('creator cannot set another resident UID', async()=>{
  await assertFails(createIncident(user('alice'),'wrong-owner','bob','new-event'));
});
test('MANAGER profile cannot CREATE resident incident as manager', async()=>{
  await assertFails(createIncident(user('mgr'),'manager-create','mgr','new-event'));
});
test('fake local timestamps are denied on incident CREATE', async()=>{
  const db=user('alice'), b=writeBatch(db);
  b.set(doc(db,'incidents','localtime'),{
    ...incident('alice','NEW',0,null,'time-evt'),
    createdAt:Timestamp.fromMillis(2222),updatedAt:Timestamp.fromMillis(2222)
  });
  b.set(doc(db,'incidents','localtime','history','time-evt'),
    entry('CREATE',null,'NEW','alice',0,Timestamp.fromMillis(2222)));
  await assertFails(b.commit());
});
test('forged extra incident field dueAt is denied',async()=>{
  const db=user('alice'),b=writeBatch(db);
  b.set(doc(db,'incidents','due-forged'),{
    ...incident('alice','NEW',0,null,'d-evt'),
    createdAt:serverTimestamp(),updatedAt:serverTimestamp(),
    dueAt:Timestamp.fromMillis(999999)
  });
  b.set(doc(db,'incidents','due-forged','history','d-evt'),
    entry('CREATE',null,'NEW','alice',0));
  await assertFails(b.commit());
});
test('Manager NEW to ASSIGNED + linked history permitted',async()=>{
  await assertSucceeds(updateWithEvent(user('mgr'),'i1','NEW','ASSIGNED',
    'ASSIGN','mgr',1,'assign-1','Team A'));
});
test('Manager ASSIGNED to IN_PROGRESS permitted',async()=>{
  await seed('i1','ASSIGNED',1);
  await assertSucceeds(updateWithEvent(user('mgr'),'i1','ASSIGNED','IN_PROGRESS',
    'START_WORK','mgr',2,'start-2'));
});
test('Manager IN_PROGRESS to COMPLETED permitted',async()=>{
  await seed('i1','IN_PROGRESS',2);
  await assertSucceeds(updateWithEvent(user('mgr'),'i1','IN_PROGRESS','COMPLETED',
    'MARK_COMPLETE','mgr',3,'complete-3'));
});
test('Owning RESIDENT COMPLETED to CLOSED permitted',async()=>{
  await seed('i1','COMPLETED',3);
  await assertSucceeds(updateWithEvent(user('alice'),'i1','COMPLETED','CLOSED',
    'CONFIRM_RESOLVED','alice',4,'close-4'));
});
test('Owning RESIDENT COMPLETED to IN_PROGRESS permitted',async()=>{
  await seed('i1','COMPLETED',3);
  await assertSucceeds(updateWithEvent(user('alice'),'i1','COMPLETED','IN_PROGRESS',
    'REPORT_UNRESOLVED','alice',4,'reopen-4'));
});
test('unrelated resident cannot close someone else incident',async()=>{
  await seed('i1','COMPLETED',3);
  await assertFails(updateWithEvent(user('bob'),'i1','COMPLETED','CLOSED',
    'CONFIRM_RESOLVED','bob',4,'hijack-4'));
});
test('resident cannot execute Manager ASSIGN',async()=>{
  await assertFails(updateWithEvent(user('alice'),'i1','NEW','ASSIGNED',
    'ASSIGN','alice',1,'fake-assign','Me'));
});
test('Manager cannot skip directly from NEW to COMPLETED',async()=>{
  await assertFails(updateWithEvent(user('mgr'),'i1','NEW','COMPLETED',
    'MARK_COMPLETE','mgr',1,'skip-1','Team A'));
});
test('standalone root status update without history is denied',async()=>{
  await assertFails(updateDoc(doc(user('mgr'),'incidents','i1'),{
    status:'ASSIGNED',assignedTo:'Team A',updatedAt:serverTimestamp(),
    revision:1,lastEventId:'missing'
  }));
});
test('forged history actor does not match authenticated Manager',async()=>{
  await assertFails(updateWithEvent(user('mgr'),'i1','NEW','ASSIGNED',
    'ASSIGN','bob',1,'forged','Team A'));
});
test('stale revision on an otherwise valid transition is denied',async()=>{
  await assertFails(updateWithEvent(user('mgr'),'i1','NEW','ASSIGNED',
    'ASSIGN','mgr',3,'stale','Team A'));
});
test('incident owner cannot mutate protected priority',async()=>{
  await assertFails(updateDoc(doc(user('alice'),'incidents','i1'),{
    priority:'URGENT',updatedAt:serverTimestamp(),revision:1,lastEventId:'tamper'
  }));
});
test('history updates and deletes are always denied to clients',async()=>{
  await env.withSecurityRulesDisabled(async c=>{
    await setDoc(doc(c.firestore(),'incidents','i1','history','h0'),
      entry('CREATE',null,'NEW','alice',0,Timestamp.fromMillis(1000)));
  });
  await assertFails(updateDoc(doc(user('mgr'),'incidents','i1','history','h0'),{actorId:'mgr'}));
  await assertFails(deleteDoc(doc(user('alice'),'incidents','i1','history','h0')));
});
test('resident cannot list incidents without owner filter',async()=>{
  await assertFails(getDocs(query(collection(user('alice'),'incidents'),limit(20))));
});
test('resident may list only their own incidents with bounded query',async()=>{
  await assertSucceeds(getDocs(query(collection(user('alice'),'incidents'),
    where('residentId','==','alice'),limit(20))));
});
test('manager can read bounded incident list',async()=>{
  await assertSucceeds(getDocs(query(collection(user('mgr'),'incidents'),limit(50))));
});
test('anonymous incident reads are denied',async()=>{
  await assertFails(getDoc(doc(unauth(),'incidents','i1')));
});

test('replay of the same event id after success is denied',async()=>{
  await assertSucceeds(updateWithEvent(user('mgr'),'i1','NEW','ASSIGNED',
    'ASSIGN','mgr',1,'replay-id','Team A'));
  await assertFails(updateWithEvent(user('mgr'),'i1','NEW','ASSIGNED',
    'ASSIGN','mgr',1,'replay-id','Team A'));
});
test('CLOSED incident is terminal',async()=>{
  await seed('i1','CLOSED',4);
  await assertFails(updateWithEvent(user('mgr'),'i1','CLOSED','IN_PROGRESS',
    'START_WORK','mgr',5,'illegal-after-close'));
});
test('mismatched incident and history transition cannot commit',async()=>{
  const db=user('mgr'), b=writeBatch(db);
  b.update(doc(db,'incidents','i1'),{
    status:'ASSIGNED',assignedTo:'Team A',revision:1,
    lastEventId:'bad-event',updatedAt:serverTimestamp()
  });
  b.set(doc(db,'incidents','i1','history','bad-event'),
    entry('MARK_COMPLETE','IN_PROGRESS','COMPLETED','mgr',1));
  await assertFails(b.commit());
});
test('initial incident and history event ids must match',async()=>{
  const db=user('alice'), b=writeBatch(db);
  b.set(doc(db,'incidents','unlinked-create'),{
    ...incident('alice','NEW',0,null,'expected-event'),
    createdAt:serverTimestamp(),updatedAt:serverTimestamp()
  });
  b.set(doc(db,'incidents','unlinked-create','history','wrong-event'),
    entry('CREATE',null,'NEW','alice',0));
  await assertFails(b.commit());
});
