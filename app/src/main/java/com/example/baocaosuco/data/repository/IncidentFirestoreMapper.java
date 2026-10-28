package com.example.baocaosuco.data.repository;

import com.example.baocaosuco.data.incident.HistoryAction;
import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.logic.IncidentStatus;
import com.example.baocaosuco.logic.IncidentStatusCodec;
import com.google.firebase.Timestamp;

import java.util.Map;

/**
 * Strict, side-effect-free Firestore document decoder for CON-01 v1.
 * No permissions, database reads or UI dependencies are hidden here.
 */
final class IncidentFirestoreMapper {
    private IncidentFirestoreMapper() {}

    static IncidentSnapshot incident(String id, Map<String, Object> fields) {
        exactShape(fields, "residentId", "title", "description", "location", "priority",
                "status", "assignedTo", "createdAt", "updatedAt", "revision", "lastEventId");
        NewIncidentInput input = new NewIncidentInput(
                strictString(fields, "title", 120),
                strictString(fields, "description", 2000),
                strictString(fields, "location", 160), string(fields, "priority"));
        IncidentStatus status = IncidentStatusCodec.parseCanonical(string(fields, "status"));
        if (status == null) throw new IllegalArgumentException("Invalid incident status");
        Object rawHandler = fields.get("assignedTo");
        if (rawHandler != null && !(rawHandler instanceof String)) {
            throw new IllegalArgumentException("Invalid incident assignee");
        }
        if (rawHandler instanceof String && !rawHandler.equals(((String) rawHandler).trim())) {
            throw new IllegalArgumentException("Noncanonical incident assignee");
        }
        return new IncidentSnapshot(id, string(fields, "residentId"), input, status,
                (String) rawHandler, millis(fields, "createdAt"), millis(fields, "updatedAt"),
                revision(fields), strictString(fields, "lastEventId", 128));
    }

    static IncidentHistorySnapshot history(String eventId, String incidentId,
                                           Map<String, Object> fields) {
        exactShape(fields, "action", "fromStatus", "toStatus", "actorId",
                "createdAt", "revision", "note");
        HistoryAction action;
        try {
            action = HistoryAction.valueOf(string(fields, "action"));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid history action", invalid);
        }
        Object fromRaw = fields.get("fromStatus");
        if (fromRaw != null && !(fromRaw instanceof String)) {
            throw new IllegalArgumentException("Invalid history fromStatus");
        }
        IncidentStatus from = fromRaw == null ? null
                : IncidentStatusCodec.parseCanonical((String) fromRaw);
        if (fromRaw != null && from == null) throw new IllegalArgumentException("Invalid fromStatus");
        IncidentStatus to = IncidentStatusCodec.parseCanonical(string(fields, "toStatus"));
        if (to == null) throw new IllegalArgumentException("Invalid toStatus");
        Object rawNote = fields.get("note");
        if (rawNote != null && !(rawNote instanceof String)) {
            throw new IllegalArgumentException("Invalid history note");
        }
        if (rawNote instanceof String) {
            String note = (String) rawNote;
            if (note.isEmpty() || !note.equals(note.trim())
                    || note.codePointCount(0, note.length()) > 500) {
                throw new IllegalArgumentException("Noncanonical history note");
            }
        }
        return new IncidentHistorySnapshot(eventId, incidentId, action, from, to,
                string(fields, "actorId"), millis(fields, "createdAt"),
                revision(fields), (String) rawNote);
    }

    private static void exactShape(Map<String, Object> fields, String... required) {
        if (fields == null || fields.size() != required.length) {
            throw new IllegalArgumentException("Missing or unexpected document fields");
        }
        for (String field : required) {
            if (!fields.containsKey(field)) {
                throw new IllegalArgumentException("Missing field: " + field);
            }
        }
    }

    private static String string(Map<String, Object> fields, String field) {
        Object value = fields.get(field);
        if (!(value instanceof String)) throw new IllegalArgumentException("Invalid " + field);
        return (String) value;
    }

    private static String strictString(Map<String, Object> fields, String field, int maxCodePoints) {
        String value = string(fields, field);
        if (value.isEmpty() || !value.equals(value.trim())
                || value.codePointCount(0, value.length()) > maxCodePoints) {
            throw new IllegalArgumentException("Noncanonical " + field);
        }
        return value;
    }

    private static long millis(Map<String, Object> fields, String field) {
        Object value = fields.get(field);
        if (!(value instanceof Timestamp)) throw new IllegalArgumentException("Invalid " + field);
        long time = ((Timestamp) value).toDate().getTime();
        if (time < 0) throw new IllegalArgumentException("Negative " + field);
        return time;
    }

    private static int revision(Map<String, Object> fields) {
        Object value = fields.get("revision");
        if (!(value instanceof Long) && !(value instanceof Integer)) {
            throw new IllegalArgumentException("Invalid revision type");
        }
        long revision = ((Number) value).longValue();
        if (revision < 0 || revision > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Revision out of range");
        }
        return (int) revision;
    }
}
