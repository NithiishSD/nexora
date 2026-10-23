package com.nexora.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Plain unit tests of the login-lock rules on the User entity (no Spring, no database).
class UserTest {

    private static final Instant T0 = Instant.parse("2026-10-07T10:00:00Z");
    private static final int MAX = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);

    private final User user = User.create(" Owner ", "{bcrypt}hash", "Owner", new Role());

    @Test
    @DisplayName("FR-AUTH-07 NFR-TEST-08 record failed login fifth failure locks account for15 minutes")
    void recordFailedLogin_fifthFailure_locksAccountFor15Minutes() {
        for (int i = 1; i <= 4; i++) {
            assertThat(user.recordFailedLogin(T0, MAX, LOCK)).isFalse();   // failures 1-4: not locked yet
        }
        assertThat(user.isLocked(T0)).isFalse();

        boolean lockedNow = user.recordFailedLogin(T0, MAX, LOCK);        // 5th failure

        assertThat(lockedNow).isTrue();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isEqualTo(T0.plus(LOCK));
        assertThat(user.isLocked(T0.plus(Duration.ofMinutes(14)))).isTrue();
    }

    @Test
    @DisplayName("FR-AUTH-07 is locked after lock expiry returns false")
    void isLocked_afterLockExpiry_returnsFalse() {
        for (int i = 0; i < MAX; i++) {
            user.recordFailedLogin(T0, MAX, LOCK);
        }

        assertThat(user.isLocked(T0.plus(LOCK))).isFalse();               // exactly 15 minutes later
    }

    @Test
    @DisplayName("FR-AUTH-07 failure after expired lock starts counting from one")
    void recordFailedLogin_afterExpiredLock_startsCountingFromOne() {
        for (int i = 0; i < MAX; i++) {
            user.recordFailedLogin(T0, MAX, LOCK);
        }
        Instant later = T0.plus(LOCK).plusSeconds(1);

        assertThat(user.recordFailedLogin(later, MAX, LOCK)).isFalse();

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLocked(later)).isFalse();
    }

    @Test
    @DisplayName("FR-AUTH-07 four failures then success resets the counter")
    void recordSuccessfulLogin_afterFourFailures_resetsCounter() {
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(T0, MAX, LOCK);
        }

        user.recordSuccessfulLogin(T0);
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(T0, MAX, LOCK);                       // AC3: 4 more failures
        }

        assertThat(user.isLocked(T0)).isFalse();
        assertThat(user.getLastLoginAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("FR-AUTH-01 username is trimmed and stored lower case")
    void create_normalizesUsername() {
        assertThat(user.getUsername()).isEqualTo("owner");
        assertThat(user.isActive()).isTrue();
    }
}
