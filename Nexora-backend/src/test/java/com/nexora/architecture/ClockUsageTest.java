package com.nexora.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import com.nexora.shared.config.ClockConfig;

// NFR-REL-06: business code reads time only from the injected Clock (Asia/Kolkata); timestamps are stored in UTC.
class ClockUsageTest {

    private static final Pattern NOW_WITHOUT_CLOCK = Pattern.compile(
            "\\b(Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime|YearMonth|Year)\\.now\\(\\s*\\)"
                    + "|System\\.currentTimeMillis\\(\\)|new\\s+(java\\.util\\.)?Date\\(\\s*\\)"
                    + "|Clock\\.system(UTC|DefaultZone)\\(\\)");

    @Test
    @DisplayName("NFR-REL-06 business code never calls now without clock")
    void businessCodeNeverCallsNowWithoutClock() {
        assertThat(SourceScan.find(NOW_WITHOUT_CLOCK, file -> true)).isEmpty();
        // the rule catches what it should
        assertThat(NOW_WITHOUT_CLOCK.matcher("LocalDate today = LocalDate.now();").find()).isTrue();
        assertThat(NOW_WITHOUT_CLOCK.matcher("LocalDate today = LocalDate.now(clock);").find()).isFalse();

        // the one clock is Asia/Kolkata; Hibernate stores timestamps in UTC (AC2)
        assertThat(new ClockConfig().clock().getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        assertThat(yaml.getObject().getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
    }
}
