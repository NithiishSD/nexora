package com.nexora.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HttpCodeStatusMapper;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.nexora.support.AbstractApiTest;

class HealthApiTest extends AbstractApiTest {

    @Autowired
    HttpCodeStatusMapper statusMapper;

    @Autowired
    Environment environment;

    @Test
    @DisplayName("NFR-OBS-03 health is up only when database reachable")
    void health_isUpOnlyWhenDatabaseReachable() throws Exception {
        // with the database there: public health and readiness are UP, without a token
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        // readiness includes the database check, and that check is DOWN for an unreachable database...
        assertThat(environment.getProperty("management.endpoint.health.group.readiness.include")).contains("db");
        DataSourceHealthIndicator unreachable = new DataSourceHealthIndicator(
                new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/none", "x", "y"));
        assertThat(unreachable.health(false).getStatus()).isEqualTo(Status.DOWN);
        // ...which the health endpoint answers with 503 (AC1)
        assertThat(statusMapper.getStatusCode(Status.DOWN)).isEqualTo(503);
    }
}
