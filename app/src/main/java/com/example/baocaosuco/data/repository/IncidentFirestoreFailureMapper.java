package com.example.baocaosuco.data.repository;

import com.google.firebase.firestore.FirebaseFirestoreException;

/**
 * A08: canonical errors for Firestore reads, transactions and authorization.
 * Do not retry writes automatically: CONFLICT must reload a fresh snapshot and
 * NETWORK may have an unknown commit outcome (requestId is the dedupe key).
 */
final class IncidentFirestoreFailureMapper {
    private IncidentFirestoreFailureMapper() {}

    static IncidentRepository.Error classify(FirebaseFirestoreException.Code code) {
        if (code == null) return IncidentRepository.Error.UNKNOWN;
        switch (code) {
            case UNAUTHENTICATED:
                return IncidentRepository.Error.AUTH_REQUIRED;
            case PERMISSION_DENIED:
                return IncidentRepository.Error.PERMISSION_DENIED;
            case INVALID_ARGUMENT:
                return IncidentRepository.Error.VALIDATION;
            case NOT_FOUND:
                return IncidentRepository.Error.NOT_FOUND;
            case ABORTED:
            case ALREADY_EXISTS:
                return IncidentRepository.Error.CONFLICT;
            case UNAVAILABLE:
            case DEADLINE_EXCEEDED:
                return IncidentRepository.Error.NETWORK;
            case FAILED_PRECONDITION:
                return IncidentRepository.Error.SECURITY_NOT_READY;
            default:
                // Quota exhaustion, internal errors and data loss are not safely
                // interchangeable with transient connectivity problems.
                return IncidentRepository.Error.UNKNOWN;
        }
    }
}
