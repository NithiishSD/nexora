package com.nexora.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.identity.domain.User;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    // SELECT ... FOR UPDATE on the user row: parallel login attempts for one account wait for each other,
    // so every failed attempt is counted exactly once (no lost update of failed_login_attempts).
    // A plain query on purpose: PostgreSQL refuses FOR UPDATE together with an outer join fetch.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);
}
