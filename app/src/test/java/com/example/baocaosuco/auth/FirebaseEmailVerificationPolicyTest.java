package com.example.baocaosuco.auth;

import com.example.baocaosuco.data.model.User;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FirebaseEmailVerificationPolicyTest {
    private static final long CUTOFF = 2_000;

    @Test public void existingResidentAndManagerKeepAccess() {
        assertFalse(FirebaseAuthRepository.requiresVerification(User.ROLE_RESIDENT, CUTOFF, CUTOFF - 1, false));
        assertFalse(FirebaseAuthRepository.requiresVerification(User.ROLE_MANAGER, CUTOFF, CUTOFF + 1, false));
        assertFalse(FirebaseAuthRepository.requiresVerification(User.ROLE_RESIDENT, 0, CUTOFF + 1, false));
    }

    @Test public void newResidentsNeedNativeVerificationAtAndAfterCutoff() {
        assertTrue(FirebaseAuthRepository.requiresVerification(User.ROLE_RESIDENT, CUTOFF, CUTOFF, false));
        assertTrue(FirebaseAuthRepository.requiresVerification(User.ROLE_RESIDENT, CUTOFF, CUTOFF + 1, false));
        assertTrue(FirebaseAuthRepository.requiresVerification(User.ROLE_RESIDENT, CUTOFF, 0, false));
        assertFalse(FirebaseAuthRepository.requiresVerification(User.ROLE_RESIDENT, CUTOFF, CUTOFF + 1, true));
    }

    @Test public void registrationNeverStartsBeforeConfiguredRollout() {
        assertFalse(FirebaseAuthRepository.registrationOpen(0, CUTOFF + 1));
        assertFalse(FirebaseAuthRepository.registrationOpen(CUTOFF, CUTOFF - 1));
        assertTrue(FirebaseAuthRepository.registrationOpen(CUTOFF, CUTOFF));
    }
}
