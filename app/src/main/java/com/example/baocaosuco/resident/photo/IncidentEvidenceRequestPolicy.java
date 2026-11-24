package com.example.baocaosuco.resident.photo;

import java.util.regex.Pattern;

/**
 * Pure Java C01 shared-evidence request policy. This is input hygiene only:
 * the remote Firebase/Firestore gateway is the actual authorization boundary.
 */
public final class IncidentEvidenceRequestPolicy {
    public static final int MAX_JPEG_BYTES = 2 * 1024 * 1024;
    private static final Pattern INCIDENT_ID =
            Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern EVIDENCE_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");

    private IncidentEvidenceRequestPolicy() { }

    public static boolean validIncidentId(String id) {
        return id != null && INCIDENT_ID.matcher(id).matches();
    }

    public static boolean validEvidenceId(String id) {
        return id != null && EVIDENCE_ID.matcher(id).matches();
    }

    /** JPEG markers and bounded file size; server validates independently. */
    public static boolean validJpeg(byte[] bytes) {
        return bytes != null && bytes.length >= 5
                && bytes.length <= MAX_JPEG_BYTES
                && (bytes[0] & 255) == 255
                && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255
                && (bytes[bytes.length - 2] & 255) == 255
                && (bytes[bytes.length - 1] & 255) == 217;
    }
}
