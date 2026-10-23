package com.nexora.shared.idempotency;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.nexora.shared.error.FieldValidationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// An @Idempotent route without a valid Idempotency-Key is 400 VALIDATION_FAILED (field "Idempotency-Key").
// Ordered after PermissionCheckInterceptor, so a caller without the permission still gets 403 first.
@Configuration
public class IdempotencyKeyInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).order(1);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(Idempotent.class)) {
            String key = request.getHeader(IdempotencyFilter.HEADER);
            if (key == null || !key.matches(IdempotencyFilter.KEY_PATTERN)) {
                throw new FieldValidationException(IdempotencyFilter.HEADER,
                        "An Idempotency-Key header of 8 to 64 letters, digits or dashes is required.");
            }
        }
        return true;
    }
}
