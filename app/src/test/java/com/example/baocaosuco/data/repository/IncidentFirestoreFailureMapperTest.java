package com.example.baocaosuco.data.repository;

import com.google.firebase.firestore.FirebaseFirestoreException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A08 error contract tests are pure JVM; no Firebase tenant, Auth fixture,
 * Rules deployment or Android device is needed.
 */
public class IncidentFirestoreFailureMapperTest {
    @Test public void unauthenticatedIsNotCollapsedToUnknown() {
        assertEquals(IncidentRepository.Error.AUTH_REQUIRED,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.UNAUTHENTICATED));
    }

    @Test public void permissionDeniedNeverBecomesAuthOrRetryableNetwork() {
        assertEquals(IncidentRepository.Error.PERMISSION_DENIED,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.PERMISSION_DENIED));
    }

    @Test public void missingDocumentMapsToNotFound() {
        assertEquals(IncidentRepository.Error.NOT_FOUND,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.NOT_FOUND));
    }

    @Test public void malformedArgumentMapsToValidation() {
        assertEquals(IncidentRepository.Error.VALIDATION,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.INVALID_ARGUMENT));
    }

    @Test public void transientNetworkAndDeadlineFailuresAreDistinguished() {
        assertEquals(IncidentRepository.Error.NETWORK,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.UNAVAILABLE));
        assertEquals(IncidentRepository.Error.NETWORK,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED));
    }

    @Test public void transactionContentionAndDuplicateWriteAreConflicts() {
        assertEquals(IncidentRepository.Error.CONFLICT,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.ABORTED));
        assertEquals(IncidentRepository.Error.CONFLICT,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.ALREADY_EXISTS));
    }

    @Test public void missingIndexOrPreconditionRemainsSecurityNotReady() {
        assertEquals(IncidentRepository.Error.SECURITY_NOT_READY,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION));
    }

    @Test public void quotaAndUnexpectedFailuresRemainUnknownRatherThanRetryable() {
        assertEquals(IncidentRepository.Error.UNKNOWN,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED));
        assertEquals(IncidentRepository.Error.UNKNOWN,
                IncidentFirestoreFailureMapper.classify(
                        FirebaseFirestoreException.Code.INTERNAL));
        assertEquals(IncidentRepository.Error.UNKNOWN,
                IncidentFirestoreFailureMapper.classify(null));
    }
}
