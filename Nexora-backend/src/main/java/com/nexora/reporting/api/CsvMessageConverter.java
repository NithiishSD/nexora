package com.nexora.reporting.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tools.jackson.databind.json.JsonMapper;

// Writes a response as CSV when the client asked for it with format=csv (FR-RPT-16). Only then: a client without
// the parameter always gets JSON, and errors stay application/problem+json.
public class CsvMessageConverter extends AbstractHttpMessageConverter<Object> {

    public static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final JsonMapper jsonMapper;

    public CsvMessageConverter(JsonMapper jsonMapper) {
        super(StandardCharsets.UTF_8, TEXT_CSV);
        this.jsonMapper = jsonMapper;
    }

    public static boolean csvRequested(HttpServletRequest request) {
        return request != null && "csv".equalsIgnoreCase(request.getParameter("format"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return !ProblemDetail.class.isAssignableFrom(clazz);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return csvRequested(currentRequest()) && super.canWrite(clazz, mediaType);
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) {
        throw new HttpMessageNotReadableException("CSV is an export format only.", inputMessage);
    }

    // The whole CSV is built before anything is written, so a too-large export still becomes a clean 400.
    @Override
    protected void writeInternal(Object body, HttpOutputMessage output) throws IOException {
        byte[] csv = CsvExporter.toCsv(jsonMapper.valueToTree(body)).getBytes(StandardCharsets.UTF_8);
        output.getHeaders().setContentDisposition(ContentDisposition.attachment().filename(fileName()).build());
        output.getBody().write(csv);
    }

    // "<list>-<from>-<to>.csv": the last path segment plus the period when one was given.
    private static String fileName() {
        HttpServletRequest request = currentRequest();
        String path = request == null ? "export" : request.getRequestURI();
        StringBuilder name = new StringBuilder(path.substring(path.lastIndexOf('/') + 1));
        for (String param : new String[] {"from", "to"}) {
            String value = request == null ? null : request.getParameter(param);
            if (value != null && value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                name.append('-').append(value);
            }
        }
        return name.append(".csv").toString();
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
}
