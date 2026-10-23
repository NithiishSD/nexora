package com.nexora.shared.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// NFR-OPS-02, NFR-SEC-02: in production every required setting must have a value. Checked when the context starts,
// before any bean is created, so a missing one stops start-up with one plain sentence naming the environment variables
// to set. (Spring's binder keeps an unresolved ${DB_PASSWORD} as literal text, which otherwise surfaced much later as
// "Unable to determine Dialect".)
@Component
@Profile("prod")
public class RequiredConfigurationCheck implements BeanFactoryPostProcessor, EnvironmentAware {

    // required setting -> the environment variable application-prod.yml / application.yml reads it from
    static final Map<String, String> REQUIRED_IN_PROD = new LinkedHashMap<>();

    static {
        REQUIRED_IN_PROD.put("spring.datasource.url", "DB_URL");
        REQUIRED_IN_PROD.put("spring.datasource.username", "DB_USERNAME");
        REQUIRED_IN_PROD.put("spring.datasource.password", "DB_PASSWORD");
        REQUIRED_IN_PROD.put("spring.flyway.user", "FLYWAY_USER");
        REQUIRED_IN_PROD.put("spring.flyway.password", "FLYWAY_PASSWORD");
        REQUIRED_IN_PROD.put("nexora.security.jwt-secret", "JWT_SECRET");
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("'([A-Z][A-Z0-9_]*)'");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> missing = new ArrayList<>();
        REQUIRED_IN_PROD.forEach((property, variable) -> {
            try {
                if (environment.getProperty(property, "").isBlank()) {
                    missing.add(variable);
                }
            } catch (IllegalArgumentException unresolved) {       // "Could not resolve placeholder 'X' in value ..."
                Matcher m = PLACEHOLDER.matcher(String.valueOf(unresolved.getMessage()));
                missing.add(m.find() ? m.group(1) : variable);
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required configuration for the prod profile: "
                    + String.join(", ", missing) + ". Set them in deploy/.env.prod (see deploy/.env.prod.example).");
        }
    }
}
