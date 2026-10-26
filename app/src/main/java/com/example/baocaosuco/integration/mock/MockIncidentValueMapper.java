package com.example.baocaosuco.integration.mock;

import com.example.baocaosuco.logic.IncidentStatus;
import com.example.baocaosuco.logic.IncidentStatusCodec;

/**
 * Converts explicit, synthetic demo labels into canonical LOG values.
 * This is NOT the Firestore serializer or a security/ownership check.
 * Production persistence MUST accept canonical codes only.
 */
public final class MockIncidentValueMapper {
    private MockIncidentValueMapper() { }

    public static IncidentStatus previewStatus(String text) {
        IncidentStatus canonical = IncidentStatusCodec.parseCanonical(text);
        if (canonical != null) return canonical;
        if (text == null) return null;
        switch (text) {
            case "Mới":
            case "Moi":
                return IncidentStatus.NEW;
            case "Đã giao":
            case "Da giao":
                return IncidentStatus.ASSIGNED;
            case "Đang xử lý":
            case "Dang xu ly":
                return IncidentStatus.IN_PROGRESS;
            case "Hoàn tất":
            case "Hoan tat":
                return IncidentStatus.COMPLETED;
            case "Đã đóng":
            case "Da dong":
                return IncidentStatus.CLOSED;
            default:
                return null;
        }
    }

    public static String displayStatus(IncidentStatus status) {
        if (status == null) throw new IllegalArgumentException("Status required");
        switch (status) {
            case NEW: return "Mới";
            case ASSIGNED: return "Đã giao";
            case IN_PROGRESS: return "Đang xử lý";
            case COMPLETED: return "Hoàn tất";
            case CLOSED: return "Đã đóng";
            default: throw new IllegalArgumentException("Unknown status");
        }
    }

    public static String previewPriority(String label) {
        if (label == null) return null;
        switch (label) {
            case "URGENT":
            case "Khẩn":
            case "Khan":
                return "URGENT";
            case "NORMAL":
            case "Thường":
            case "Thuong":
                return "NORMAL";
            case "LOW":
            case "Thấp":
            case "Thap":
                return "LOW";
            default:
                return null;
        }
    }
}
