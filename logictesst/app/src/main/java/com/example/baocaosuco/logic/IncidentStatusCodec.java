package com.example.baocaosuco.logic;

/** PROPOSED canonical status names. UI labels are presentation only. */
public final class IncidentStatusCodec {
    private IncidentStatusCodec() {}
    public static IncidentStatus parseCanonical(String value) {
        if (value == null) return null;
        for (IncidentStatus status : IncidentStatus.values()) {
            if (status.name().equals(value)) return status;
        }
        return null;
    }
    public static String toCanonical(IncidentStatus value) {
        if (value == null) throw new IllegalArgumentException("Missing status");
        return value.name();
    }
}
