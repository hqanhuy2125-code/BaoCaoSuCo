package com.example.baocaosuco.resident.photo;

import org.junit.Test;

import static org.junit.Assert.*;

/** Only new cloud-request boundary cases; existing Camera/Repository tests stay unchanged. */
public class IncidentEvidenceRequestPolicyTest {
    @Test public void allowsCanonicalIncidentIds() {
        assertTrue(IncidentEvidenceRequestPolicy.validIncidentId("ABC_01-02"));
        assertTrue(IncidentEvidenceRequestPolicy.validIncidentId("a"));
    }

    @Test public void rejectsInvalidIncidentIds() {
        assertFalse(IncidentEvidenceRequestPolicy.validIncidentId(null));
        assertFalse(IncidentEvidenceRequestPolicy.validIncidentId(""));
        assertFalse(IncidentEvidenceRequestPolicy.validIncidentId("../unsafe"));
        assertFalse(IncidentEvidenceRequestPolicy.validIncidentId("a/b"));
        assertFalse(IncidentEvidenceRequestPolicy.validIncidentId("a?kind=RESOLUTION"));
    }

    @Test public void validatesEvidenceUuidShape() {
        assertTrue(IncidentEvidenceRequestPolicy.validEvidenceId(
                "c5ab61b8-61bd-49be-8a67-cc3bfcb64e9b"));
        assertFalse(IncidentEvidenceRequestPolicy.validEvidenceId("not-an-id"));
        assertFalse(IncidentEvidenceRequestPolicy.validEvidenceId("../data"));
    }

    @Test public void checksJpegMarkers() {
        assertTrue(IncidentEvidenceRequestPolicy.validJpeg(
                new byte[]{(byte)255, (byte)216, (byte)255, 1, (byte)255, (byte)217}));
        assertFalse(IncidentEvidenceRequestPolicy.validJpeg(
                new byte[]{(byte)137, 'P', 'N', 'G', 0, 0}));
    }

    @Test public void rejectsNullTruncatedAndTooLargeFiles() {
        assertFalse(IncidentEvidenceRequestPolicy.validJpeg(null));
        assertFalse(IncidentEvidenceRequestPolicy.validJpeg(new byte[4]));
        assertFalse(IncidentEvidenceRequestPolicy.validJpeg(
                new byte[IncidentEvidenceRequestPolicy.MAX_JPEG_BYTES + 1]));
    }
}
