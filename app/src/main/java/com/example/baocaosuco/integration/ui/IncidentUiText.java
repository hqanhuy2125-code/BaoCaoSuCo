package com.example.baocaosuco.integration.ui;

import android.view.View;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;
import androidx.core.content.ContextCompat;
import com.example.baocaosuco.R;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.example.baocaosuco.data.repository.IncidentRepository;
import com.example.baocaosuco.logic.IncidentStatus;

import java.text.DateFormat;
import java.util.Date;

public final class IncidentUiText {
    private IncidentUiText() {}

    public static String status(IncidentStatus value) {
        switch (value) {
            case NEW: return "Mới";
            case ASSIGNED: return "Đã phân công";
            case IN_PROGRESS: return "Đang xử lý";
            case COMPLETED: return "Hoàn tất";
            case CLOSED: return "Đã đóng";
            default: throw new AssertionError(value);
        }
    }

    public static void applyStatusBadge(TextView view, IncidentStatus status) {
        switch (status) {
            case NEW: applyBadge(view, R.color.f7_new_fg, R.color.f7_new_bg); break;
            case ASSIGNED: applyBadge(view, R.color.f7_assigned_fg, R.color.f7_assigned_bg); break;
            case IN_PROGRESS: applyBadge(view, R.color.f7_progress_fg, R.color.f7_progress_bg); break;
            case COMPLETED: applyBadge(view, R.color.f7_completed_fg, R.color.f7_completed_bg); break;
            case CLOSED: applyBadge(view, R.color.f7_closed_fg, R.color.f7_closed_bg); break;
        }
    }

    public static void applyPriorityBadge(TextView view, String priority) {
        switch (priority) {
            case "URGENT": applyBadge(view, R.color.f7_new_fg, R.color.f7_new_bg); break;
            case "LOW": applyBadge(view, R.color.f7_assigned_fg, R.color.f7_assigned_bg); break;
            default: applyBadge(view, R.color.f7_progress_fg, R.color.f7_progress_bg);
        }
    }

    private static void applyBadge(TextView view, int foreground, int background) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(20 * view.getResources().getDisplayMetrics().density);
        shape.setColor(ContextCompat.getColor(view.getContext(), background));
        view.setBackground(shape);
        view.setTextColor(ContextCompat.getColor(view.getContext(), foreground));
    }

    public static String priority(String code) {
        switch (code) {
            case "URGENT": return "Khẩn cấp";
            case "LOW": return "Thấp";
            default: return "Thường";
        }
    }

    public static String time(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(millis));
    }

    public static String error(IncidentRepository.Error error) {
        switch (error) {
            case NETWORK: return "Lỗi mạng. Kiểm tra kết nối rồi tải lại.";
            case CONFLICT: return "Sự cố đã thay đổi. Tải lại trước khi tiếp tục.";
            case VALIDATION: return "Kiểm tra các trường bắt buộc và độ dài nội dung.";
            case PERMISSION_DENIED: return "Bạn không có quyền truy cập sự cố này.";
            case AUTH_REQUIRED: return "Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.";
            case NOT_FOUND: return "Không tìm thấy sự cố trong danh sách đã tải.";
            default: return "Không thể tải hoặc lưu dữ liệu. Vui lòng thử lại.";
        }
    }

    public static void applyInsets(AppCompatActivity activity, View root) {
        boolean light = (activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        Runnable setStatusBar = () -> WindowCompat.getInsetsController(activity.getWindow(), root)
                .setAppearanceLightStatusBars(light);
        root.post(setStatusBar);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            setStatusBar.run();
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
