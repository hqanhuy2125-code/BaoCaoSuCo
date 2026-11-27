package com.example.baocaosuco.integration.ui;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ContentValues;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.content.FileProvider;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.*;
import android.view.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.baocaosuco.R;
import com.example.baocaosuco.auth.*;
import com.example.baocaosuco.resident.ResidentMainActivity;
import com.example.baocaosuco.resident.ResidentDetailActivity;
import com.example.baocaosuco.manager.ManagerDetailActivity;
import com.example.baocaosuco.manager.ManagerMainActivity;
import com.example.baocaosuco.data.incident.*;
import com.example.baocaosuco.data.repository.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.Tasks;
import org.junit.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.action.ViewActions.*;

/** Explicit live UI acceptance. Credentials are private device files only. */
public class B07LiveUiTest {
    private final android.app.Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Properties config = new Properties();
    private Activity activity;
    private final String title = "B07 QA " + UUID.randomUUID().toString().substring(0,8);

    @Before public void setup() throws Exception {
        assumeTrue("Live test is opt-in", "yes".equals(InstrumentationRegistry.getArguments().getString("b07Live")));
        assertEquals("baocaosuco-c4ccc", FirebaseApp.getInstance().getOptions().getProjectId());
        try (InputStream input = new FileInputStream(new File(inst.getTargetContext().getFilesDir(),"backend-smoke.properties"))) {
            config.load(input);
        }
        assertEquals("baocaosuco-c4ccc", config.getProperty("projectId"));
        assertEquals("yes", config.getProperty("allowLiveTestWrites"));
    }

    @Test public void f7ReadOnly() throws Exception {
        BundleArgs args = new BundleArgs();
        assumeTrue("Explicit read-only UI audit", args.enabled);
        main(() -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                args.dark ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                        : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO));
        login("resident", ResidentMainActivity.class);
        Configuration config = activity.getResources().getConfiguration();
        assertEquals(320, config.screenWidthDp);
        assertEquals(512, config.screenHeightDp);
        assertEquals(Float.parseFloat(args.scale), config.fontScale, 0.01f);
        assertEquals(args.dark, (config.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
        openForm();
        main(() -> {
            ((EditText) activity.findViewById(R.id.et_incident_title)).setText("Bản nháp QA");
            ((EditText) activity.findViewById(R.id.et_incident_location)).setText("QA-ONLY");
        });
        focusForScreenshot(R.id.et_incident_title);
        screenshot(args.prefix + "-01-input");
        focusForScreenshot(R.id.btn_submit_incident);
        if (args.after) checkWholeLabel(R.id.btn_submit_incident);
        screenshot(args.prefix + "-02-submit");
        main(() -> activity.findViewById(R.id.nav_item_home).performClick());
        await(() -> activity.findViewById(R.id.et_search) != null, "Resident home");
        main(() -> ((EditText) activity.findViewById(R.id.et_search)).setText("F7 no match QA"));
        await(() -> activity.findViewById(R.id.layout_empty).isShown(), "Resident empty");
        focusForScreenshot(R.id.btn_empty_create);
        if (args.after) checkWholeLabel(R.id.btn_empty_create);
        screenshot(args.prefix + "-05-resident-empty");
        openResident("B07 QA e6172611");
        awaitStatus(R.id.tv_detail_status,"Đã đóng");
        screenshot(args.prefix + "-06-closed");
        if (args.scale.equals("1.5")) {
            await(() -> {
                RecyclerView timeline = activity.findViewById(R.id.rv_timeline_events);
                return timeline.getAdapter() != null && timeline.getAdapter().getItemCount() == 7;
            }, "Seven real timeline events");
            for (int index = 0; index < 7; index++) {
                final int event = index;
                await(() -> ((RecyclerView) activity.findViewById(R.id.rv_timeline_events))
                        .findViewHolderForAdapterPosition(event) != null, "Timeline row " + index);
                main(() -> scrollToView(((RecyclerView) activity.findViewById(R.id.rv_timeline_events))
                        .findViewHolderForAdapterPosition(event).itemView));
                if (args.after) main(() -> {
                    View row = ((RecyclerView) activity.findViewById(R.id.rv_timeline_events))
                            .findViewHolderForAdapterPosition(event).itemView;
                    assertWholeLabel(row.findViewById(R.id.tv_timeline_action));
                    assertWholeLabel(row.findViewById(R.id.tv_timeline_status));
                });
                screenshot(args.prefix + "-qa01-event-" + (event + 1));
            }
        }
        login("manager", ManagerMainActivity.class);
        main(() -> ((EditText) activity.findViewById(R.id.et_manager_search)).setText("F7 no match QA"));
        await(() -> activity.findViewById(R.id.layout_manager_empty).isShown(), "Manager empty");
        screenshot(args.prefix + "-05-manager-empty");
        main(() -> ((EditText) activity.findViewById(R.id.et_manager_search)).setText("G2 QA 5cd7e623"));
        await(() -> {
            RecyclerView list = activity.findViewById(R.id.rv_manager_incidents);
            return list.isShown() && list.getChildCount() > 0 && hasText(list.getChildAt(0),"G2 QA 5cd7e623");
        }, "Existing NEW QA record");
        screenshot(args.prefix + "-01-manager-list");
        if (args.after) assertManagerSort(args.prefix);
        main(() -> ((RecyclerView) activity.findViewById(R.id.rv_manager_incidents)).getChildAt(0).performClick());
        resumed(ManagerDetailActivity.class);
        awaitStatus(R.id.tv_mgr_detail_status, "Mới");
        screenshot(args.prefix + "-06-new");
        focusForScreenshot(R.id.btn_mgr_assign);
        main(() -> activity.findViewById(R.id.btn_mgr_assign).performClick());
        onView(withHint("Tên người xử lý")).inRoot(isDialog()).perform(replaceText("Người xử lý QA"),
                androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        screenshot(args.prefix + "-04-assign-dialog");
        onView(withText("Hủy")).inRoot(isDialog()).perform(click());
        android.util.Log.i("F7QA", "PASS " + args.prefix + " read-only");
    }

    private void assertManagerSort(String prefix) throws Exception {
        // F7 adds a control for the order of the loaded page.
        int sort = activity.getResources().getIdentifier("f7_manager_sort", "id", activity.getPackageName());
        assertTrue(sort != 0);
        focusForScreenshot(sort);
        main(() -> activity.findViewById(sort).performClick());
        screenshot(prefix + "-func02-sort");
        onView(withText("Hạn gần nhất")).inRoot(isDialog()).perform(click());
    }

    private void focusForScreenshot(int id) throws Exception {
        await(() -> activity.findViewById(id) != null, "View " + id);
        main(() -> scrollToView(activity.findViewById(id)));
        inst.waitForIdleSync();
    }

    private static void scrollToView(View view) {
        view.requestRectangleOnScreen(new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()), true);
    }

    private void checkWholeLabel(int id) {
        main(() -> assertWholeLabel(activity.findViewById(id)));
    }

    private static void assertWholeLabel(TextView view) {
        android.text.Layout text = view.getLayout();
        int last = text.getLineCount() - 1;
        assertTrue(last >= 0);
        for (int line = 0; line <= last; line++) assertEquals(0, text.getEllipsisCount(line));
        assertEquals(text.getText().length(), text.getLineEnd(last));
        int top = view.getTotalPaddingTop();
        android.graphics.Rect visible = new android.graphics.Rect();
        assertTrue(view.getLocalVisibleRect(visible));
        assertTrue("Rendered label lines must fit inside the visible view", visible.top <= top
                && top + text.getLineBottom(last) <= visible.bottom);
    }

    private static final class BundleArgs {
        final android.os.Bundle bundle = InstrumentationRegistry.getArguments();
        final boolean enabled = "yes".equals(bundle.getString("f7ReadOnly"));
        final boolean after = "after".equals(bundle.getString("f7Phase"));
        final boolean dark = "dark".equals(bundle.getString("f7Theme"));
        final String scale = bundle.getString("f7Scale","1.5");
        final String prefix = "f7-" + bundle.getString("f7Phase","before") + "-"
                + bundle.getString("f7Theme","light") + "-" + scale.replace('.', '_');
    }

    @Test public void final01EvidenceReadOnly() throws Exception {
        assumeTrue("Explicit FINAL-01 read-only QA opt-in",
                "yes".equals(InstrumentationRegistry.getArguments().getString("final01ReadOnly")));
        login("resident", ResidentMainActivity.class);
        openResident("B07 QA e6172611");
        await(() -> hasText(activity.getWindow().getDecorView(), "Ảnh chia sẻ chưa được cấu hình"),
                "Resident evidence disabled by build configuration");
        focusForScreenshot(R.id.incident_evidence_panel);
        screenshot("final01-01-resident-evidence");
        main(() -> com.google.firebase.auth.FirebaseAuth.getInstance().signOut());
        await(() -> !activity.findViewById(R.id.incident_evidence_panel).isShown(),
                "Old evidence panel hidden after sign-out");

        login("manager", ManagerMainActivity.class);
        main(() -> ((EditText) activity.findViewById(R.id.et_manager_search)).setText("B07 QA e6172611"));
        await(() -> {
            RecyclerView list = activity.findViewById(R.id.rv_manager_incidents);
            return list.isShown() && list.getChildCount() > 0 && hasText(list.getChildAt(0), "B07 QA e6172611");
        }, "Existing QA incident");
        main(() -> ((RecyclerView) activity.findViewById(R.id.rv_manager_incidents)).getChildAt(0).performClick());
        resumed(ManagerDetailActivity.class);
        await(() -> hasText(activity.getWindow().getDecorView(), "Ảnh chia sẻ chưa được cấu hình"),
                "Manager evidence disabled by build configuration");
        focusForScreenshot(R.id.incident_evidence_panel);
        screenshot("final01-02-manager-evidence");

        login("resident", ResidentMainActivity.class);
        FirebaseIncidentRepository repository = new FirebaseIncidentRepository();
        List<IncidentSnapshot> incidents = repo(cb -> repository.resident().listMine(cb));
        IncidentSnapshot existing = incidents.stream().filter(i -> "B07 QA e6172611".equals(i.getTitle()))
                .findFirst().orElseThrow(() -> new AssertionError("Existing QA fixture not found"));
        openForm();
        Uri media = insertGalleryPhoto();
        try {
            main(() -> activity.findViewById(R.id.photo_gallery).performClick());
            pickGeneratedImage();
            clickExternal("Photo picker Done", "done", "add");
            resumed(ResidentMainActivity.class);
            await(this::previewShown, "Actual prepared JPEG");
            // Exercise the post-persistence callback with an existing read-only QA fixture.
            // No click on the initial submit action and no new Firestore write.
            main(() -> {
                androidx.fragment.app.Fragment host = ((ResidentMainActivity) activity)
                        .getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                ((com.example.baocaosuco.resident.CreateIncidentFragment) host).onSaved(existing);
            });
            await(() -> hasText(activity.getWindow().getDecorView(), "Báo cáo đã lưu. Ảnh chia sẻ chưa được cấu hình"),
                    "Persisted report is distinct from unavailable evidence");
            main(() -> assertFalse(activity.findViewById(R.id.btn_submit_incident).isEnabled()));
            focusForScreenshot(R.id.incident_form_message);
            screenshot("final01-03-report-saved-photo-unavailable");
            Activity original = activity;
            main(original::recreate);
            resumedNew(ResidentMainActivity.class, original);
            await(() -> hasText(activity.getWindow().getDecorView(), "Báo cáo đã lưu. Ảnh chia sẻ chưa được cấu hình"),
                    "Saved incident state restored without enabling re-create");
            main(() -> assertFalse(activity.findViewById(R.id.btn_submit_incident).isEnabled()));
        } finally {
            inst.getTargetContext().getContentResolver().delete(media, null, null);
        }
    }

    @Test public void liveUiJourney() throws Exception {
        login("manager", ManagerMainActivity.class);
        login("otherResident", ResidentMainActivity.class);
        login("resident", ResidentMainActivity.class);
        await(() -> activity.findViewById(R.id.et_search) != null, "Resident list");

        openForm();
        await(() -> activity.findViewById(R.id.et_incident_title) != null, "Create form");
        main(() -> {
            ((EditText)activity.findViewById(R.id.et_incident_title)).setText(title);
            ((EditText)activity.findViewById(R.id.et_incident_desc)).setText("Authorized B07 live UI QA");
            ((EditText)activity.findViewById(R.id.et_incident_location)).setText("QA-ONLY");
            activity.findViewById(R.id.rb_priority_normal).performClick();
            activity.findViewById(R.id.btn_submit_incident).performClick();
        });
        openResident(title);
        awaitStatus(R.id.tv_detail_status, "Mới");
        screenshot("02-created");
        FirebaseIncidentRepository repository = new FirebaseIncidentRepository();
        List<IncidentSnapshot> items = repo(cb -> repository.resident().listMine(cb));
        IncidentSnapshot created = items.stream().filter(i -> title.equals(i.getTitle())).findFirst().get();
        String id = created.getId();
        android.util.Log.i("B07QA","CREATED incident="+id+" title="+title);
        assertEquals(0, created.getRevision());
        assertEquals(1, B07LiveUiTest.<List<IncidentHistorySnapshot>>repo(cb -> repository.resident().historyMine(id,cb)).size());

        login("manager", ManagerMainActivity.class);
        await(() -> activity.findViewById(R.id.et_manager_search) != null, "Manager list");
        main(() -> ((EditText)activity.findViewById(R.id.et_manager_search)).setText(title));
        await(() -> ((RecyclerView)activity.findViewById(R.id.rv_manager_incidents)).getChildCount() > 0, "Manager row");
        main(() -> ((RecyclerView)activity.findViewById(R.id.rv_manager_incidents)).getChildAt(0).performClick());
        resumed(ManagerDetailActivity.class);
        awaitStatus(R.id.tv_mgr_detail_status,"Mới");
        screenshot("03-manager-new");
        main(() -> activity.findViewById(R.id.btn_mgr_assign).performClick());
        screenshot("03b-assign-dialog");
        onView(withHint("Tên người xử lý")).inRoot(isDialog()).perform(replaceText("B07 QA Handler"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        onView(withText("Giao việc")).inRoot(isDialog()).perform(click());
        awaitStatus(R.id.tv_mgr_detail_status,"Đã phân công");
        screenshot("04-assigned");
        main(() -> {
            View start=activity.findViewById(R.id.btn_mgr_start_processing);
            start.performClick(); start.performClick();
        });
        awaitStatus(R.id.tv_mgr_detail_status,"Đang xử lý");
        main(() -> activity.findViewById(R.id.btn_mgr_complete).performClick());
        awaitStatus(R.id.tv_mgr_detail_status,"Hoàn tất");
        screenshot("05-completed");

        login("resident", ResidentMainActivity.class);
        openResident(title);
        awaitStatus(R.id.tv_detail_status,"Hoàn tất");
        main(() -> activity.findViewById(R.id.btn_confirm_not_done).performClick());
        awaitStatus(R.id.tv_detail_status,"Đang xử lý");
        screenshot("06-reopened");

        login("manager", ManagerMainActivity.class);
        await(() -> activity.findViewById(R.id.et_manager_search) != null,"Manager return");
        main(() -> ((EditText)activity.findViewById(R.id.et_manager_search)).setText(title));
        await(() -> ((RecyclerView)activity.findViewById(R.id.rv_manager_incidents)).getChildCount() > 0,"Manager return row");
        main(() -> ((RecyclerView)activity.findViewById(R.id.rv_manager_incidents)).getChildAt(0).performClick());
        resumed(ManagerDetailActivity.class);
        awaitStatus(R.id.tv_mgr_detail_status,"Đang xử lý");
        main(() -> activity.findViewById(R.id.btn_mgr_complete).performClick());
        awaitStatus(R.id.tv_mgr_detail_status,"Hoàn tất");

        login("resident", ResidentMainActivity.class);
        openResident(title);
        awaitStatus(R.id.tv_detail_status,"Hoàn tất");
        main(() -> activity.findViewById(R.id.btn_confirm_done).performClick());
        awaitStatus(R.id.tv_detail_status,"Đã đóng");
        screenshot("07-closed");
        main(() -> {
            RecyclerView timeline=activity.findViewById(R.id.rv_timeline_events);
            assertEquals(7,timeline.getAdapter().getItemCount());
            View ancestor=timeline;
            while(ancestor!=null && !(ancestor instanceof ScrollView)) {
                ancestor=ancestor.getParent() instanceof View ? (View)ancestor.getParent() : null;
            }
            assertNotNull(ancestor);
            ((ScrollView)ancestor).fullScroll(View.FOCUS_DOWN);
        });
        screenshot("07b-history-seven");
        IncidentSnapshot closed = repo(cb -> repository.resident().getMine(id,cb));
        List<IncidentHistorySnapshot> history = repo(cb -> repository.resident().historyMine(id,cb));
        assertEquals("CLOSED",closed.getStatusCode());
        assertEquals(6,closed.getRevision());
        assertEquals(7,history.size());
        for (int i=0;i<history.size();i++) assertEquals(i,history.get(i).getRevision());
        main(() -> activity.findViewById(R.id.btn_back).performClick());
        resumed(ResidentMainActivity.class);
        await(() -> activity.findViewById(R.id.et_search)!=null,"Back list");
        main(() -> ((EditText)activity.findViewById(R.id.et_search)).setText("empty-"+UUID.randomUUID()));
        await(() -> activity.findViewById(R.id.layout_empty).getVisibility()==View.VISIBLE,"Empty state");
        screenshot("08-empty");
        login("otherResident",ResidentMainActivity.class);
        CountDownLatch denied = new CountDownLatch(1);
        AtomicReference<IncidentRepository.Error> failure = new AtomicReference<>();
        repository.resident().getMine(id,new IncidentRepository.Callback<IncidentSnapshot>(){
            public void onSuccess(IncidentSnapshot value){denied.countDown();}
            public void onError(IncidentRepository.Error error){failure.set(error);denied.countDown();}
        });
        assertTrue(denied.await(40,TimeUnit.SECONDS));
        assertEquals(IncidentRepository.Error.PERMISSION_DENIED,failure.get());
        List<IncidentSnapshot> otherItems=repo(cb -> repository.resident().listMine(cb));
        assertFalse(otherItems.stream().anyMatch(i -> id.equals(i.getId())));
        B07LiveUiTest.<List<IncidentHistorySnapshot>>expectError(IncidentRepository.Error.PERMISSION_DENIED, cb -> repository.resident().historyMine(id,cb));
        android.util.Log.i("B07QA","PASS incident="+id+" revision=6 history=7 UI journey and cross-owner denied");
    }

    @Test public void liveNetworkAndConflict() throws Exception {
        login("manager",ManagerMainActivity.class);
        login("resident",ResidentMainActivity.class);
        await(() -> activity.findViewById(R.id.et_search)!=null,"Resident list");
        FirebaseFirestore db=FirebaseFirestore.getInstance();
        Tasks.await(db.disableNetwork(),20,TimeUnit.SECONDS);
        try {
            Activity old = activity;
            main(old::recreate);
            resumedNew(ResidentMainActivity.class, old);
            await(() -> hasText(activity.getWindow().getDecorView(),"Lỗi mạng"),"Network feedback");
            screenshot("09-network-error");
        } finally { Tasks.await(db.enableNetwork(),20,TimeUnit.SECONDS); }
        main(() -> activity.findViewById(R.id.btn_retry).performClick());
        await(() -> activity.findViewById(R.id.layout_loading).getVisibility()!=View.VISIBLE
                && activity.findViewById(R.id.layout_error).getVisibility()!=View.VISIBLE,"Network retry");
        openForm();
        main(() -> activity.findViewById(R.id.btn_submit_incident).performClick());
        await(() -> hasText(activity.getWindow().getDecorView(),"Kiểm tra các trường"),"Validation");
        screenshot("10-validation");
        String conflictTitle=title+" conflict";
        main(() -> {
            ((EditText)activity.findViewById(R.id.et_incident_title)).setText(conflictTitle);
            ((EditText)activity.findViewById(R.id.et_incident_desc)).setText("Authorized B07 concurrency QA");
            ((EditText)activity.findViewById(R.id.et_incident_location)).setText("QA-ONLY");
            View submit=activity.findViewById(R.id.btn_submit_incident);
            submit.performClick(); submit.performClick();
        });
        openResident(conflictTitle);
        awaitStatus(R.id.tv_detail_status,"Mới");
        FirebaseIncidentRepository repository=new FirebaseIncidentRepository();
        List<IncidentSnapshot> items=repo(cb -> repository.resident().listMine(cb));
        assertEquals(1,items.stream().filter(i -> conflictTitle.equals(i.getTitle())).count());
        IncidentSnapshot created=items.stream().filter(i -> conflictTitle.equals(i.getTitle())).findFirst().get();
        String id=created.getId();
        android.util.Log.i("B07QA","CREATED incident="+id+" title="+conflictTitle);
        login("manager",ManagerMainActivity.class);
        await(() -> activity.findViewById(R.id.et_manager_search)!=null,"Manager list");
        main(() -> ((EditText)activity.findViewById(R.id.et_manager_search)).setText(conflictTitle));
        await(() -> ((RecyclerView)activity.findViewById(R.id.rv_manager_incidents)).getChildCount()>0,"Conflict row");
        main(() -> ((RecyclerView)activity.findViewById(R.id.rv_manager_incidents)).getChildAt(0).performClick());
        resumed(ManagerDetailActivity.class);
        awaitStatus(R.id.tv_mgr_detail_status,"Mới");
        // A second authorized client advances the real QA record while UI retains revision 0.
        CommandPrecondition expected=new CommandPrecondition("NEW",0,UUID.randomUUID().toString());
        IncidentSnapshot assigned=repo(cb -> repository.manager().assign(id,expected,"B07 concurrent QA",cb));
        assertEquals(1,assigned.getRevision());
        B07LiveUiTest.<IncidentSnapshot>expectError(IncidentRepository.Error.CONFLICT,cb -> repository.manager().assign(id,expected,"B07 concurrent QA",cb));
        main(() -> activity.findViewById(R.id.btn_mgr_assign).performClick());
        onView(withHint("Tên người xử lý")).inRoot(isDialog()).perform(replaceText("B07 stale QA"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        onView(withText("Giao việc")).inRoot(isDialog()).perform(click());
        await(() -> hasText(activity.getWindow().getDecorView(),"Sự cố đã thay đổi"),"Revision conflict feedback");
        screenshot("11-revision-conflict");
        clickText("Tải lại");
        awaitStatus(R.id.tv_mgr_detail_status,"Đã phân công");
        screenshot("12-conflict-reloaded");
        main(() -> activity.findViewById(R.id.btn_manager_back).performClick());
        resumed(ManagerMainActivity.class);
        await(() -> activity.findViewById(R.id.et_manager_search)!=null,"Manager back");
        main(() -> activity.findViewById(R.id.nav_item_saved).performClick());
        await(() -> ((com.google.android.material.chip.Chip) activity.findViewById(R.id.chip_mgr_overdue))
                .isChecked(),"Overdue navigation");
        main(() -> activity.findViewById(R.id.nav_item_home).performClick());
        await(() -> ((com.google.android.material.chip.Chip) activity.findViewById(R.id.chip_mgr_all))
                .isChecked(),"Bottom home");
        login("resident",ResidentMainActivity.class);
        openResident(conflictTitle);
        awaitStatus(R.id.tv_detail_status,"Đã phân công");
        IncidentSnapshot current=repo(cb -> repository.resident().getMine(id,cb));
        List<IncidentHistorySnapshot> history=repo(cb -> repository.resident().historyMine(id,cb));
        assertEquals(1,current.getRevision());
        assertEquals(2,history.size());
        android.util.Log.i("B07QA","PASS network retry validation duplicate UI submit/start revision conflict incident="+id+" revision=1 history=2");
    }

    @Test public void localPhotoAndRecreation() throws Exception {
        login("resident", ResidentMainActivity.class);
        openForm();
        main(() -> ((EditText) activity.findViewById(R.id.et_incident_title)).setText("B07 local draft"));
        screenshot("13-create-form");
        Uri media = insertGalleryPhoto();
        try {
            main(() -> activity.findViewById(R.id.photo_gallery).performClick());
            pickGeneratedImage();
            clickExternal("Photo picker Done", "done", "add");
            resumed(ResidentMainActivity.class);
            await(this::previewShown, "Gallery preview");
            screenshot("14-gallery-preview");
            android.util.Log.i("B07QA", "PASS gallery picker and preview");
            main(() -> activity.findViewById(R.id.photo_gallery).performClick());
            awaitExternal();
            assertTrue(inst.getUiAutomation().performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
            await(() -> activity.findViewById(R.id.photo_gallery).isEnabled() && previewShown(),
                    "Gallery cancel keeps preview");
            android.util.Log.i("B07QA", "PASS gallery cancel preserves preview");
            verifyInvalidPhotoPreservesPreview();
            Activity portrait = activity;
            main(() -> portrait.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            resumedNew(ResidentMainActivity.class, portrait);
            await(() -> activity.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE && previewShown(), "Rotated preview");
            assertDraft();
            screenshot("15-photo-landscape");
            Activity landscape = activity;
            main(() -> landscape.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            resumedNew(ResidentMainActivity.class, landscape);
            await(this::previewShown, "Restored portrait preview");
            assertDraft();
            android.util.Log.i("B07QA", "PASS rotation retains draft and preview");
            await(() -> activity.findViewById(R.id.btn_remove_photo).isEnabled(), "Verified role after rotation");
            main(() -> activity.findViewById(R.id.btn_remove_photo).performClick());
            await(() -> !previewShown(), "Remove preview");
            android.util.Log.i("B07QA", "PASS photo removal");
            main(() -> activity.findViewById(R.id.photo_capture).performClick());
            clickExternal("Camera shutter", "take photo", "take picture", "shutter", "capture");
            clickExternal("Camera confirmation", "done", "use photo", "ok", "confirm");
            await(this::previewShown, "Camera preview");
            screenshot("16-camera-preview");
            android.util.Log.i("B07QA", "PASS camera shutter, return and preview");
            main(() -> activity.findViewById(R.id.photo_capture).performClick());
            awaitExternal();
            assertTrue(inst.getUiAutomation().performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
            await(() -> activity.findViewById(R.id.photo_capture).isEnabled() && previewShown(),
                    "Camera cancel preserves preview");
            android.util.Log.i("B07QA", "PASS camera cancel preserves preview");
            assertTrue(inst.getUiAutomation().performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
            await(() -> activity.findViewById(R.id.et_search) != null, "Back to Resident home");
            openForm();
            main(() -> {
                assertFalse(previewShown());
                assertEquals(0, ((EditText) activity.findViewById(R.id.et_incident_title)).length());
            });
            main(() -> activity.findViewById(R.id.nav_item_home).performClick());
            await(() -> activity.findViewById(R.id.et_search) != null, "Home after revisiting form");
            Activity light = activity;
            main(() -> activity.findViewById(R.id.theme_toggle_button).performClick());
            resumedNew(ResidentMainActivity.class, light);
            await(() -> activity.findViewById(R.id.et_search) != null, "Home after theme change");
            await(() -> ((TextView) activity.findViewById(R.id.tv_header_resident_name)).length() > 0, "Role after theme change");
            openForm();
            await(() -> (activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
                    "Dark mode");
            screenshot("17-create-dark");
            android.util.Log.i("B07QA", "PASS form Back, revisit, and theme recreation");
        } finally {
            inst.getTargetContext().getContentResolver().delete(media, null, null);
            main(() -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        }
    }

    @Test public void compactLayout() throws Exception {
        login("resident", ResidentMainActivity.class);
        main(() -> ((EditText) activity.findViewById(R.id.et_search)).setText("B07 QA"));
        await(() -> {
            RecyclerView list = activity.findViewById(R.id.rv_incidents);
            android.graphics.Rect visible = new android.graphics.Rect();
            return list.getChildCount() > 0 && list.getChildAt(0).getGlobalVisibleRect(visible)
                    && visible.height() >= 24;
        }, "Visible B07 row on compact list");
        main(() -> {
            View label = activity.findViewById(R.id.tv_nav_home);
            android.graphics.Rect visible = new android.graphics.Rect();
            assertTrue(label.getGlobalVisibleRect(visible));
            assertEquals("Bottom label is clipped", label.getHeight(), visible.height());
        });
        screenshot("18-compact-list");
        openForm();
        await(() -> activity.findViewById(R.id.et_incident_title).getGlobalVisibleRect(
                new android.graphics.Rect()), "Visible form field on compact display");
        main(() -> {
            View submit = activity.findViewById(R.id.btn_submit_incident);
            submit.requestRectangleOnScreen(new android.graphics.Rect(
                    0, 0, submit.getWidth(), submit.getHeight()), true);
        });
        await(() -> activity.findViewById(R.id.btn_submit_incident).getGlobalVisibleRect(
                new android.graphics.Rect()), "Reach submit by scrolling compact form");
        screenshot("19-compact-form");
        Configuration config = activity.getResources().getConfiguration();
        login("manager", ManagerMainActivity.class);
        main(() -> ((EditText) activity.findViewById(R.id.et_manager_search)).setText("B07 QA"));
        await(() -> activity.findViewById(R.id.rv_manager_incidents).isShown(), "Compact Manager list");
        main(() -> {
            for (int id : new int[]{R.id.tv_nav_home, R.id.tv_nav_saved, R.id.tv_nav_profile}) {
                View label = activity.findViewById(id);
                android.graphics.Rect visible = new android.graphics.Rect();
                assertTrue(label.getGlobalVisibleRect(visible));
                assertEquals("Manager bottom label is clipped", label.getHeight(), visible.height());
            }
        });
        screenshot("20-compact-manager");
        android.util.Log.i("B07QA", "PASS compact list and scrollable form "
                + config.screenWidthDp + "x" + config.screenHeightDp + "dp fontScale=" + config.fontScale);
    }

    private Uri insertGalleryPhoto() throws Exception {
        ContentValues fields = new ContentValues();
        fields.put(MediaStore.Images.Media.DISPLAY_NAME, "B07-QA-" + UUID.randomUUID() + ".png");
        fields.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
        fields.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/B07-QA");
        android.content.ContentResolver resolver = inst.getTargetContext().getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, fields);
        assertNotNull(uri);
        Bitmap generated = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        generated.eraseColor(android.graphics.Color.rgb(32, 116, 165));
        try (OutputStream data = resolver.openOutputStream(uri)) {
            assertTrue(generated.compress(Bitmap.CompressFormat.PNG, 100, data));
        } finally {
            generated.recycle();
        }
        return uri;
    }

    private void verifyInvalidPhotoPreservesPreview() throws Exception {
        File folder = new File(inst.getTargetContext().getCacheDir(), "incident_camera");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        File invalid = File.createTempFile("b07-invalid-", ".jpg", folder);
        try (OutputStream os = new FileOutputStream(invalid)) { os.write(new byte[]{1, 2, 3}); }
        Uri file = FileProvider.getUriForFile(inst.getTargetContext(),
                "com.example.baocaosuco.incidentphoto.fileprovider", invalid);
        Intent result = new Intent().setData(file);
        IntentFilter picker = new IntentFilter(MediaStore.ACTION_PICK_IMAGES);
        picker.addDataType("image/*");
        Instrumentation.ActivityMonitor monitor = inst.addMonitor(
                picker,
                new Instrumentation.ActivityResult(Activity.RESULT_OK, result), true);
        try {
            main(() -> activity.findViewById(R.id.photo_gallery).performClick());
            await(() -> hasText(activity.getWindow().getDecorView(), "Ảnh không hợp lệ")
                    && previewShown(), "Invalid gallery image");
            screenshot("14b-invalid-image");
            android.util.Log.i("B07QA", "PASS invalid image feedback preserves prior preview");
        } finally {
            inst.removeMonitor(monitor);
            invalid.delete();
        }
    }

    private void assertDraft() {
        main(() -> assertEquals("B07 local draft",
                ((EditText) activity.findViewById(R.id.et_incident_title)).getText().toString()));
    }

    private boolean previewShown() {
        android.widget.ImageView image = activity.findViewById(R.id.iv_captured_photo);
        return image != null && image.isShown() && image.getDrawable() != null;
    }

    private void pickGeneratedImage() throws Exception {
        awaitExternal();
        long end = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < end) {
            Bitmap device = inst.getUiAutomation().takeScreenshot();
            if (device != null) {
                try {
                    for (int y = device.getHeight() / 2; y < device.getHeight() - 15; y += 8) {
                        for (int x = 15; x < device.getWidth() - 15; x += 8) {
                            int color = device.getPixel(x, y);
                            if (Math.abs(android.graphics.Color.red(color) - 32) < 8
                                    && Math.abs(android.graphics.Color.green(color) - 116) < 16
                                    && Math.abs(android.graphics.Color.blue(color) - 165) < 18) {
                                long now = android.os.SystemClock.uptimeMillis();
                                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                                MotionEvent up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, x, y, 0);
                                try {
                                    assertTrue(inst.getUiAutomation().injectInputEvent(down, true));
                                    assertTrue(inst.getUiAutomation().injectInputEvent(up, true));
                                } finally {
                                    down.recycle();
                                    up.recycle();
                                }
                                return;
                            }
                        }
                    }
                } finally {
                    device.recycle();
                }
            }
            Thread.sleep(250);
        }
        fail("Generated QA image was not visible in the system picker");
    }

    private void awaitExternal() throws Exception {
        long end = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < end) {
            AccessibilityNodeInfo root = inst.getUiAutomation().getRootInActiveWindow();
            if (root != null && !inst.getTargetContext().getPackageName().contentEquals(root.getPackageName())) return;
            Thread.sleep(100);
        }
        fail("External picker did not open");
    }

    private void clickExternal(String step, String... options) throws Exception {
        long end = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < end) {
            AccessibilityNodeInfo root = inst.getUiAutomation().getRootInActiveWindow();
            if (root != null && !inst.getTargetContext().getPackageName().contentEquals(root.getPackageName())
                    && clickMatching(root, options)) return;
            Thread.sleep(150);
        }
        // Log only resource IDs from the dedicated QA emulator.
        AccessibilityNodeInfo root = inst.getUiAutomation().getRootInActiveWindow();
        if (root != null) tracePicker(root);
        fail("Missing external control: " + step);
    }

    private boolean clickMatching(AccessibilityNodeInfo node, String[] options) {
        String label = (String.valueOf(node.getText()) + " " + node.getContentDescription())
                .toLowerCase(Locale.ROOT).trim();
        for (String option : options) {
            if (label.contains(option)) {
                AccessibilityNodeInfo target = node;
                for (int depth = 0; target != null && depth < 3; depth++, target = target.getParent()) {
                    if (target.isClickable() && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && clickMatching(child, options)) return true;
        }
        return false;
    }

    private void tracePicker(AccessibilityNodeInfo node) {
        if (node.isClickable()) android.util.Log.i("B07QA", "Picker control id="
                + node.getViewIdResourceName());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) tracePicker(child);
        }
    }

    private boolean hasText(View root,String part) {
        if(root instanceof TextView && ((TextView)root).getText().toString().contains(part)) return true;
        if(root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++) if(hasText(group.getChildAt(i),part)) return true;
        }
        return false;
    }

    private static <T> void expectError(IncidentRepository.Error expected,java.util.function.Consumer<IncidentRepository.Callback<T>> operation) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<IncidentRepository.Error> result=new AtomicReference<>();
        operation.accept(new IncidentRepository.Callback<T>() {
            public void onSuccess(T value){latch.countDown();}
            public void onError(IncidentRepository.Error error){result.set(error);latch.countDown();}
        });
        assertTrue(latch.await(45,TimeUnit.SECONDS));
        assertEquals(expected,result.get());
    }

    private void openResident(String query) throws Exception {
        resumed(ResidentMainActivity.class);
        await(() -> activity.findViewById(R.id.et_search)!=null,"Resident return");
        main(() -> ((EditText)activity.findViewById(R.id.et_search)).setText(query));
        await(() -> {
            RecyclerView list = activity.findViewById(R.id.rv_incidents);
            return list.isShown() && list.getChildCount() > 0
                    && hasText(list.getChildAt(0), query);
        }, "Resident row");
        if (query.equals(title)) screenshot("01b-personal-row");
        main(() -> ((RecyclerView)activity.findViewById(R.id.rv_incidents)).getChildAt(0).performClick());
        resumed(ResidentDetailActivity.class);
    }

    private void login(String prefix,Class<?> expected) throws Exception {
        main(() -> AuthRepositories.get(inst.getTargetContext()).signOut());
        Intent intent = new Intent(inst.getTargetContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = inst.startActivitySync(intent);
        main(() -> {
            ((EditText) loginView("et_email", R.id.auth_login_email)).setText(config.getProperty(prefix+"Email"));
            ((EditText) loginView("et_password", R.id.auth_login_password)).setText(config.getProperty(prefix+"Password"));
            loginView("btn_action", R.id.auth_login_btn).performClick();
        });
        resumed(expected);
        if (expected == ResidentMainActivity.class) {
            await(() -> activity.findViewById(R.id.et_search) != null
                    && ((TextView) activity.findViewById(R.id.tv_header_resident_name)).length() > 0,
                    "Verified Resident role");
        } else {
            await(() -> activity.findViewById(R.id.et_manager_search) != null
                    && ((TextView) activity.findViewById(R.id.tv_header_resident_name)).length() > 0,
                    "Verified Manager role");
        }
    }

    private View loginView(String currentName, int legacyId) {
        int currentId = activity.getResources().getIdentifier(currentName, "id", activity.getPackageName());
        View view = currentId == 0 ? null : activity.findViewById(currentId);
        if (view == null) view = activity.findViewById(legacyId);
        assertNotNull("Login control missing: " + currentName, view);
        return view;
    }

    private void openForm() throws Exception {
        main(() -> activity.findViewById(R.id.btn_camera_fab).performClick());
        await(() -> {
            View submit = activity.findViewById(R.id.btn_submit_incident);
            return submit != null && submit.isEnabled();
        }, "Create form");
    }

    private void resumed(Class<?> expected) throws Exception { resumedNew(expected, null); }

    private void resumedNew(Class<?> expected, Activity old) throws Exception {
        await(() -> {
            for(Activity candidate: ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if(expected.isInstance(candidate) && candidate != old) { activity=candidate; return true; }
            }
            return false;
        },"Login role routing");
    }
    private void clickText(String text) { onView(withText(text)).perform(click()); }
    private void main(Runnable operation) { inst.runOnMainSync(operation); }
    private interface Condition { boolean ready(); }
    private void await(Condition condition,String label) throws Exception {
        long until=System.currentTimeMillis()+45000;
        while(System.currentTimeMillis()<until) {
            AtomicBoolean ready=new AtomicBoolean();
            main(() -> ready.set(condition.ready()));
            if(ready.get()) { inst.waitForIdleSync(); return; }
            Thread.sleep(200);
        }
        fail("Timed out: "+label);
    }
    private void awaitStatus(int id,String value) throws Exception {
        await(() -> { TextView v=activity.findViewById(id);return v!=null&&value.contentEquals(v.getText()); },value);
    }
    private void screenshot(String name) throws Exception {
        inst.waitForIdleSync();
        // Wait for two display frames, not just Java view state, before runtime capture.
        // Do not publish profile email addresses in screenshots.
        main(() -> {
            View email = activity.findViewById(R.id.tv_header_resident_email);
            if (email != null) email.setVisibility(View.INVISIBLE);
        });
        CountDownLatch drawn=new CountDownLatch(1);
        main(() -> activity.getWindow().getDecorView().postOnAnimation(() ->
            activity.getWindow().getDecorView().postOnAnimation(drawn::countDown)));
        assertTrue(drawn.await(10,TimeUnit.SECONDS));
        inst.waitForIdleSync();
        // UiAutomation captures the composed surface, which can lag behind the view
        // layout on the emulator immediately after a programmatic scroll.
        if (InstrumentationRegistry.getArguments().getString("f7ReadOnly") != null
                || InstrumentationRegistry.getArguments().getString("final01ReadOnly") != null) {
            inst.getUiAutomation().waitForIdle(300, 3000);
        }
        Bitmap bitmap=inst.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try(OutputStream out = new FileOutputStream(new File(inst.getTargetContext().getFilesDir(),"b07-"+name+".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
        }
        bitmap.recycle();
    }
    private static <T> T repo(java.util.function.Consumer<IncidentRepository.Callback<T>> operation) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<T> result=new AtomicReference<>();
        AtomicReference<IncidentRepository.Error> error=new AtomicReference<>();
        operation.accept(new IncidentRepository.Callback<T>(){
            public void onSuccess(T value){result.set(value);latch.countDown();}
            public void onError(IncidentRepository.Error value){error.set(value);latch.countDown();}
        });
        assertTrue(latch.await(45,TimeUnit.SECONDS));
        assertNull(error.get());
        return result.get();
    }
}
