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

    @Test public void sharedPhotoQaRequiresItsExactPairAndDebugOptIn() {
        String ref = "toqmwniuwqinielydgnp";
        String firebase = "baocaosuco-c4ccc";
        EvidenceEndpoint qa = EvidenceEndpoint.resolve("qa-shared-photo", ref, firebase, true);
        assertNotNull(qa);
        assertEquals("https://toqmwniuwqinielydgnp.supabase.co/functions/v1/evidence-gateway", qa.gateway.toString());
        assertEquals(firebase, qa.firebaseProject);
        assertNull(EvidenceEndpoint.resolve("qa-shared-photo", ref, firebase, false));
        assertNull(EvidenceEndpoint.resolve("qa-shared-photo", "abcdefghijklmnopqrst", firebase, true));
        assertNull(EvidenceEndpoint.resolve("qa-shared-photo", ref, "fixture-only", true));
        assertNull(EvidenceEndpoint.resolve("qa-shared-photo", "", "", true));
        assertNull(EvidenceEndpoint.resolve("staging", ref, firebase, true));
        assertNull(EvidenceEndpoint.resolve("disabled", ref, firebase, true));
    }

    @Test public void productionRejectsOverridesByKeepingTheFixedPair() {
        EvidenceEndpoint production = EvidenceEndpoint.resolve("production", "other", "other", false);
        assertEquals("xnmlnqaetcaawvsbmhjy.supabase.co", production.gateway.getHost());
        assertEquals("baocaosuco-c4ccc", production.firebaseProject);
    }
}
