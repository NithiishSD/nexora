package com.nexora.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nexora.identity.domain.User;
import com.nexora.identity.infrastructure.RoleRepository;
import com.nexora.identity.infrastructure.UserRepository;
import com.nexora.support.AbstractIntegrationTest;

// The application's PasswordEncoder bean must store BCrypt hashes with the {bcrypt} prefix (FR-AUTH-08).
class PasswordStorageIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserRepository users;

    @Autowired
    RoleRepository roles;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("FR-AUTH-08 stored hash uses bcrypt prefix")
    void storedHash_usesBcryptPrefix() {
        String plain = "Storage-Check-2026";
        User user = users.save(User.create("storage-" + System.nanoTime(), passwordEncoder.encode(plain), "Storage",
                roles.findByName("STAFF").orElseThrow()));

        String stored = jdbc.queryForObject("select password_hash from users where id = ?", String.class, user.getId());

        assertThat(stored).startsWith("{bcrypt}$2").isNotEqualTo(plain).doesNotContain(plain);
        assertThat(passwordEncoder.matches(plain, stored)).isTrue();
    }
}
