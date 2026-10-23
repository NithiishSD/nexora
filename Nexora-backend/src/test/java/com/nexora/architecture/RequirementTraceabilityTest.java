package com.nexora.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// NFR-TEST-03: every MVP requirement ID of 02-REQUIREMENTS appears in the display name of at least one test, backend
// or desktop client. Two documented exceptions, both read from the committed docs so nothing is hard-coded here:
// - a requirement proven only by operational "check:" commands in 08-TESTING section 16 (run at the milestone gates);
// - a requirement whose task in 07-TASKS is still unticked: the build plan is not finished yet. Ticking that task
//   without the test makes this test fail, so the allowance shrinks to nothing by the end of the plan.
class RequirementTraceabilityTest {

    private static final Path DOCS = Path.of("../docs");
    private static final List<Path> TEST_TREES = List.of(Path.of("src/test/java"),
            Path.of("../nexora-desktop/src/test/kotlin"));
    private static final Pattern MVP_ROW = Pattern.compile("^\\| ((?:FR|NFR)-[A-Z]+-\\d+) \\|[^|]*\\|[^|]*\\| MVP \\|",
            Pattern.MULTILINE);
    private static final Pattern DISPLAY_NAME = Pattern.compile("@DisplayName\\(\"([^\"]*)\"\\)");
    private static final Pattern REQUIREMENT_ID = Pattern.compile("\\b(?:FR|NFR)-[A-Z]+-\\d+\\b");

    @Test
    @DisplayName("NFR-TEST-03 every mvp requirement id appears in atest display name")
    void everyMvpRequirementId_appearsInATestDisplayName() throws IOException {
        assumeTrue(Files.exists(DOCS.resolve("02-REQUIREMENTS.md")), "docs/02-REQUIREMENTS.md not present");
        Set<String> mvp = matches(MVP_ROW, Files.readString(DOCS.resolve("02-REQUIREMENTS.md")));
        assertThat(mvp).as("MVP requirements read from 02-REQUIREMENTS").hasSizeGreaterThan(200);
        Set<String> tested = testedIds();
        String plan = Files.readString(DOCS.resolve("07-TASKS.md"));
        String traceability = Files.readString(DOCS.resolve("08-TESTING.md"));

        List<String> missing = new ArrayList<>();
        for (String id : mvp) {
            if (!tested.contains(id) && !checkOnly(id, traceability) && unfinishedTasks(id, plan).isEmpty()) {
                missing.add(id);
            }
        }
        assertThat(missing).as("MVP requirements without a test (add the ID to a @DisplayName)").isEmpty();
    }

    // The ID's row in the 08 traceability table names no test method (Class#method), only check: commands.
    private static boolean checkOnly(String id, String traceability) {
        Matcher row = Pattern.compile("^\\| " + id + " \\|.*$", Pattern.MULTILINE).matcher(traceability);
        return row.find() && !row.group().contains("#");
    }

    // Unticked tasks of 07 whose requirement list names the ID.
    static List<String> unfinishedTasks(String id, String plan) {
        List<String> open = new ArrayList<>();
        for (String block : plan.split("\n### ")) {
            Matcher task = Pattern.compile("^\\[ ] (T\\d+\\.\\d+)").matcher(block);
            Matcher requirements = Pattern.compile("- \\*\\*Requirements:\\*\\*(.*)").matcher(block);
            if (task.find() && requirements.find()
                    && Pattern.compile("\\b" + id + "\\b").matcher(requirements.group(1)).find()) {
                open.add(task.group(1));
            }
        }
        return open;
    }

    private static Set<String> testedIds() throws IOException {
        Set<String> ids = new TreeSet<>();
        for (Path tree : TEST_TREES) {
            if (!Files.isDirectory(tree)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(tree)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java") || f.toString().endsWith(".kt"))
                        .toList()) {
                    Matcher name = DISPLAY_NAME.matcher(Files.readString(file));
                    while (name.find()) {
                        ids.addAll(matches(REQUIREMENT_ID, name.group(1)));
                    }
                }
            }
        }
        return ids;
    }

    private static Set<String> matches(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        Set<String> found = new TreeSet<>();
        while (m.find()) {
            found.add(m.groupCount() > 0 ? m.group(1) : m.group());
        }
        return found;
    }

    @Test
    @DisplayName("NFR-TEST-03 rejects a requirement without a test and names it")
    void rule_rejectsARequirementWithoutATest() {
        // AC2: the allowance only holds while a task naming the ID is unticked
        String plan = """
                ### [x] T1.01 - Done
                - **Requirements:** FR-AUTH-01
                ### [ ] T7.02 - Open
                - **Requirements:** NFR-SEC-07""";
        assertThat(unfinishedTasks("FR-AUTH-01", plan)).isEmpty();
        assertThat(unfinishedTasks("NFR-SEC-07", plan)).containsExactly("T7.02");
        assertThat(checkOnly("NFR-SEC-07", "| NFR-SEC-07 | Deps | MVP | check: `./gradlew dependencyCheckAnalyze` | T7.02 |"))
                .isTrue();
        assertThat(checkOnly("FR-AUTH-01", "| FR-AUTH-01 | Login | MVP | `LoginApiTest#login_ok` | T1.03 |")).isFalse();
    }
}
