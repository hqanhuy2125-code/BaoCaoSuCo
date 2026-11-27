package com.example.baocaosuco.resident.photo;

import org.junit.Test;

import static org.junit.Assert.*;

/** Focused policy unit tests; Camera/Picker state is tested in its existing class. */
public class IncidentPhotoPolicyTest {
    @Test public void acceptsOnlyJpegOrPngOrSniffableUnknownMime() {
        assertTrue(IncidentPhotoPolicy.supportsMime("image/jpeg"));
        assertTrue(IncidentPhotoPolicy.supportsMime("image/png"));
        assertTrue(IncidentPhotoPolicy.supportsMime(null));
        assertFalse(IncidentPhotoPolicy.supportsMime("image/heic"));
        assertFalse(IncidentPhotoPolicy.supportsMime("application/pdf"));
    }

    @Test public void rejectsInvalidOrHugeDimensions() {
        assertFalse(IncidentPhotoPolicy.validDimensions(0, 100));
        assertFalse(IncidentPhotoPolicy.validDimensions(100, -1));
        assertFalse(IncidentPhotoPolicy.validDimensions(16001, 100));
        assertTrue(IncidentPhotoPolicy.validDimensions(16000, 16000));
    }

    @Test public void samplesLargePhotoWithoutFullResolutionAllocation() {
        assertEquals(1, IncidentPhotoPolicy.sampleSize(1600, 1200));
        assertEquals(2, IncidentPhotoPolicy.sampleSize(2400, 1600));
        assertEquals(4, IncidentPhotoPolicy.sampleSize(6000, 4000));
    }

    @Test public void preservesAspectRatioAndNeverUpscales() {
        assertEquals(800, IncidentPhotoPolicy.targetSide(800, 1200));
        assertEquals(1600, IncidentPhotoPolicy.targetSide(4000, 4000));
        assertEquals(1200, IncidentPhotoPolicy.targetSide(3000, 4000));
    }
}
