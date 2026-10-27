package com.example.baocaosuco.data.repository;

import com.example.baocaosuco.data.incident.IncidentHistorySnapshot;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.logic.IncidentStatus;

import org.junit.Test;
import static org.junit.Assert.*;

public final class IncidentContractCandidateTest {
    @Test public void inputAcceptsOnlyCanonicalPriorityAndTrimsFields() {
        NewIncidentInput input = new NewIncidentInput("  Light  ", "  broken  ", "  A2  ", "NORMAL");
        assertEquals("Light", input.getTitle());
        assertEquals("broken", input.getDescription());
        assertEquals("A2", input.getLocation());
        assertEquals("NORMAL", input.getPriority());
    }

    @Test public void inputRejectsArbitraryStatusAndPriority() {
        for (String value : new String[]{"Urgent", "Thường", "MANAGER", "UNSUPPORTED"}) {
            try {
                new NewIncidentInput("t", "d", "loc", value);
                fail("Non-canonical priority must be denied: " + value);
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void inputRejectsMissingFields() {
        try {
            new NewIncidentInput("  ", "description", "floor", "NORMAL");
            fail("blank title");
        } catch (IllegalArgumentException expected) { }
        try {
            new NewIncidentInput("title", null, "floor", "LOW");
            fail("missing description");
        } catch (IllegalArgumentException expected) { }
        try {
            new NewIncidentInput("title", "description", " ", "URGENT");
            fail("blank location");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void incidentReadModelUsesCanonicalCodesAndImmutableTransition() {
        NewIncidentInput draft = new NewIncidentInput("t", "d", "room", "URGENT");
        IncidentSnapshot original = new IncidentSnapshot("i1", "residentA", draft,
                IncidentStatus.NEW, null, 1000L, 14401000L, 1000L);
        IncidentSnapshot transitioned = original.withTransition(
                IncidentStatus.ASSIGNED, "Team X", 2000L);
        assertEquals("NEW", original.getStatusCode());
        assertEquals("ASSIGNED", transitioned.getStatusCode());
        assertEquals(IncidentStatus.NEW, original.getStatus());
        assertNull(original.getAssignedTo());
        assertEquals("Team X", transitioned.getAssignedTo());
        assertEquals(original.getDueAtMillis(), transitioned.getDueAtMillis());
        assertEquals(original.getResidentId(), transitioned.getResidentId());
    }

    @Test public void incidentReadModelRejectsInvalidTimestampOrdering() {
        NewIncidentInput draft = new NewIncidentInput("t", "d", "room", "LOW");
        try {
            new IncidentSnapshot("i1", "residentA", draft, IncidentStatus.NEW,
                    null, 100L, 99L, 100L);
            fail("Invalid deadline");
        } catch (IllegalArgumentException expected) { }
        try {
            new IncidentSnapshot("i1", "residentA", draft, IncidentStatus.NEW,
                    null, 100L, 200L, 99L);
            fail("Invalid updated time");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void historyCandidateCarriesActorAndStatesWithoutStoredPathAssumptions() {
        IncidentHistorySnapshot item = new IncidentHistorySnapshot("e1", "i1",
                IncidentStatus.COMPLETED, IncidentStatus.CLOSED, "residentA", 3500L, null);
        assertEquals("e1", item.getEventId());
        assertEquals("i1", item.getIncidentId());
        assertEquals("residentA", item.getActorId());
        assertNull(item.getNote());
        assertEquals(IncidentStatus.COMPLETED, item.getFromStatus());
        assertEquals(IncidentStatus.CLOSED, item.getToStatus());
    }

    @Test public void historyRejectsNoOpEvents() {
        try {
            new IncidentHistorySnapshot("e1", "i1", IncidentStatus.NEW,
                    IncidentStatus.NEW, "actor", 100L, "");
            fail("No-op history is invalid");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void repositoryCapabilityInterfacesAndErrorsAreExplicit() {
        assertTrue(IncidentRepository.Resident.class.isInterface());
        assertTrue(IncidentRepository.Manager.class.isInterface());
        assertTrue(IncidentRepository.Callback.class.isInterface());
        assertTrue(IncidentRepository.Subscription.class.isInterface());
        assertEquals(IncidentRepository.Error.SECURITY_NOT_READY,
                IncidentRepository.Error.valueOf("SECURITY_NOT_READY"));
        assertFalse(IncidentRepository.Resident.class
                .isAssignableFrom(IncidentRepository.Manager.class));
    }
}
