package com.example.baocaosuco.integration.ui;

import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.data.incident.NewIncidentInput;
import com.example.baocaosuco.logic.IncidentStatus;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;

public class ManagerPageOrderTest {
    private static final long HOUR = 3600000;

    @Test public void priorityAndDeadlineSortTheSamePageDifferently() {
        IncidentSnapshot olderNormal = item("normal", "NORMAL", HOUR);
        IncidentSnapshot urgentLate = item("urgent-late", "URGENT", 90 * HOUR);
        IncidentSnapshot urgentEarly = item("urgent-early", "URGENT", 80 * HOUR);
        IncidentSnapshot low = item("low", "LOW", HOUR);
        List<IncidentSnapshot> page = new ArrayList<>(
                Arrays.asList(low, urgentLate, olderNormal, urgentEarly));

        ManagerPageOrder.PRIORITY_THEN_DUE.sort(page);
        assertEquals(Arrays.asList(urgentEarly, urgentLate, olderNormal, low), page);
        ManagerPageOrder.DUE_FIRST.sort(page);
        assertEquals(Arrays.asList(olderNormal, urgentEarly, urgentLate, low), page);
        ManagerPageOrder.NEWEST.sort(page);
        assertEquals(Arrays.asList(urgentLate, urgentEarly, low, olderNormal), page);
    }

    private static IncidentSnapshot item(String id, String priority, long created) {
        return new IncidentSnapshot(id, "owner",
                new NewIncidentInput("Test", "Description", "QA", priority),
                IncidentStatus.NEW, null, created, created, 0, "event");
    }
}
