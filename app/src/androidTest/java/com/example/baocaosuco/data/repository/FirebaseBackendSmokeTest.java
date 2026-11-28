package com.example.baocaosuco.data.repository;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import android.os.SystemClock;
import android.security.NetworkSecurityPolicy;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.R;
import com.example.baocaosuco.auth.AuthRepository;
import com.example.baocaosuco.auth.FirebaseAuthRepository;
import com.example.baocaosuco.auth.LoginActivity;
import com.example.baocaosuco.auth.RegisterActivity;
import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.data.model.User;
import com.example.baocaosuco.manager.ManagerMainActivity;
import com.example.baocaosuco.resident.ResidentMainActivity;
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
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import java.util.function.Predicate;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** A06/A07: opt-in Android SDK tests. Never provisions a Manager or deletes data. */
@RunWith(AndroidJUnit4.class)
public class FirebaseBackendSmokeTest {
    private static final String LIVE_PROJECT = "baocaosuco-c4ccc";
    private static final String DEMO_PROJECT = "demo-baocaosuco-smoke";
    private static boolean defaultEmulatorConnected;
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

    @Test public void residentRegistrationVerificationAndProfileNavigation() throws Exception {
        FirebaseAuth auth = connectDefaultEmulator();
        String email = "f12-nav-" + UUID.randomUUID() + "@example.test";
        try (ActivityScenario<LoginActivity> ignored = ActivityScenario.launch(LoginActivity.class)) {
            awaitUi(LoginActivity.class, login -> tap(login, R.id.tv_switch_mode));
            awaitUi(RegisterActivity.class, register -> {
                ((TextView) register.findViewById(R.id.et_name)).setText("Local Resident");
                ((TextView) register.findViewById(R.id.et_email)).setText(email);
                ((TextView) register.findViewById(R.id.et_password)).setText(UUID.randomUUID().toString());
                return tap(register, R.id.btn_action);
            });
            awaitUi(LoginActivity.class, login -> isVisible(login, R.id.auth_verification_panel)
                    && enabled(login, R.id.auth_resend_verification));
            String initial = awaitVerificationCode(email, null);
            awaitUi(LoginActivity.class, login -> tap(login, R.id.auth_check_verification));
            awaitUi(LoginActivity.class, login -> isVisible(login, R.id.auth_verification_panel)
                    && enabled(login, R.id.auth_resend_verification));
            screenshot("resident-verification");
            awaitUi(LoginActivity.class, login -> tap(login, R.id.auth_resend_verification));
            task(auth.applyActionCode(awaitVerificationCode(email, initial)));
            awaitUi(LoginActivity.class, login -> tap(login, R.id.auth_check_verification));
            awaitUi(ResidentMainActivity.class, resident -> tap(resident, R.id.nav_item_projects)
                    && resident.findViewById(R.id.nav_item_projects).isSelected());
            awaitUi(ResidentMainActivity.class, resident -> isVisible(resident, R.id.btn_submit_incident));
            awaitUi(ResidentMainActivity.class, resident -> {
                ((ResidentMainActivity) resident).getOnBackPressedDispatcher().onBackPressed();
                return true;
            });
            awaitUi(ResidentMainActivity.class, resident -> !isVisible(resident, R.id.btn_submit_incident)
                    && resident.findViewById(R.id.nav_item_home).isSelected()
                    && tap(resident, R.id.nav_item_profile));
            awaitUi(ResidentMainActivity.class, resident -> ownProfile(resident, email,
                    R.string.account_profile_resident));
            screenshot("resident-profile");
            awaitUi(ResidentMainActivity.class, FirebaseBackendSmokeTest::closeProfile);
            assertNotNull(auth.getCurrentUser());
            assertTrue(auth.getCurrentUser().isEmailVerified());
        } finally {
            auth.signOut();
        }
    }

    @Test public void managerLoginOverdueNavigationAndProfile() throws Exception {
        FirebaseAuth auth = connectDefaultEmulator();
        String email = required("managerEmail");
        try (ActivityScenario<LoginActivity> ignored = ActivityScenario.launch(LoginActivity.class)) {
            awaitUi(LoginActivity.class, login -> {
                ((TextView) login.findViewById(R.id.et_email)).setText(email);
                ((TextView) login.findViewById(R.id.et_password)).setText(required("managerPassword"));
                return tap(login, R.id.btn_action);
            });
            awaitUi(ManagerMainActivity.class, manager -> tap(manager, R.id.nav_item_saved)
                    && manager.findViewById(R.id.nav_item_saved).isSelected());
            awaitUi(ManagerMainActivity.class, manager -> tap(manager, R.id.nav_item_home)
                    && manager.findViewById(R.id.nav_item_home).isSelected()
                    && !manager.findViewById(R.id.nav_item_saved).isSelected());
            awaitUi(ManagerMainActivity.class, manager -> {
                if (profile(manager) != null) return true;
                tap(manager, R.id.nav_item_profile);
                return profile(manager) != null;
            });
            awaitUi(ManagerMainActivity.class, manager -> ownProfile(manager, email, R.string.account_profile_manager));
            screenshot("manager-profile");
            awaitUi(ManagerMainActivity.class, FirebaseBackendSmokeTest::closeProfile);
        } finally {
            auth.signOut();
        }
    }

    private FirebaseAuth connectDefaultEmulator() {
        assumeTrue("Local synthetic fixture only", emulator);
        assertEquals(DEMO_PROJECT, FirebaseApp.getInstance().getOptions().getProjectId());
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (!defaultEmulatorConnected) {
            String host = config.getProperty("emulatorHost", "localhost");
            assertEquals("Use localhost with adb reverse for UI tests", "localhost", host);
            auth.useEmulator(host, 9099);
            FirebaseFirestore.getInstance().useEmulator(host, 8087);
            defaultEmulatorConnected = true;
        }
        auth.signOut();
        return auth;
    }

    private static void awaitUi(Class<? extends Activity> screen, Predicate<Activity> action) {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        AtomicInteger matched = new AtomicInteger();
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (screen.isInstance(activity) && action.test(activity)) {
                        matched.set(1);
                        return;
                    }
                }
            });
            if (matched.get() == 1) return;
            SystemClock.sleep(100);
        }
        fail("Expected UI state was not reached on " + screen.getSimpleName());
    }

    private static boolean isVisible(Activity screen, int id) {
        View view = screen.findViewById(id);
        return view != null && view.isShown();
    }

    private static boolean enabled(Activity screen, int id) {
        return isVisible(screen, id) && screen.findViewById(id).isEnabled();
    }

    private static boolean tap(Activity screen, int id) {
        return enabled(screen, id) && screen.findViewById(id).performClick();
    }

    private static AlertDialog profile(Activity activity) {
        androidx.fragment.app.Fragment dialog = ((AppCompatActivity) activity).getSupportFragmentManager()
                .findFragmentByTag("own-account-profile");
        return dialog instanceof ResidentMainActivity.AccountProfileDialog
                ? (AlertDialog) ((ResidentMainActivity.AccountProfileDialog) dialog).getDialog() : null;
    }

    private static boolean ownProfile(Activity activity, String email, int role) {
        AlertDialog dialog = profile(activity);
        TextView message = dialog == null ? null : dialog.findViewById(android.R.id.message);
        return message != null && message.getText().toString().contains(email)
                && message.getText().toString().contains(activity.getString(role));
    }

    private static boolean closeProfile(Activity activity) {
        AlertDialog dialog = profile(activity);
        return dialog != null && dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    }

    private void screenshot(String name) throws Exception {
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Unable to capture API 37 evidence", bitmap);
        File file = new File(context.getCacheDir(), name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            bitmap.recycle();
        }
    }

    private static String awaitVerificationCode(String email, String previous) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 12000;
        while (SystemClock.elapsedRealtime() < deadline) {
            URL url = new URL("http://localhost:9099/emulator/v1/projects/" + DEMO_PROJECT + "/oobCodes");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream response = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                for (int read; (read = input.read(buffer)) != -1;) response.write(buffer, 0, read);
                JSONArray codes = new JSONObject(response.toString("UTF-8")).getJSONArray("oobCodes");
                for (int i = 0; i < codes.length(); i++) {
                    JSONObject record = codes.getJSONObject(i);
                    String code = record.getString("oobCode");
                    if (email.equals(record.getString("email"))
                            && "VERIFY_EMAIL".equals(record.getString("requestType"))
                            && !code.equals(previous)) return code;
                }
            } finally {
                connection.disconnect();
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Firebase Auth Emulator did not issue a verification code");
    }

    @Test public void residentRestoresPendingDraftAndReconcilesCommittedWrite() throws Exception {
        assumeTrue("Local synthetic fixture only", emulator);
        assertEquals(DEMO_PROJECT, FirebaseApp.getInstance().getOptions().getProjectId());
        Session resident = session("resident");
        User owner = login(resident, "RESIDENT");
        String id = UUID.randomUUID().toString();
        NewIncidentInput draft = new NewIncidentInput("Draft recovery " + id,
                "Synthetic committed write", "Local fixture", "LOW");
        assertEquals(id, FirebaseBackendSmokeTest.<IncidentSnapshot>repoCall(
                cb -> resident.repo.resident().create(draft, id, cb)).getId());
        File pending = new File(context.getNoBackupFilesDir(), "resident-submit-" + android.net.Uri.encode(owner.getUid()));
        assertTrue("The local fixture account already has a pending draft", pending.createNewFile());
        try (FileOutputStream output = new FileOutputStream(pending)) {
            JSONObject contents = new JSONObject().put("id", id).put("title", draft.getTitle())
                    .put("description", draft.getDescription()).put("location", draft.getLocation())
                    .put("priority", draft.getPriority());
            output.write(contents.toString().getBytes(StandardCharsets.UTF_8));
        }
        FirebaseAuth defaultAuth = connectDefaultEmulator();
        try {
            task(defaultAuth.signInWithEmailAndPassword(required("residentEmail"), required("residentPassword")));
            try (ActivityScenario<ResidentMainActivity> activity = ActivityScenario.launch(ResidentMainActivity.class)) {
                long deadline = SystemClock.elapsedRealtime() + 12000;
                while (pending.isFile() && SystemClock.elapsedRealtime() < deadline) {
                    activity.onActivity(screen -> {
                        View submit = screen.findViewById(R.id.btn_submit_incident);
                        if (submit == null) {
                            screen.switchToCreateIncident();
                        } else if (submit.isEnabled()) {
                            TextView title = screen.findViewById(R.id.et_incident_title);
                            assertEquals("Pending draft should restore the original title", draft.getTitle(),
                                    title.getText().toString());
                            assertFalse("Retry must preserve original input", title.isEnabled());
                            assertTrue(submit.performClick());
                        }
                    });
                    SystemClock.sleep(100);
                }
                assertFalse("No successful UI reconciliation", pending.isFile());
            }
            assertEquals(1, FirebaseBackendSmokeTest.<List<IncidentHistorySnapshot>>repoCall(
                    cb -> resident.repo.resident().historyMine(id, cb)).size());
            List<IncidentSnapshot> records = repoCall(resident.repo.resident()::listMine);
            long count = records.stream().filter(incident -> draft.getTitle().equals(incident.getTitle())).count();
            assertEquals("Duplicate incident after retry", 1L, count);
        } finally {
            defaultAuth.signOut();
            if (pending.isFile()) assertTrue("Local fixture cleanup", pending.delete());
        }
    }

    @Test public void cursorPagesAndManagerLookupBeyondFirstFifty() throws Exception {
        assumeTrue("Local synthetic fixture only", emulator);
        Session resident = session("resident");
        Session manager = session("manager");
        Session other = session("otherResident");
        User owner = login(resident, "RESIDENT");
        login(manager, "MANAGER");
        login(other, "RESIDENT");
        String batch = UUID.randomUUID().toString();
        List<String> submitted = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            String id = UUID.randomUUID().toString();
            NewIncidentInput draft = new NewIncidentInput("Cursor " + batch + " " + i,
                    "Synthetic pagination", "Local fixture", "NORMAL");
            FirebaseBackendSmokeTest.<IncidentSnapshot>repoCall(
                    cb -> resident.repo.resident().create(draft, id, cb));
            submitted.add(id);
        }
        String oldest = submitted.get(0);
        IncidentRepository.Page managerFirst = repoCall(cb -> manager.repo.managerPages().load(null, cb));
        assertEquals(50, managerFirst.rows.size());
        assertFalse(contains(managerFirst.rows, oldest));
        assertNotNull(managerFirst.next);
        assertEquals(oldest, FirebaseBackendSmokeTest.<IncidentSnapshot>repoCall(
                cb -> manager.repo.manager().get(oldest, cb)).getId());
        List<IncidentSnapshot> managerRows = collectPages(manager.repo.managerPages(), managerFirst);
        assertTrue("Manager pagination dropped fixture rows",
                managerRows.stream().map(IncidentSnapshot::getId).collect(java.util.stream.Collectors.toSet())
                        .containsAll(submitted));

        IncidentRepository.Page residentFirst = repoCall(cb -> resident.repo.residentPages().load(null, cb));
        assertEquals(20, residentFirst.rows.size());
        assertNotNull(residentFirst.next);
        List<IncidentSnapshot> residentRows = collectPages(resident.repo.residentPages(), residentFirst);
        assertTrue("Resident pagination dropped fixture rows",
                residentRows.stream().map(IncidentSnapshot::getId).collect(java.util.stream.Collectors.toSet())
                        .containsAll(submitted));
        assertTrue(residentRows.stream().allMatch(row -> owner.getUid().equals(row.getResidentId())));
        FirebaseBackendSmokeTest.<IncidentRepository.Page>assertRepoError(IncidentRepository.Error.PERMISSION_DENIED,
                cb -> other.repo.residentPages().load(residentFirst.next, cb));
    }

    private static List<IncidentSnapshot> collectPages(IncidentRepository.PageSource source,
                                                        IncidentRepository.Page first) throws Exception {
        List<IncidentSnapshot> rows = new ArrayList<>(first.rows);
        IncidentRepository.Page page = first;
        for (int i = 0; i < 10 && page.next != null; i++) {
            IncidentRepository.PageCursor cursor = page.next;
            page = repoCall(cb -> source.load(cursor, cb));
            rows.addAll(page.rows);
        }
        assertNull("Expected pagination to exhaust the small local dataset", page.next);
        assertEquals("Cursor returned duplicate incident IDs", rows.size(),
                rows.stream().map(IncidentSnapshot::getId).distinct().count());
        return rows;
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
        NewIncidentInput draft = new NewIncidentInput("A07 smoke " + runId,
                "Authorized backend smoke test", "DEMO-ONLY", "URGENT");
        IncidentSnapshot created = repoCall(cb -> resident.repo.resident().create(draft, runId, cb));
        if (emulator) {
            // Repeat a committed submission as if its first readback had not reached the form.
            IncidentSnapshot retried = repoCall(cb -> resident.repo.resident().create(draft, runId, cb));
            IncidentSnapshot reconciled = repoCall(cb -> resident.repo.resident().reconcileCreate(runId, cb));
            assertEquals(created.getId(), retried.getId());
            assertEquals(created.getId(), reconciled.getId());
            assertEquals(created.getId(), FirebaseBackendSmokeTest.<IncidentSnapshot>repoCall(
                    cb -> manager.repo.manager().get(runId, cb)).getId());
            FirebaseBackendSmokeTest.<IncidentSnapshot>assertRepoError(
                    IncidentRepository.Error.PERMISSION_DENIED, cb -> other.repo.manager().get(runId, cb));
            assertEquals(1, FirebaseBackendSmokeTest.<List<IncidentHistorySnapshot>>repoCall(
                    cb -> resident.repo.resident().historyMine(runId, cb)).size());
            NewIncidentInput changed = new NewIncidentInput("Different", "Other", "DEMO-ONLY", "URGENT");
            FirebaseBackendSmokeTest.<IncidentSnapshot>assertRepoError(IncidentRepository.Error.CONFLICT,
                    cb -> resident.repo.resident().create(changed, runId, cb));
        }
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
                created.getId(), expected(completed), false, emulator ? "Local QA note" : null, cb));
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
        assertEquals(emulator ? "Local QA note" : null, history.get(4).getNote());
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
