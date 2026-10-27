import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import {
  collection, doc, getDoc, getDocs, limit, orderBy, query, runTransaction,
  serverTimestamp, setDoc, Timestamp, where, writeBatch
} from 'firebase/firestore';

// TEST-ONLY contract acceptance. A JS transaction here is an independent prototype,
// NOT an implementation of Android FirebaseIncidentRepository or a production role grant.
const rules=readFileSync(new URL('./incidents.writepilot.candidate.rules',import.meta.url),'utf8');
const indexes=JSON.parse(readFileSync(new URL('./firestore.indexes.candidate.json',import.meta.url),'utf8'));
let env,serial=0;
const uidDb=(uid)=>env.authenticatedContext(uid,{email:uid+'@example.test'}).firestore();
const profile=(uid,role='RESIDENT')=>({
  uid,role,displayName:'Demo '+uid,email:uid+'@example.test',createdAt:Timestamp.fromMillis(1000)
});
const incident=(eid,owner='alice')=>({
  residentId:owner,title:'Lamp needs repair',description:'Room light failure',
  location:'Hallway A',priority:'NORMAL',status:'NEW',assignedTo:null,revision:0,
  lastEventId:eid,createdAt:serverTimestamp(),updatedAt:serverTimestamp()
});
const history=(action,from,to,actor,rev,note=null)=>({
  action,fromStatus:from,toStatus:to,actorId:actor,revision:rev,note,
  createdAt:serverTimestamp()
});
const newId=()=>`final-${++serial}`;
function createBatch(db,id){
  const b=writeBatch(db),eid='create-'+id;
  b.set(doc(db,'incidents',id),incident(eid));
  b.set(doc(db,'incidents',id,'history',eid),history('CREATE',null,'NEW','alice',0));
  return b;
}
function createMany(db,n){
  const b=writeBatch(db),ids=[];
  for(let i=0;i<n;i++){
    const id=newId(),eid='create-'+id;ids.push(id);
    b.set(doc(db,'incidents',id),incident(eid));
    b.set(doc(db,'incidents',id,'history',eid),history('CREATE',null,'NEW','alice',0));
  }
  return {ids,commit:()=>b.commit()};
}
const seedRoot=async(id='root')=>env.withSecurityRulesDisabled(async(ctx)=>{
  const d=ctx.firestore();
  await setDoc(doc(d,'incidents',id),{
    ...incident('seed'),createdAt:Timestamp.fromMillis(1000),
    updatedAt:Timestamp.fromMillis(1000)
  });
});
function assignBatch(db,id,eventId){
  const b=writeBatch(db);
  b.update(doc(db,'incidents',id),{status:'ASSIGNED',assignedTo:'Team A',
    revision:1,lastEventId:eventId,updatedAt:serverTimestamp()});
  b.set(doc(db,'incidents',id,'history',eventId),
    history('ASSIGN','NEW','ASSIGNED','mgr',1));
  return b.commit();
}
async function checkedAssignTransaction(db,id,expectedRevision,eventId){
  const rootRef=doc(db,'incidents',id);
  return runTransaction(db,async(tx)=>{
    const snapshot=await tx.get(rootRef);
    if(!snapshot.exists())throw Error('NOT_FOUND');
    const current=snapshot.data();
    // Stable eventId is never generated inside the retried transaction function.
    if(current.status!=='NEW'||current.revision!==expectedRevision)throw Error('CONFLICT');
    tx.update(rootRef,{
      status:'ASSIGNED',assignedTo:'Team A',revision:expectedRevision+1,
      lastEventId:eventId,updatedAt:serverTimestamp()
    });
    tx.set(doc(db,'incidents',id,'history',eventId),
      history('ASSIGN','NEW','ASSIGNED','mgr',expectedRevision+1));
    return eventId;
  },{maxAttempts:3});
}
before(async()=>{env=await initializeTestEnvironment({
  projectId:'demo-baocaosuco-sec02a',
  firestore:{host:'127.0.0.1',port:8087,rules}
});});
after(async()=>{if(env)await env.cleanup();});
beforeEach(async()=>{
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async(ctx)=>{
    for(const uid of ['alice','bob','mgr'])
      await setDoc(doc(ctx.firestore(),'users',uid),profile(uid,uid==='mgr'?'MANAGER':'RESIDENT'));
  });
});

test('FINAL-01 index JSON defines canonical resident composite fields and scope',()=>{
  assert.equal(indexes.indexes.length,1);
  assert.deepEqual(indexes.indexes[0],{
    collectionGroup:'incidents',queryScope:'COLLECTION',
    fields:[{fieldPath:'residentId',order:'ASCENDING'},
      {fieldPath:'createdAt',order:'DESCENDING'}]
  });
  assert.deepEqual(indexes.fieldOverrides,[]);
});
test('FINAL-02 canonical Resident owner newest-first query with limit 20 succeeds',async()=>{
  await assertSucceeds(createBatch(uidDb('alice'),newId()).commit());
  await assertSucceeds(getDocs(query(collection(uidDb('alice'),'incidents'),
    where('residentId','==','alice'),orderBy('createdAt','desc'),limit(20))));
});
test('FINAL-03 canonical Manager newest-first query with limit 50 succeeds',async()=>{
  await assertSucceeds(createBatch(uidDb('alice'),newId()).commit());
  await assertSucceeds(getDocs(query(collection(uidDb('mgr'),'incidents'),
    orderBy('createdAt','desc'),limit(50))));
});
test('FINAL-04 Resident wrong-owner and oversized list both denied',async()=>{
  const a=uidDb('alice');
  await assertFails(getDocs(query(collection(a,'incidents'),where('residentId','==','bob'),
    orderBy('createdAt','desc'),limit(20))));
  await assertFails(getDocs(query(collection(a,'incidents'),where('residentId','==','alice'),
    orderBy('createdAt','desc'),limit(21))));
});
test('FINAL-05 Manager over 50 and ascending list both denied',async()=>{
  const mgr=uidDb('mgr');
  await assertFails(getDocs(query(collection(mgr,'incidents'),orderBy('createdAt','desc'),limit(51))));
  await assertFails(getDocs(query(collection(mgr,'incidents'),orderBy('createdAt','asc'),limit(50))));
});
test('FINAL-06 history chronological ASC within 50 and deterministic ID tie break',async()=>{
  const root='history-root';await seedRoot(root);
  await env.withSecurityRulesDisabled(async ctx=>{
    const admin=ctx.firestore();
    for(const id of ['b','a','c'])await setDoc(doc(admin,'incidents',root,'history',id),{
      ...history('ASSIGN','NEW','ASSIGNED','mgr',1),
      createdAt:Timestamp.fromMillis(id==='c'?3000:2000)
    });
  });
  const m=uidDb('mgr');
  const rows=await assertSucceeds(getDocs(query(collection(m,'incidents',root,'history'),
    orderBy('createdAt','asc'),limit(50))));
  assert.deepEqual(rows.docs.map(x=>x.id),['a','b','c']);
});
test('FINAL-07 history descending, unordered, overlimit, cross-owner denied',async()=>{
  const root='history-root';await seedRoot(root);
  const owner=uidDb('alice'),other=uidDb('bob'),manager=uidDb('mgr');
  const coll=(db)=>collection(db,'incidents',root,'history');
  await assertFails(getDocs(query(coll(owner),orderBy('createdAt','desc'),limit(50))));
  await assertFails(getDocs(query(coll(owner),limit(50))));
  await assertFails(getDocs(query(coll(manager),orderBy('createdAt','asc'),limit(51))));
  await assertFails(getDocs(query(coll(other),orderBy('createdAt','asc'),limit(50))));
});
test('FINAL-08 one paired CREATE within operation budget succeeds',async()=>{
  const t=createMany(uidDb('alice'),1);await assertSucceeds(t.commit());
});
test('FINAL-09 two paired CREATEs succeed, persisted atomically',async()=>{
  const t=createMany(uidDb('alice'),2);await assertSucceeds(t.commit());
  await env.withSecurityRulesDisabled(async(ctx)=>{
    for(const id of t.ids){
      const d=await getDoc(doc(ctx.firestore(),'incidents',id));
      assert.equal(d.data().revision,0);
      const ev=await getDoc(doc(ctx.firestore(),'incidents',id,'history','create-'+id));
      assert.equal(ev.exists(),true);
    }
  });
});
test('FINAL-10 five paired CREATEs supported by this emulator',async()=>{
  const t=createMany(uidDb('alice'),5);await assertSucceeds(t.commit());
});
test('FINAL-11 ten paired CREATEs fail atomically at aggregate access budget',async()=>{
  const t=createMany(uidDb('alice'),10);await assertFails(t.commit());
  await env.withSecurityRulesDisabled(async(ctx)=>{
    for(const id of t.ids){
      assert.equal((await getDoc(doc(ctx.firestore(),'incidents',id))).exists(),false);
      assert.equal((await getDoc(doc(ctx.firestore(),'incidents',id,'history','create-'+id))).exists(),false);
    }
  });
});
test('FINAL-12 protected Manager one incident + history transition succeeds',async()=>{
  await seedRoot('r12');
  await assertSucceeds(assignBatch(uidDb('mgr'),'r12',newId()));
  const d=await getDoc(doc(uidDb('mgr'),'incidents','r12'));
  assert.equal(d.data().revision,1);
});
test('FINAL-13 two racing transaction preconditions: one commit, one conflict',async()=>{
  await seedRoot('race');
  const db=uidDb('mgr');
  const ids=[newId(),newId()];
  const results=await Promise.allSettled(ids.map(id=>checkedAssignTransaction(db,'race',0,id)));
  assert.equal(results.filter(x=>x.status==='fulfilled').length,1,JSON.stringify(results));
  assert.equal(results.filter(x=>x.status==='rejected').length,1,JSON.stringify(results));
  const snap=await getDoc(doc(db,'incidents','race'));
  assert.equal(snap.data().revision,1);
  const histories=await getDocs(query(collection(db,'incidents','race','history'),
    orderBy('createdAt','asc'),limit(50)));
  assert.equal(histories.size,1);
  assert.equal(histories.docs[0].id,snap.data().lastEventId);
});
test('FINAL-14 repeated stable event ID after commit cannot write duplicate history',async()=>{
  await seedRoot('replay');
  const mgr=uidDb('mgr'),eid=newId();
  await assertSucceeds(assignBatch(mgr,'replay',eid));
  await assertFails(assignBatch(mgr,'replay',eid));
  const root=await getDoc(doc(mgr,'incidents','replay'));
  assert.equal(root.data().revision,1);
  const events=await getDocs(query(collection(mgr,'incidents','replay','history'),
    orderBy('createdAt','asc'),limit(50)));
  assert.equal(events.size,1);
});
test('FINAL-15 second requestId against stale expected revision must conflict',async()=>{
  await seedRoot('stale');
  const mgr=uidDb('mgr');
  await checkedAssignTransaction(mgr,'stale',0,newId());
  await assert.rejects(checkedAssignTransaction(mgr,'stale',0,newId()),/CONFLICT/);
});
test('FINAL-16 forged actor or mismatched event in paired command denied',async()=>{
  await seedRoot('forged');
  const m=uidDb('mgr'),id=newId(),b=writeBatch(m);
  b.update(doc(m,'incidents','forged'),{
    status:'ASSIGNED',assignedTo:'Team A',revision:1,
    lastEventId:id,updatedAt:serverTimestamp()
  });
  b.set(doc(m,'incidents','forged','history',id),
    history('ASSIGN','NEW','ASSIGNED','bob',1));
  await assertFails(b.commit());
  const root=await getDoc(doc(m,'incidents','forged'));
  assert.equal(root.data().revision,0);
});
