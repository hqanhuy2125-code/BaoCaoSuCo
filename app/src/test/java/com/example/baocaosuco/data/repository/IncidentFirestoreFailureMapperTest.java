package com.example.baocaosuco.data.repository;

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
                        "UNAUTHENTICATED"));
    }

    @Test public void permissionDeniedNeverBecomesAuthOrRetryableNetwork() {
        assertEquals(IncidentRepository.Error.PERMISSION_DENIED,
                IncidentFirestoreFailureMapper.classify(
                        "PERMISSION_DENIED"));
    }

    @Test public void missingDocumentMapsToNotFound() {
        assertEquals(IncidentRepository.Error.NOT_FOUND,
                IncidentFirestoreFailureMapper.classify(
                        "NOT_FOUND"));
    }

    @Test public void malformedArgumentMapsToValidation() {
        assertEquals(IncidentRepository.Error.VALIDATION,
                IncidentFirestoreFailureMapper.classify(
                        "INVALID_ARGUMENT"));
    }

    @Test public void transientNetworkAndDeadlineFailuresAreDistinguished() {
        assertEquals(IncidentRepository.Error.NETWORK,
                IncidentFirestoreFailureMapper.classify(
                        "UNAVAILABLE"));
        assertEquals(IncidentRepository.Error.NETWORK,
                IncidentFirestoreFailureMapper.classify(
                        "DEADLINE_EXCEEDED"));
    }

    @Test public void transactionContentionAndDuplicateWriteAreConflicts() {
        assertEquals(IncidentRepository.Error.CONFLICT,
                IncidentFirestoreFailureMapper.classify(
                        "ABORTED"));
        assertEquals(IncidentRepository.Error.CONFLICT,
                IncidentFirestoreFailureMapper.classify(
                        "ALREADY_EXISTS"));
    }

    @Test public void missingIndexOrPreconditionRemainsSecurityNotReady() {
        assertEquals(IncidentRepository.Error.SECURITY_NOT_READY,
                IncidentFirestoreFailureMapper.classify(
                        "FAILED_PRECONDITION"));
    }

    @Test public void quotaAndUnexpectedFailuresRemainUnknownRatherThanRetryable() {
        assertEquals(IncidentRepository.Error.UNKNOWN,
                IncidentFirestoreFailureMapper.classify(
                        "RESOURCE_EXHAUSTED"));
        assertEquals(IncidentRepository.Error.UNKNOWN,
                IncidentFirestoreFailureMapper.classify(
                        "INTERNAL"));
        assertEquals(IncidentRepository.Error.UNKNOWN,
                IncidentFirestoreFailureMapper.classify(null));
    }
}
