package com.example.baocaosuco.resident.photo;

/**
 * G1: Pure Java single-request gate, independent from Android/Firestore.
 * A second picker/camera action cannot overwrite the first pending result.
 */
final class IncidentPhotoRequestState {
    enum Operation { CAMERA, GALLERY }

    private Operation pending;
    private boolean closed;

    boolean begin(Operation operation) {
        if (operation == null || closed || pending != null) return false;
        pending = operation;
        return true;
    }

    boolean finish(Operation operation) {
        if (closed || pending != operation || operation == null) return false;
        pending = null;
        return true;
    }

    boolean isPending(Operation operation) {
        return !closed && pending == operation && operation != null;
    }

    boolean isBusy() {
        return !closed && pending != null;
    }

    boolean isClosed() {
        return closed;
    }

    void close() {
        closed = true;
        pending = null;
    }
}
