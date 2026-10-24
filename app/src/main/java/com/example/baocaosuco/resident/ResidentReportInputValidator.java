package com.example.baocaosuco.resident;

import java.util.EnumSet;

/**
 * Kiem tra cac truong bat buoc cua form bao su co truoc khi gui.
 * Khong phu thuoc Firebase, giao dien, hay Data Contract chua duoc chot.
 */
public final class ResidentReportInputValidator {

    public enum Field {
        TITLE,
        DESCRIPTION,
        LOCATION,
        PRIORITY
    }

    private ResidentReportInputValidator() {
    }

    /**
     * Tra ve tap cac truong bi thieu de UI hien thi loi cho dung o nhap.
     * Gia tri priority chi duoc kiem tra co chon hay chua; enum hop le
     * se duoc xac dinh sau khi CON-01 duoc phe duyet.
     */
    public static EnumSet<Field> findMissingFields(
            String title, String description, String location, String priority) {
        EnumSet<Field> missing = EnumSet.noneOf(Field.class);
        if (isBlank(title)) {
            missing.add(Field.TITLE);
        }
        if (isBlank(description)) {
            missing.add(Field.DESCRIPTION);
        }
        if (isBlank(location)) {
            missing.add(Field.LOCATION);
        }
        if (isBlank(priority)) {
            missing.add(Field.PRIORITY);
        }
        return missing;
    }

    /** Kiem tra null, chuoi rong va chuoi chi chua khoang trang. */
    private static boolean isBlank(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
