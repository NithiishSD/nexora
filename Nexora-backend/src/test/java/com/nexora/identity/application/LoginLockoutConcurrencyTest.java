package com.nexora.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nexora.identity.api.dto.LoginRequest;
import com.nexora.identity.api.dto.TokenResponse;
import com.nexora.identity.domain.User;
import com.nexora.identity.infrastructure.RoleRepository;
import com.nexora.identity.infrastructure.UserRepository;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.support.AbstractIntegrationTest;
import com.nexora.support.ConcurrentRunner;
import com.nexora.support.ConcurrentRunner.Outcome;

// Five wrong passwords sent at the same moment must all be counted. Without the row lock in
// UserRepository.findByUsernameForUpdate, parallel transactions would overwrite each other's counter
// (or fail on the @Version check) and the account would not be locked.
class LoginLockoutConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("FR-AUTH-07 parallel wrong passwords count every failure")
    void parallelWrongPasswords_countEveryFailure() throws Exception {
        String username = "lockout-" + System.nanoTime();
        users.save(User.create(username, passwordEncoder.encode("Right-Password-2026"), "Lockout",
                roles.findByName("OWNER").orElseThrow()));

        List<Callable<TokenResponse>> attempts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            attempts.add(() -> authService.login(new LoginRequest(username, "Wrong-Password-1")));
        }
        List<Outcome<TokenResponse>> outcomes = ConcurrentRunner.runTogether(attempts);

        // every attempt failed with the generic login error (no 500, no optimistic-lock error)
        assertThat(outcomes).allSatisfy(o -> assertThat(o.error())
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS)));
        assertThat(jdbc.queryForObject("select failed_login_attempts from users where username = ?",
                Integer.class, username)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select locked_until > now() from users where username = ?",
                Boolean.class, username)).isTrue();
    }
}
