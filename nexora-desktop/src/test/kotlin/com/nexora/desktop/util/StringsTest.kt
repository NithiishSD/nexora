package com.nexora.desktop.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

class StringsTest {

    @Test
    @DisplayName("every text key used in the code exists in strings properties")
    fun everyKeyUsedInCode_existsInStringsFile() {
        // Finds s("some.key" in every main source file; Gradle runs tests from the project directory.
        val usage = Regex("""\bs\("([A-Za-z0-9_.\-]+)"""")
        val keys = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .flatMap { file -> usage.findAll(file.readText()).map { it.groupValues[1] } }
            .toSortedSet()
        val missing = keys.filterNot { Strings.has(it) }
        assertEquals(emptyList<String>(), missing, "keys missing from strings.properties")
    }

    @Test
    @DisplayName("arguments are placed into the text")
    fun arguments_areFilledIn() {
        assertEquals("Something went wrong (X). Please try again or contact support.", s("error.unknown", "X"))
    }
}
