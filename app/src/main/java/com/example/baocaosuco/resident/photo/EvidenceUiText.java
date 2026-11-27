package com.example.baocaosuco.resident.photo;

public final class EvidenceUiText {
    private EvidenceUiText() {}

    public static String error(SupabaseEvidenceClient.Error error) {
        switch (error) {
            case NOT_CONFIGURED: return "Ảnh chia sẻ chưa được cấu hình trong bản này.";
            case CONFIG_MISMATCH: return "Cấu hình ảnh và Firebase không khớp.";
            case GATE_DISABLED: return "Cổng chia sẻ ảnh đang khóa.";
            case AUTH_REQUIRED: return "Phiên đăng nhập không còn hợp lệ.";
            case PERMISSION_DENIED: return "Bạn không có quyền truy cập ảnh này.";
            case NOT_FOUND: return "Không tìm thấy ảnh.";
            case CONFLICT: return "Đã có ảnh cho báo cáo này. Mở chi tiết để kiểm tra ảnh đã lưu.";
            case VALIDATION: return "Ảnh hoặc thông tin ảnh không hợp lệ.";
            case TOO_LARGE: return "Ảnh vượt giới hạn 2 MiB.";
            case UNCONFIRMED: return "Chưa xác nhận ảnh đã lưu. Kiểm tra chi tiết hoặc thử lại.";
            case NETWORK: return "Không kết nối được dịch vụ ảnh.";
            default: return "Dịch vụ ảnh tạm thời chưa phản hồi.";
        }
    }

    public static boolean canRetry(SupabaseEvidenceClient.Error error) {
        return error == SupabaseEvidenceClient.Error.NETWORK
                || error == SupabaseEvidenceClient.Error.SERVER_ERROR
                || error == SupabaseEvidenceClient.Error.UNCONFIRMED
                || error == SupabaseEvidenceClient.Error.GATE_DISABLED;
    }
}
