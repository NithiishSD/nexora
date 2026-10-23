package com.nexora.shared.observability;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// NFR-OBS-01: one log line per request with requestId (MDC), method, path, status, duration and user id, as structured
// key-value pairs (JSON fields with logging.structured.format.console in prod). It wraps Spring Security, so refused
// requests are logged too; the user id is captured inside the security chain by UserCapture. Never logs bodies,
// query strings or headers (09-SECURITY section 13).
@Component
@Order(RequestLoggingFilter.ORDER)
public class RequestLoggingFilter extends OncePerRequestFilter {

    static final int ORDER = -120;                           // after the request id, before rate limit and security
    static final String USER_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".userId";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");          // probes every few seconds: no noise
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            Object user = request.getAttribute(USER_ATTRIBUTE);
            log.atInfo()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000)
                    .addKeyValue("userId", user == null ? "-" : user)
                    .log("event=http_request");
        }
    }

    // Inside the security chain, right after the token is checked: remembers who made the request (for the request
    // line) and adds userId to the logging context of the rest of the request (09-SECURITY section 13).
    public static final class UserCapture extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
                chain.doFilter(request, response);
                return;
            }
            request.setAttribute(USER_ATTRIBUTE, auth.getName());
            MDC.put("userId", auth.getName());
            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove("userId");
            }
        }
    }
}
