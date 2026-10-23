package com.nexora.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.shared.error.ErrorCode;

// NFR-TEST-04: every stable error code (DOMAIN_RULES section 7) is asserted by at least one test, as the JSON
// "code" value or through ErrorCode.<NAME>.
class ErrorCodeCoverageTest {

    @Test
    @DisplayName("NFR-TEST-04 every error code is asserted by at least one test")
    void everyErrorCode_isAssertedByAtLeastOneTest() throws IOException {
        String tests;
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            tests = files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("ErrorCodeCoverageTest.java"))
                    .map(ErrorCodeCoverageTest::read).collect(Collectors.joining("\n"));
        }
        List<String> unasserted = Arrays.stream(ErrorCode.values()).map(Enum::name)
                .filter(code -> !tests.contains("\"" + code + "\"") && !tests.contains("ErrorCode." + code))
                .toList();
        assertThat(unasserted).as("error codes no test asserts").isEmpty();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
