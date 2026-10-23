package com.nexora.shared.security;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nexora.shared.error.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// NFR-SEC-11, ADR-034: sliding one-minute windows kept in memory. /api/v1/auth/** counts per client IP (10 a minute);
// every other API path counts per user, the subject of a valid token (per IP without one), 300 a minute. Over the
// limit: 429 RATE_LIMITED with Retry-After. Runs before Spring Security so a flood never reaches token checks twice.
// ponytail: per-instance memory; a shared store only if a second instance is ever deployed (09-SECURITY section 6).
@Component
@Order(RateLimitFilter.ORDER)
public class RateLimitFilter extends OncePerRequestFilter {

    static final int ORDER = -110;                                       // before the size filter and Spring Security
    private static final long WINDOW_MILLIS = 60_000;
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final boolean enabled;
    private final int authPerMinute;
    private final int apiPerMinute;
    private final JwtDecoder jwtDecoder;
    private final ProblemJsonWriter writer;
    private final Clock clock;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${nexora.rate-limit.enabled:true}") boolean enabled,
                           @Value("${nexora.rate-limit.auth-per-minute:10}") int authPerMinute,
                           @Value("${nexora.rate-limit.api-per-minute:300}") int apiPerMinute,
                           JwtDecoder jwtDecoder, ProblemJsonWriter writer, Clock clock) {
        this.enabled = enabled;
        this.authPerMinute = authPerMinute;
        this.apiPerMinute = apiPerMinute;
        this.jwtDecoder = jwtDecoder;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean auth = request.getRequestURI().startsWith("/api/v1/auth/")
                || request.getRequestURI().startsWith("/api/v1/setup/");
        String key = auth ? "ip:" + request.getRemoteAddr() : clientKey(request);
        long retryAfterMillis = admit(key, auth ? authPerMinute : apiPerMinute, clock.millis());
        if (retryAfterMillis > 0) {
            long seconds = Math.max(1, (retryAfterMillis + 999) / 1000);
            log.warn("event=rate_limited client={} path={}", key, request.getRequestURI());
            response.setHeader("Retry-After", Long.toString(seconds));
            writer.write(request, response, ErrorCode.RATE_LIMITED,
                    "Too many requests. Try again in " + seconds + " seconds.");
            return;
        }
        chain.doFilter(request, response);
    }

    // 0 when the request is admitted (and counted), otherwise the milliseconds until the oldest request leaves the window.
    long admit(String key, int limit, long now) {
        Deque<Long> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() <= now - WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() < limit) {
                window.addLast(now);
                return 0;
            }
            return window.peekFirst() + WINDOW_MILLIS - now;
        }
    }

    // The user of a token that verifies (signature and expiry); anyone else is counted by IP.
    private String clientKey(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                return "user:" + jwtDecoder.decode(header.substring(7)).getSubject();
            } catch (JwtException e) {
                // an invalid token is answered 401 by Spring Security; it still counts against the IP
            }
        }
        return "ip:" + request.getRemoteAddr();
    }

    // Forget clients idle for a whole window, so the map does not grow with every IP ever seen.
    @Scheduled(fixedDelay = WINDOW_MILLIS)
    void evictIdle() {
        long cutoff = clock.millis() - WINDOW_MILLIS;
        windows.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() <= cutoff;
            }
        });
    }
}
