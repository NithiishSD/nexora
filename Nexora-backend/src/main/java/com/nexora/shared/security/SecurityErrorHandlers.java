package com.nexora.shared.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.nexora.shared.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 401: the request has no valid token (missing, malformed, wrong signature, expired).
// 403: the token is valid but the user lacks the permission. (401 = who are you? 403 = I know you, but no.)
@Component
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemJsonWriter writer;

    public SecurityErrorHandlers(ProblemJsonWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        // The resource server reports an expired token with "Jwt expired at ..." in the message.
        boolean expired = exception.getMessage() != null
                && exception.getMessage().toLowerCase().contains("expired");
        writer.write(request, response,
                expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED,
                expired ? "The access token has expired." : "Authentication is required to access this resource.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        writer.write(request, response, ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action.");
    }
}
