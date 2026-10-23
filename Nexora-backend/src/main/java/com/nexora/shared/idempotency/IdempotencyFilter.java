package com.nexora.shared.idempotency;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.ServletRequestPathUtils;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.security.CurrentUser;
import com.nexora.shared.security.ProblemJsonWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

// Runs an @Idempotent POST inside ONE transaction that also holds its idempotency key (03-ARCHITECTURE 7.2):
// claim the key -> run the controller (the use case joins this transaction) -> store status, body and Location ->
// commit. A failed request (4xx/5xx) rolls back with its key, so its retry runs normally. A filter, not an
// interceptor, because the transaction must span the whole handler call and the response body must be captured
// before it is sent. Runs after Spring Security (default filter order), so the caller is known.
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED = "Idempotency-Replayed";
    static final String KEY_PATTERN = "^[A-Za-z0-9-]{8,64}$";

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMapping;
    private final IdempotencyService idempotency;
    private final TransactionTemplate tx;
    private final ProblemJsonWriter problems;

    // Qualified: Actuator registers a second RequestMappingHandlerMapping for its own endpoints.
    public IdempotencyFilter(@Qualifier("requestMappingHandlerMapping")
                             ObjectProvider<RequestMappingHandlerMapping> handlerMapping, IdempotencyService idempotency,
                             PlatformTransactionManager transactionManager, ProblemJsonWriter problems) {
        this.handlerMapping = handlerMapping;
        this.idempotency = idempotency;
        this.tx = new TransactionTemplate(transactionManager);
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        UUID userId = CurrentUser.idOrNull();
        // Missing or malformed keys are refused by IdempotencyKeyInterceptor, after the permission check (403 first).
        if (key == null || !key.matches(KEY_PATTERN) || userId == null || !isIdempotent(request)) {
            chain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readAllBytes();
        HttpServletRequest replayable = new CachedBodyRequest(request, body);
        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        Optional<StoredResponse> replay;
        try {
            replay = tx.execute(status -> {
                Optional<StoredResponse> stored = idempotency.claim(userId, key, request.getMethod(),
                        request.getRequestURI(), sha256(body));
                if (stored.isPresent()) {
                    return stored;
                }
                invoke(chain, replayable, captured);
                if (captured.getStatus() >= 200 && captured.getStatus() < 300) {
                    idempotency.complete(userId, key, new StoredResponse(captured.getStatus(),
                            new String(captured.getContentAsByteArray(), StandardCharsets.UTF_8),
                            captured.getHeader(HttpHeaders.LOCATION)));
                } else {
                    status.setRollbackOnly();                 // the key goes with the failed use case
                }
                return Optional.<StoredResponse>empty();
            });
        } catch (BusinessException e) {                         // DUPLICATE_REQUEST from claim
            problems.write(request, response, e.getCode(), e.getMessage());
            return;
        } catch (PessimisticLockingFailureException e) {         // the first request held the key for over 5 s
            problems.write(request, response, ErrorCode.CONCURRENT_MODIFICATION,
                    "The same request is still being processed; retry in a moment.");
            return;
        } catch (DataAccessException | TransactionException e) {
            // the database went away mid-request (restart, killed connection): nothing was committed and the key was
            // not kept, so the client's retry with the same key runs normally (NFR-REL-05)
            log.error("event=request_transaction_failed path={}", request.getRequestURI(), e);
            problems.write(request, response, ErrorCode.INTERNAL_ERROR,
                    "An unexpected error occurred. Please try again later.");
            return;
        }
        if (replay != null && replay.isPresent()) {
            StoredResponse stored = replay.get();
            response.setStatus(stored.status());
            response.setHeader(REPLAYED, "true");
            if (stored.location() != null) {
                response.setHeader(HttpHeaders.LOCATION, stored.location());
            }
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(stored.body().getBytes(StandardCharsets.UTF_8));
            return;
        }
        captured.copyBodyToResponse();                           // only after the commit succeeded
    }

    private boolean isIdempotent(HttpServletRequest request) {
        // Outside the DispatcherServlet the path must be parsed first (PathPatternParser); the dispatcher parses it
        // again for itself, so the cached copy is removed afterwards.
        ServletRequestPathUtils.parseAndCache(request);
        try {
            HandlerExecutionChain handler = handlerMapping.getObject().getHandler(request);
            return handler != null && handler.getHandler() instanceof HandlerMethod method
                    && method.hasMethodAnnotation(Idempotent.class);
        } catch (Exception e) {
            return false;                                        // no route: let MVC answer 404/405
        } finally {
            ServletRequestPathUtils.clearParsedRequestPath(request);
        }
    }

    private static void invoke(FilterChain chain, HttpServletRequest request, HttpServletResponse response) {
        try {
            chain.doFilter(request, response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ServletException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);                  // every JVM has SHA-256
        }
    }

    // The body was read once to hash it; the controller reads it again from this copy.
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("synchronous reads only");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
