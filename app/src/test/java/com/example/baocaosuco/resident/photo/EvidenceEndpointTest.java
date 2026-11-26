package com.example.baocaosuco.resident.photo;

import org.junit.Test;
import static org.junit.Assert.*;

public class EvidenceEndpointTest {
    @Test public void disabledAndIncompleteStagingDoNotResolve() {
        assertNull(EvidenceEndpoint.resolve("disabled", "", "", true));
        assertNull(EvidenceEndpoint.resolve("staging", "", "", true));
        assertNull(EvidenceEndpoint.resolve("staging", "https://other.example", "fixture-only", true));
    }

    @Test public void stagingIsDebugOnlyAndCannotUseProductionAccountsOrHost() {
        String fixtureRef = "abcdefghijklmnopqrst";
        assertNull(EvidenceEndpoint.resolve("staging", fixtureRef, "fixture-only", false));
        assertNull(EvidenceEndpoint.resolve("staging", fixtureRef, "baocaosuco-c4ccc", true));
        assertNull(EvidenceEndpoint.resolve("staging", "xnmlnqaetcaawvsbmhjy", "fixture-only", true));
        EvidenceEndpoint fixture = EvidenceEndpoint.resolve("staging", fixtureRef, "fixture-only", true);
        assertNotNull(fixture);
        assertEquals("https", fixture.gateway.getScheme());
        assertEquals(fixtureRef + ".supabase.co", fixture.gateway.getHost());
        assertEquals("/functions/v1/evidence-gateway", fixture.gateway.getPath());
    }

    @Test public void productionRejectsOverridesByKeepingTheFixedPair() {
        EvidenceEndpoint production = EvidenceEndpoint.resolve("production", "other", "other", false);
        assertEquals("xnmlnqaetcaawvsbmhjy.supabase.co", production.gateway.getHost());
        assertEquals("baocaosuco-c4ccc", production.firebaseProject);
    }
}
