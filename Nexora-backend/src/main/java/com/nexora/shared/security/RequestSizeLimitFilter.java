package com.nexora.shared.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nexora.shared.error.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

// Body size limits before anything parses the body (NFR-SEC-03, 09-SECURITY section 5): 1 MiB for JSON, 10 MiB plus
// form overhead for a document upload. A declared Content-Length above the limit is refused at once with 400
// VALIDATION_FAILED (the code FR-DOC-01 AC2 asks for); a body without a length (chunked) is counted while it is read
// and stops at the limit, so it can never fill the memory. Runs before the rate limiter's neighbour, Spring Security.
@Component
@Order(RequestSizeLimitFilter.ORDER)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final int ORDER = -105;                                       // Spring Security's chain is -100
    static final long JSON_LIMIT = 1024 * 1024;
    static final long DOCUMENT_LIMIT = 10L * 1024 * 1024 + 64 * 1024;    // the 10 MiB file + multipart overhead

    private final ProblemJsonWriter writer;

    public RequestSizeLimitFilter(ProblemJsonWriter writer) {
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean document = request.getRequestURI().startsWith("/api/v1/documents");
        long limit = document ? DOCUMENT_LIMIT : JSON_LIMIT;
        if (request.getContentLengthLong() > limit) {
            String field = document ? "file" : "body";
            String message = document ? "A document can be at most 10 MB." : "A request body can be at most 1 MB.";
            writer.write(request, response, ErrorCode.VALIDATION_FAILED, "Request validation failed.",
                    List.of(Map.of("field", field, "message", message)));
            return;
        }
        chain.doFilter(request.getContentLengthLong() < 0 && !document ? new Limited(request, limit) : request,
                response);
    }

    // The body of a request without Content-Length, cut off at the limit: reading past it fails like a broken body.
    private static final class Limited extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;

        Limited(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                ServletInputStream in = super.getInputStream();
                stream = new ServletInputStream() {
                    private long read;

                    @Override
                    public int read() throws IOException {
                        int b = in.read();
                        if (b >= 0 && ++read > limit) {
                            throw new IOException("Request body larger than " + limit + " bytes.");
                        }
                        return b;
                    }

                    @Override
                    public boolean isFinished() {
                        return in.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return in.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        in.setReadListener(listener);
                    }
                };
            }
            return stream;
        }
    }
}
