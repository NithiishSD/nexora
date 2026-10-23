package com.nexora.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// NFR-DATA-01: money and weight are BigDecimal everywhere they are modelled or carried: no double or float in domain,
// application or API classes (DTOs included).
class ExactTypesTest {

    private static final Pattern FLOATING = Pattern.compile("\\b(double|float|Double|Float)\\b");

    @Test
    @DisplayName("NFR-DATA-01 no double or float in domain or application")
    void noDoubleOrFloatInDomainOrApplication() {
        assertThat(SourceScan.find(FLOATING, file -> file.contains("/domain/") || file.contains("/application/")
                || file.contains("/api/"))).isEmpty();
        assertThat(FLOATING.matcher("private double amount;").find()).isTrue();
        assertThat(FLOATING.matcher("// never double").find()).isTrue();             // comments are removed first
        assertThat(SourceScan.code("int a; // never double")).doesNotContain("double");
    }
}
