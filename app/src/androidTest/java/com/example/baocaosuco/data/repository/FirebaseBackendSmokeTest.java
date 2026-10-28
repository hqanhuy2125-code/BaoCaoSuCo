package com.example.baocaosuco.data.repository;

import android.content.Context;
import android.os.Looper;
import android.security.NetworkSecurityPolicy;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.baocaosuco.auth.AuthRepository;
import com.example.baocaosuco.auth.FirebaseAuthRepository;
import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.data.model.User;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.Source;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** A06/A07: opt-in Android SDK tests. Never provisions a Manager or deletes data. */
@RunWith(AndroidJUnit4.class)
public class FirebaseBackendSmokeTest {
    private static final String LIVE_PROJECT = "baocaosuco-c4ccc";
    private static final String DEMO_PROJECT = "demo-baocaosuco-smoke";
    private final List<Session> sessions = new ArrayList<>();
    private final Properties config = new Properties();
    private Context context;
    private boolean emulator;

    @Before public void configure() throws Exception {
        String mode = InstrumentationRegistry.getArguments().getString("backendSmoke", "off");
        assumeTrue("NOT_RUN: explicitly select backendSmoke=emulator or live",
                mode.equals("emulator") || mode.equals("live"));
        emulator = mode.equals("emulator");
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Mat khau chi doc tu file rieng tren thiet bi, khong truyen qua runner arguments.
        File fixture = new File(context.getFilesDir(), "backend-smoke.properties");
        assertTrue("BLOCKED: private backend-smoke.properties missing", fixture.isFile());
        try (FileInputStream input = new FileInputStream(fixture)) { config.load(input); }
        assertEquals("Explicit project acknowledgement required",
                emulator ? DEMO_PROJECT : LIVE_PROJECT, required("projectId"));
        if (!emulator) assertEquals("Live test data writes require acknowledgement",
                "yes", required("allowLiveTestWrites"));
    }

    @After public void closeSessions() throws Exception {
        for (Session session : sessions) {
            session.auth.signOut();
            try { task(session.db.terminate()); }
            finally { session.app.delete(); }
        }
    }

    @Test public void residentLoginProfileAndAuthorization() throws Exception {
        Session resident = session("resident");
        User user = login(resident, "RESIDENT");
        assertEquals(user.getUid(), authCall(resident.authRepo::getCurrentUser).getUid());
        DocumentSnapshot before = task(resident.db.collection("users").document(user.getUid())
                .get(Source.SERVER));
        assertProfile(before, user.getUid(), "RESIDENT");
        String originalName = before.getString("displayName");
        try {
            task(before.getReference().update("displayName", "A06 Smoke Resident"));
            DocumentSnapshot renamed = task(before.getReference().get(Source.SERVER));
            assertEquals("A06 Smoke Resident", renamed.getString("displayName"));
            assertEquals("A06 Smoke Resident",
                    authCall(resident.authRepo::getCurrentUser).getDisplayName());
            denied(before.getReference().update("role", "MANAGER"));
            assertProfile(task(before.getReference().get(Source.SERVER)), user.getUid(), "RESIDENT");
            assertRepoError(IncidentRepository.Error.PERMISSION_DENIED,
                    resident.repo.manager()::listAll);
        } finally {
            // Chi khoi phuc ten cua tai khoan demo; khong xoa profile/Auth hay doi quyen.
            task(before.getReference().update("displayName", originalName));
        }
        resident.authRepo.signOut();
        assertNull(authCall(resident.authRepo::getCurrentUser));
        assertRepoError(IncidentRepository.Error.AUTH_REQUIRED, resident.repo.resident()::listMine);
        authError(cb -> resident.authRepo.signIn(required("residentEmail"), UUID.randomUUID().toString(), cb));
        assertNull(authCall(resident.authRepo::getCurrentUser));
        login(resident, "RESIDENT");
    }

    @Test public void registrationCreatesResidentProfile() throws Exception {
        assumeTrue("NOT_RUN: signupEmail/signupPassword must name an approved unused test identity",
                config.containsKey("signupEmail") && config.containsKey("signupPassword"));
        Session signup = session("signup");
        User created = authCall(cb -> signup.authRepo.register("A06 Signup Smoke",
                required("signupEmail"), required("signupPassword"), cb));
        assertEquals("RESIDENT", created.getRole());
        assertProfile(task(signup.db.collection("users").document(created.getUid()).get(Source.SERVER)),
                created.getUid(), "RESIDENT");
        authError(cb -> signup.authRepo.register("Duplicate Signup", required("signupEmail"),
                required("signupPassword"), cb));
        assertEquals(created.getUid(), signup.auth.getCurrentUser().getUid());
        assertProfile(task(signup.db.collection("users").document(created.getUid()).get(Source.SERVER)),
                created.getUid(), "RESIDENT");
        signup.authRepo.signOut();
        assertNull(authCall(signup.authRepo::getCurrentUser));
        User signedIn = authCall(cb -> signup.authRepo.signIn(required("signupEmail"),
                required("signupPassword"), cb));
        assertEquals(created.getUid(), signedIn.getUid());
        // Giu tai khoan de owner doi chieu Auth + Firestore; khong tu xoa hay tai su dung email.
    }

    @Test public void incidentLifecycleWithTrustedManagerAndSecondResident() throws Exception {
        Session resident = session("resident");
        Session manager = session("manager");
        Session other = session("otherResident");
        User owner = login(resident, "RESIDENT");
        User trusted = login(manager, "MANAGER");
        User stranger = login(other, "RESIDENT");
        assertNotEquals(owner.getUid(), trusted.getUid());
        assertNotEquals(owner.getUid(), stranger.getUid());
        String runId = UUID.randomUUID().toString();
        IncidentSnapshot created = repoCall(cb -> resident.repo.resident().create(
                new NewIncidentInput("A07 smoke " + runId, "Authorized backend smoke test",
                        "DEMO-ONLY", "URGENT"), cb));
        assertEquals("NEW", created.getStatusCode());
        assertEquals(0, created.getRevision());
        assertEquals(owner.getUid(), created.getResidentId());
        assertEquals(4L * 60 * 60 * 1000, created.getDueAtMillis() - created.getCreatedAtMillis());
        assertTrue(contains(repoCall(resident.repo.resident()::listMine), created.getId()));
        assertTrue(contains(repoCall(manager.repo.manager()::listAll), created.getId()));
        assertFalse(contains(repoCall(other.repo.resident()::listMine), created.getId()));
        assertEquals(created.getId(), FirebaseBackendSmokeTest.<IncidentSnapshot>repoCall(
                cb -> resident.repo.resident().getMine(created.getId(), cb)).getId());
        FirebaseBackendSmokeTest.<IncidentSnapshot>assertRepoError(IncidentRepository.Error.PERMISSION_DENIED,
                cb -> other.repo.resident().getMine(created.getId(), cb));
        FirebaseBackendSmokeTest.<List<IncidentHistorySnapshot>>assertRepoError(IncidentRepository.Error.PERMISSION_DENIED,
                cb -> other.repo.resident().historyMine(created.getId(), cb));
        // Probe Rules truc tiep, tranh chi chung minh role guard o Java.
        denied(other.db.collection("incidents").document(created.getId()).get(Source.SERVER));
        denied(resident.db.collection("incidents").orderBy("createdAt",
                com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50).get(Source.SERVER));
        FirebaseBackendSmokeTest.<IncidentSnapshot>assertRepoError(IncidentRepository.Error.PERMISSION_DENIED,
                cb -> manager.repo.manager().complete(created.getId(), expected(created), cb));
        CommandPrecondition assignRequest = expected(created);
        FirebaseBackendSmokeTest.<IncidentSnapshot>assertRepoError(IncidentRepository.Error.VALIDATION,
                cb -> manager.repo.manager().assign(created.getId(), assignRequest, " ", cb));
        IncidentSnapshot assigned = repoCall(cb -> manager.repo.manager().assign(
                created.getId(), assignRequest, "A07 Demo Handler", cb));
        assertEquals("ASSIGNED", assigned.getStatusCode());
        assertEquals(1, assigned.getRevision());
        FirebaseBackendSmokeTest.<IncidentSnapshot>assertRepoError(IncidentRepository.Error.CONFLICT,
                cb -> manager.repo.manager().assign(created.getId(), assignRequest, "A07 Demo Handler", cb));
        FirebaseBackendSmokeTest.<IncidentSnapshot>assertRepoError(IncidentRepository.Error.CONFLICT,
                cb -> manager.repo.manager().start(created.getId(), expected(created), cb));
        IncidentSnapshot started = repoCall(cb -> manager.repo.manager().start(
                created.getId(), expected(assigned), cb));
        assertEquals("IN_PROGRESS", started.getStatusCode());
        IncidentSnapshot completed = repoCall(cb -> manager.repo.manager().complete(
                created.getId(), expected(started), cb));
        assertEquals("COMPLETED", completed.getStatusCode());
        FirebaseBackendSmokeTest.<IncidentSnapshot>assertRepoError(IncidentRepository.Error.PERMISSION_DENIED,
                cb -> other.repo.resident().confirm(created.getId(), expected(completed), true, cb));
        IncidentSnapshot reopened = repoCall(cb -> resident.repo.resident().confirm(
                created.getId(), expected(completed), false, cb));
        assertEquals("IN_PROGRESS", reopened.getStatusCode());
        assertEquals(created.getDueAtMillis(), reopened.getDueAtMillis());
        IncidentSnapshot completedAgain = repoCall(cb -> manager.repo.manager().complete(
                created.getId(), expected(reopened), cb));
        IncidentSnapshot closed = repoCall(cb -> resident.repo.resident().confirm(
                created.getId(), expected(completedAgain), true, cb));
        assertEquals("CLOSED", closed.getStatusCode());
        assertEquals(6, closed.getRevision());
        FirebaseBackendSmokeTest.<IncidentSnapshot>assertRepoError(IncidentRepository.Error.PERMISSION_DENIED,
                cb -> resident.repo.resident().confirm(created.getId(), expected(closed), true, cb));
        List<IncidentHistorySnapshot> history = repoCall(
                cb -> resident.repo.resident().historyMine(created.getId(), cb));
        String[] actions = {"CREATE", "ASSIGN", "START_WORK", "MARK_COMPLETE",
                "REPORT_UNRESOLVED", "MARK_COMPLETE", "CONFIRM_RESOLVED"};
        String[] states = {"NEW", "ASSIGNED", "IN_PROGRESS", "COMPLETED",
                "IN_PROGRESS", "COMPLETED", "CLOSED"};
        assertEquals(actions.length, history.size());
        for (int i = 0; i < history.size(); i++) {
            IncidentHistorySnapshot event = history.get(i);
            assertEquals(i, event.getRevision());
            assertEquals(actions[i], event.getAction().name());
            assertEquals(states[i], event.getToStatus().name());
            assertEquals(i == 0 || i == 4 || i == 6 ? owner.getUid() : trusted.getUid(), event.getActorId());
            if (i == 0) assertNull(event.getFromStatus());
            else {
                assertEquals(states[i - 1], event.getFromStatus().name());
                assertTrue(event.getOccurredAtMillis() >= history.get(i - 1).getOccurredAtMillis());
            }
        }
        assertEquals(closed.getLastEventId(), history.get(6).getEventId());
        assertEquals(closed.getUpdatedAtMillis(), history.get(6).getOccurredAtMillis());
        assertEquals(created.getCreatedAtMillis(), history.get(0).getOccurredAtMillis());
        // Emit only synthetic incident ID for owner audit; credentials and UID are never logged.
        android.util.Log.i("BackendSmoke", "A07 verified incident=" + created.getId() + " history=7");
    }

    private Session session(String identity) {
        FirebaseOptions options;
        if (emulator) {
            options = new FirebaseOptions.Builder().setProjectId(DEMO_PROJECT)
                    .setApplicationId("1:1234567890:android:1234567890abcdef123456")
                    .setApiKey("demo-emulator-key").build();
        } else {
            options = FirebaseOptions.fromResource(context);
            assertNotNull("BLOCKED: real Android google-services.json required", options);
            assertEquals("Refuse another Firebase project", LIVE_PROJECT, options.getProjectId());
        }
        FirebaseApp app = FirebaseApp.initializeApp(context, options, "smoke-" + UUID.randomUUID());
        Session session = new Session(app, identity);
        sessions.add(session);
        session.db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false).build());
        if (emulator) {
            String host = config.getProperty("emulatorHost", "localhost");
            assertTrue("Only local Android emulator/adb reverse hosts allowed",
                    host.equals("10.0.2.2") || host.equals("127.0.0.1") || host.equals("localhost"));
            assertTrue("BLOCKED: emulator HTTP disallowed; use localhost + adb reverse on API37. "
                    + "Do not weaken the production Manifest for a smoke test.",
                    NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host));
            session.auth.useEmulator(host, 9099);
            session.db.useEmulator(host, 8087);
        }
        return session;
    }

    private User login(Session session, String role) throws Exception {
        User user = authCall(cb -> session.authRepo.signIn(required(session.identity + "Email"),
                required(session.identity + "Password"), cb));
        assertEquals("BLOCKED: expected trusted role not present; no promotion is attempted", role, user.getRole());
        assertProfile(task(session.db.collection("users").document(user.getUid()).get(Source.SERVER)),
                user.getUid(), role);
        return user;
    }

    private String required(String key) {
        String value = config.getProperty(key);
        assertTrue("BLOCKED: missing private fixture property " + key, value != null && !value.isEmpty());
        return value;
    }

    private static void assertProfile(DocumentSnapshot profile, String uid, String role) {
        assertTrue(profile.exists());
        assertNotNull(profile.getData());
        assertEquals(5, profile.getData().size());
        assertEquals(uid, profile.getString("uid"));
        assertEquals(role, profile.getString("role"));
        assertNotNull(profile.getString("displayName"));
        assertNotNull(profile.getString("email"));
        assertNotNull(profile.getTimestamp("createdAt"));
    }

    private static boolean contains(List<IncidentSnapshot> items, String id) {
        for (IncidentSnapshot item : items) if (id.equals(item.getId())) return true;
        return false;
    }

    private static CommandPrecondition expected(IncidentSnapshot incident) {
        return new CommandPrecondition(incident.getStatusCode(), incident.getRevision(), UUID.randomUUID().toString());
    }

    private static <T> T task(Task<T> task) throws Exception {
        return Tasks.await(task, 45, TimeUnit.SECONDS);
    }

    private static void denied(Task<?> task) throws Exception {
        try { task(task); fail("Expected server PERMISSION_DENIED"); }
        catch (ExecutionException rejected) {
            assertTrue(rejected.getCause() instanceof FirebaseFirestoreException);
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    ((FirebaseFirestoreException) rejected.getCause()).getCode());
        }
    }

    private static <T> T repoCall(Consumer<IncidentRepository.Callback<T>> operation) throws Exception {
        Result<T> result = invoke(operation);
        assertNull("Unexpected repository error: " + result.error, result.error);
        return result.value;
    }

    private static <T> void assertRepoError(IncidentRepository.Error expected,
                                            Consumer<IncidentRepository.Callback<T>> operation) throws Exception {
        assertEquals(expected, invoke(operation).error);
    }

    private static <T> Result<T> invoke(Consumer<IncidentRepository.Callback<T>> operation) throws Exception {
        Result<T> result = new Result<>();
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Boolean> onMain = new AtomicReference<>(false);
        operation.accept(new IncidentRepository.Callback<T>() {
            @Override public void onSuccess(T value) { result.value = value; finish(); }
            @Override public void onError(IncidentRepository.Error error) { result.error = error; finish(); }
            private void finish() {
                calls.incrementAndGet();
                onMain.set(Looper.myLooper() == Looper.getMainLooper());
                done.countDown();
            }
        });
        assertTrue("Repository callback timeout", done.await(60, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals("Duplicate callback", 1, calls.get());
        assertTrue("Repository callback must run on main thread", onMain.get());
        return result;
    }

    private static User authCall(Consumer<AuthRepository.Callback<User>> operation) throws Exception {
        AtomicReference<User> value = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        operation.accept(new AuthRepository.Callback<User>() {
            @Override public void onSuccess(User user) { value.set(user); done.countDown(); }
            @Override public void onError(String message) { error.set(message); done.countDown(); }
        });
        assertTrue("Auth callback timeout", done.await(60, TimeUnit.SECONDS));
        assertTrue("Auth smoke failed; inspect provider/profile without publishing credentials", error.get() == null);
        return value.get();
    }

    private static void authError(Consumer<AuthRepository.Callback<User>> operation) throws Exception {
        AtomicReference<Boolean> rejected = new AtomicReference<>(false);
        CountDownLatch done = new CountDownLatch(1);
        operation.accept(new AuthRepository.Callback<User>() {
            @Override public void onSuccess(User user) { done.countDown(); }
            @Override public void onError(String message) { rejected.set(true); done.countDown(); }
        });
        assertTrue("Auth error callback timeout", done.await(60, TimeUnit.SECONDS));
        assertTrue("Expected Auth error without account/profile mutation", rejected.get());
    }

    private static final class Result<T> {
        T value;
        IncidentRepository.Error error;
    }

    private static final class Session {
        final FirebaseApp app;
        final FirebaseAuth auth;
        final FirebaseFirestore db;
        final FirebaseAuthRepository authRepo;
        final FirebaseIncidentRepository repo;
        final String identity;
        Session(FirebaseApp app, String identity) {
            this.app = app;
            this.identity = identity;
            auth = FirebaseAuth.getInstance(app);
            db = FirebaseFirestore.getInstance(app);
            authRepo = new FirebaseAuthRepository(auth, db);
            repo = new FirebaseIncidentRepository(auth, db);
        }
    }
}
