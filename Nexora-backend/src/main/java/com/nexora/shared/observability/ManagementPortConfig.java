package com.nexora.shared.observability;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// NFR-OBS-04: a second HTTP port (8081) for metrics, never published outside the compose network. Health and info stay
// on the main port, where Caddy, Docker and the desktop client call them; SecurityConfig serves /actuator/prometheus
// and /actuator/metrics only to requests that arrived on this port.
@Configuration
public class ManagementPortConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> managementConnector(
            @Value("${nexora.management.port:8081}") int port) {
        return factory -> {
            if (port > 0 && port != factory.getPort()) {
                Connector connector = new Connector();
                connector.setPort(port);
                factory.addAdditionalConnectors(connector);
            }
        };
    }
}
