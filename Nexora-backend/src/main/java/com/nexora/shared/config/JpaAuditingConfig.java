package com.nexora.shared.config;

import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.nexora.shared.security.AuthenticatedUserAuditor;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    // created_by comes from the logged-in user's JWT (empty outside a request).
    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return new AuthenticatedUserAuditor();
    }
}
