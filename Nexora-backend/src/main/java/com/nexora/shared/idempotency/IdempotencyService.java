package com.nexora.shared.idempotency;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

// The idempotency_keys table (04-DATA-MODEL 8.4). Plain SQL: the claim needs INSERT ... ON CONFLICT, whose wait on a
// concurrent duplicate is exactly the behaviour we want (03-ARCHITECTURE 6 and 7.2).
@Service
class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    static final Duration RETENTION = Duration.ofHours(48);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    IdempotencyService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // First lock of the use case (ADR-028). Empty = this request owns the key and must run; present = a stored
    // response to replay. A concurrent duplicate blocks on the unique index until the first request's transaction
    // ends: committed -> replay its response; rolled back -> this request runs. Same key, other request -> 409.
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<StoredResponse> claim(UUID userId, String key, String method, String path, String hash) {
        Instant now = clock.instant();
        jdbc.update("delete from idempotency_keys where user_id = ? and idempotency_key = ? and expires_at < ?",
                userId, key, Timestamp.from(now));
        List<UUID> inserted = jdbc.queryForList("insert into idempotency_keys (idempotency_key, user_id, request_method,"
                + " request_path, request_hash, response_status, response_body, created_at, expires_at)"
                + " values (?, ?, ?, ?, ?, 0, '', ?, ?) on conflict (user_id, idempotency_key) do nothing returning id",
                UUID.class, key, userId, method, path, hash, Timestamp.from(now), Timestamp.from(now.plus(RETENTION)));
        if (!inserted.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(jdbc.queryForObject("select request_method, request_path, request_hash, response_status,"
                + " response_body, response_location from idempotency_keys where user_id = ? and idempotency_key = ?",
                (rs, n) -> {
                    if (!rs.getString(1).equals(method) || !rs.getString(2).equals(path) || !rs.getString(3).equals(hash)) {
                        throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "This Idempotency-Key was already used"
                                + " for a different request; send a new key for a new request.");
                    }
                    return new StoredResponse(rs.getInt(4), rs.getString(5), rs.getString(6));
                }, userId, key));
    }

    // Stores the answer in the same transaction as the use case, just before it commits.
    @Transactional(propagation = Propagation.MANDATORY)
    void complete(UUID userId, String key, StoredResponse response) {
        jdbc.update("update idempotency_keys set response_status = ?, response_body = ?, response_location = ?"
                + " where user_id = ? and idempotency_key = ?", response.status(), response.body(), response.location(),
                userId, key);
    }

    // Daily clean-up of keys older than 48 hours (03-ARCHITECTURE 7.2).
    @Scheduled(cron = "${nexora.idempotency.cleanup-cron:0 15 3 * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void deleteExpired() {
        int deleted = jdbc.update("delete from idempotency_keys where expires_at < ?", Timestamp.from(clock.instant()));
        log.info("event=idempotency_keys_expired deleted={}", deleted);
    }
}
