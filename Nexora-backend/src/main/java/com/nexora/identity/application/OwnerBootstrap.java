package com.nexora.identity.application;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.identity.domain.User;
import com.nexora.identity.infrastructure.RoleRepository;
import com.nexora.identity.infrastructure.UserRepository;

// Creates the first OWNER at startup (FR-AUTH-10), so a brand-new installation has someone who can log in.
// It runs only while the users table is empty; afterwards the BOOTSTRAP_OWNER_* variables are ignored
// and should be removed from the server's environment.
@Component
public class OwnerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public OwnerBootstrap(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                          @Value("${nexora.bootstrap.owner-username:}") String username,
                          @Value("${nexora.bootstrap.owner-password:}") String password) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    // Called by Spring Boot once the application has started. An exception here stops the startup (fail fast).
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createOwnerIfNoUsers(username, password);
    }

    @Transactional(readOnly = true)
    public boolean hasUsers() {
        return users.count() > 0;
    }

    // Returns true when an owner was created.
    @Transactional
    public boolean createOwnerIfNoUsers(String username, String password) {
        if (users.count() > 0) {
            return false;                                   // AC2: never touch an existing installation
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("event=bootstrap_skipped reason=no_users_and_no_bootstrap_variables");
            return false;
        }
        String normalized = User.normalizeUsername(username);
        checkPasswordPolicy(normalized, password);          // AC3: a weak password stops startup
        users.save(User.create(normalized, passwordEncoder.encode(password), "Owner",
                roles.findByName("OWNER").orElseThrow(() -> new IllegalStateException("Role OWNER is not seeded"))));
        log.info("event=bootstrap_owner_created username={}", normalized);   // never the password
        return true;
    }

    // Password policy (09-SECURITY 2.4): 12-72 bytes (BCrypt ignores anything after 72 bytes),
    // at least one letter and one digit, and not the username itself.
    static void checkPasswordPolicy(String username, String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (bytes < 12 || bytes > 72 || !hasLetter || !hasDigit || password.equalsIgnoreCase(username)) {
            throw new IllegalStateException("nexora.bootstrap.owner-password (env BOOTSTRAP_OWNER_PASSWORD) must be "
                    + "12-72 bytes with at least one letter and one digit, and differ from the username");
        }
    }
}
