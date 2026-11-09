package com.example.baocaosuco.resident.photo;

import org.junit.Test;

import static org.junit.Assert.*;

/** G1 pure-JVM coverage; Camera/Picker platform behavior requires Android device tests. */
public class IncidentPhotoRequestStateTest {
    @Test public void cameraCanStartAndComplete() {
        IncidentPhotoRequestState state = new IncidentPhotoRequestState();
        assertTrue(state.begin(IncidentPhotoRequestState.Operation.CAMERA));
        assertTrue(state.isPending(IncidentPhotoRequestState.Operation.CAMERA));
        assertTrue(state.finish(IncidentPhotoRequestState.Operation.CAMERA));
        assertFalse(state.isBusy());
    }

    @Test public void galleryCanStartAndComplete() {
        IncidentPhotoRequestState state = new IncidentPhotoRequestState();
        assertTrue(state.begin(IncidentPhotoRequestState.Operation.GALLERY));
        assertTrue(state.finish(IncidentPhotoRequestState.Operation.GALLERY));
        assertFalse(state.isBusy());
    }

    @Test public void overlappingRequestIsRejected() {
        IncidentPhotoRequestState state = new IncidentPhotoRequestState();
        assertTrue(state.begin(IncidentPhotoRequestState.Operation.CAMERA));
        assertFalse(state.begin(IncidentPhotoRequestState.Operation.GALLERY));
        assertTrue(state.isPending(IncidentPhotoRequestState.Operation.CAMERA));
    }

    @Test public void mismatchedCallbackCannotFinishPendingRequest() {
        IncidentPhotoRequestState state = new IncidentPhotoRequestState();
        assertTrue(state.begin(IncidentPhotoRequestState.Operation.GALLERY));
        assertFalse(state.finish(IncidentPhotoRequestState.Operation.CAMERA));
        assertTrue(state.isPending(IncidentPhotoRequestState.Operation.GALLERY));
    }

    @Test public void duplicateCompletionCannotReplay() {
        IncidentPhotoRequestState state = new IncidentPhotoRequestState();
        assertTrue(state.begin(IncidentPhotoRequestState.Operation.CAMERA));
        assertTrue(state.finish(IncidentPhotoRequestState.Operation.CAMERA));
        assertFalse(state.finish(IncidentPhotoRequestState.Operation.CAMERA));
    }

    @Test public void closeInvalidatesPendingAndFutureRequests() {
        IncidentPhotoRequestState state = new IncidentPhotoRequestState();
        assertTrue(state.begin(IncidentPhotoRequestState.Operation.CAMERA));
        state.close();
        assertTrue(state.isClosed());
        assertFalse(state.isBusy());
        assertFalse(state.finish(IncidentPhotoRequestState.Operation.CAMERA));
        assertFalse(state.begin(IncidentPhotoRequestState.Operation.GALLERY));
        state.close();
    }

    @Test public void nullOperationsAreRejected() {
        IncidentPhotoRequestState state = new IncidentPhotoRequestState();
        assertFalse(state.begin(null));
        assertFalse(state.finish(null));
        assertFalse(state.isPending(null));
    }

    @Test public void successiveNonoverlappingRequestsWork() {
        IncidentPhotoRequestState state = new IncidentPhotoRequestState();
        assertTrue(state.begin(IncidentPhotoRequestState.Operation.CAMERA));
        assertTrue(state.finish(IncidentPhotoRequestState.Operation.CAMERA));
        assertTrue(state.begin(IncidentPhotoRequestState.Operation.GALLERY));
        assertTrue(state.finish(IncidentPhotoRequestState.Operation.GALLERY));
    }
}
