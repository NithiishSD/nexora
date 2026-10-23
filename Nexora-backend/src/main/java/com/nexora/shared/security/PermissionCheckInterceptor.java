package com.nexora.shared.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Checks a controller method's @PreAuthorize BEFORE Spring MVC reads and validates the request body.
// Method security alone runs the check only when the method is invoked, i.e. after @Valid: a STAFF user
// sending an invalid body to an owner-only endpoint would get 400 (and learn the validation rules)
// instead of 403. With this interceptor the answer is always 403 ACCESS_DENIED first.
// The @PreAuthorize annotation stays the single source of truth; method security still checks it again.
@Configuration
public class PermissionCheckInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    // Spring Security's own evaluator for @PreAuthorize expressions such as hasAuthority('PARTY_MANAGE').
    private final PreAuthorizeAuthorizationManager preAuthorize = new PreAuthorizeAuthorizationManager();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(PreAuthorize.class)) {
            // Evaluated without the method arguments: our expressions only look at the caller's authorities.
            AuthorizationResult result = preAuthorize.authorize(
                    SecurityContextHolder.getContext()::getAuthentication,
                    new SimpleMethodInvocation(method.getBean(), method.getMethod()));
            if (result != null && !result.isGranted()) {
                // handled by GlobalExceptionHandler -> 403 ACCESS_DENIED problem+json
                throw new AuthorizationDeniedException("Access Denied", result);
            }
        }
        return true;
    }
}
