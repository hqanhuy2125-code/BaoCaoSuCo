package com.example.baocaosuco.data.repository;

import static org.junit.Assert.*;

import com.example.baocaosuco.data.incident.HistoryAction;
import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** No Firebase server, Auth, Android or privileged account required. */
public class IncidentFirestoreMapperTest {
    private static Timestamp at(long seconds) { return new Timestamp(seconds, 0); }

    private static Map<String, Object> incident() {
        Map<String, Object> data = new HashMap<>();
        data.put("residentId", "alice");
        data.put("title", "No light");
        data.put("description", "Corridor light is out");
        data.put("location", "Block A");
        data.put("priority", "NORMAL");
        data.put("status", "NEW");
        data.put("assignedTo", null);
        data.put("createdAt", at(1000));
        data.put("updatedAt", at(1000));
        data.put("revision", 0L);
        data.put("lastEventId", "create-1");
        return data;
    }

    private static Map<String, Object> history() {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "CREATE");
        data.put("fromStatus", null);
        data.put("toStatus", "NEW");
        data.put("actorId", "alice");
        data.put("createdAt", at(1000));
        data.put("revision", 0L);
        data.put("note", null);
        return data;
    }

    private static void invalid(Runnable operation) {
        try {
            operation.run();
            fail("Expected invalid Firestore document to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected: fail closed; no partial DTO produced.
        }
    }

    @Test public void validIncidentDerivesDeadlineFromCreatedAt() {
        IncidentSnapshot parsed = IncidentFirestoreMapper.incident("case-1", incident());
        assertEquals("case-1", parsed.getId());
        assertEquals("alice", parsed.getResidentId());
        assertEquals("NEW", parsed.getStatusCode());
        assertEquals(0, parsed.getRevision());
        assertEquals("create-1", parsed.getLastEventId());
        assertEquals(1000_000L, parsed.getCreatedAtMillis());
        assertEquals(1000_000L + 72L * 3600 * 1000, parsed.getDueAtMillis());
    }

    @Test public void validCreateHistoryHasNullSource() {
        IncidentHistorySnapshot parsed = IncidentFirestoreMapper.history("create-1", "case-1", history());
        assertEquals(HistoryAction.CREATE, parsed.getAction());
        assertNull(parsed.getFromStatus());
        assertEquals("case-1", parsed.getIncidentId());
        assertEquals(0, parsed.getRevision());
    }

    @Test public void invalidStatusIsRejected() {
        Map<String, Object> data = incident();
        data.put("status", "Mới");
        invalid(() -> IncidentFirestoreMapper.incident("case-1", data));
    }

    @Test public void unexpectedIncidentFieldIsRejected() {
        Map<String, Object> data = incident();
        data.put("dueAt", at(1000));
        invalid(() -> IncidentFirestoreMapper.incident("case-1", data));
    }

    @Test public void wrongTimestampOrRevisionTypeIsRejected() {
        Map<String, Object> data = incident();
        data.put("createdAt", "not_timestamp");
        invalid(() -> IncidentFirestoreMapper.incident("case-1", data));
        Map<String, Object> invalidRevision = incident();
        invalidRevision.put("revision", 0.5);
        invalid(() -> IncidentFirestoreMapper.incident("case-1", invalidRevision));
    }

    @Test public void changedNewStatusMustAdvanceRevision() {
        Map<String, Object> data = incident();
        data.put("status", "ASSIGNED");
        data.put("assignedTo", "Team A");
        invalid(() -> IncidentFirestoreMapper.incident("case-1", data));
        data.put("revision", 1L);
        IncidentSnapshot parsed = IncidentFirestoreMapper.incident("case-1", data);
        assertEquals(1, parsed.getRevision());
    }

    @Test public void historyRejectsInvalidActionAndUnexpectedFields() {
        Map<String, Object> data = history();
        data.put("action", "FORGE_ROLE");
        invalid(() -> IncidentFirestoreMapper.history("evt-1", "case-1", data));
        Map<String, Object> unexpectedFields = history();
        unexpectedFields.put("secret", "x");
        invalid(() -> IncidentFirestoreMapper.history("evt-1", "case-1", unexpectedFields));
    }

    @Test public void historyRejectsBadTransitionAndNonStringNote() {
        Map<String, Object> data = history();
        data.put("action", "ASSIGN");
        data.put("fromStatus", "COMPLETED");
        data.put("toStatus", "CLOSED");
        data.put("revision", 1L);
        invalid(() -> IncidentFirestoreMapper.history("evt-1", "case-1", data));
        Map<String, Object> nonStringNote = history();
        nonStringNote.put("note", 123);
        invalid(() -> IncidentFirestoreMapper.history("evt-1", "case-1", nonStringNote));
    }
}
