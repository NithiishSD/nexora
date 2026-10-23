package com.nexora.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

// @PreAuthorize("hasAuthority('PARTY_MANGE')") compiles fine but would lock everyone out (typo).
// This test finds every controller and checks that each authority it names is a real Permission.
class PreAuthorizePermissionTest {

    // The only expression shapes we allow: one permission, or "any logged-in user".
    private static final Pattern HAS_AUTHORITY = Pattern.compile("^hasAuthority\\('([A-Z_]+)'\\)$");
    private static final String AUTHENTICATED = "isAuthenticated()";

    @Test
    @DisplayName("FR-AUTH-09 every pre authorize names existing permission")
    void everyPreAuthorize_namesExistingPermission() throws Exception {
        Set<String> permissions = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        int checked = 0;

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.nexora")) {
            for (Method method : Class.forName(candidate.getBeanClassName()).getDeclaredMethods()) {
                PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
                if (annotation == null) {
                    continue;                        // missing annotations are caught by EndpointSecurityCoverageTest
                }
                checked++;
                String expression = annotation.value();
                Matcher matcher = HAS_AUTHORITY.matcher(expression);
                boolean ok = AUTHENTICATED.equals(expression)
                        || (matcher.matches() && permissions.contains(matcher.group(1)));
                if (!ok) {
                    problems.add(candidate.getBeanClassName() + "#" + method.getName() + ": " + expression);
                }
            }
        }

        assertThat(checked).as("controller methods with @PreAuthorize").isGreaterThan(0);
        assertThat(problems).as("@PreAuthorize expressions naming an unknown permission").isEmpty();
    }
}
