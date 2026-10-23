package com.nexora.shared.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.nexora.shared.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

// Security filters run BEFORE Spring MVC, so @RestControllerAdvice never sees their errors.
// This writes the same problem+json body by hand for 401 and 403.
@Component
public class ProblemJsonWriter {

    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ProblemJsonWriter(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
            throws IOException {
        write(request, response, code, detail, null);
    }

    // With field errors, the same "errors" list the MVC handler gives for VALIDATION_FAILED.
    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail,
                      List<Map<String, String>> errors) throws IOException {
        HttpStatus status = code.status();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code.name());
        body.put("timestamp", Instant.now(clock).toString());
        if (errors != null) {
            body.put("errors", errors);
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
