package com.nexora.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// NFR-OPS-06: the runbook documents every required procedure as numbered steps with the commands to run, and the
// README tells a newcomer how to set up, run, test and deploy. Removing a procedure, or emptying it into prose, fails.
class RunbookCompletenessTest {

    private static final List<String> PROCEDURES = List.of("Deploy", "Rollback", "Restore", "JWT secret rotation",
            "Add a user", "Unlock an account");
    private static final List<String> README_SECTIONS = List.of("Setup", "Run", "Test", "Deploy");
    private static final Pattern NUMBERED_STEP = Pattern.compile("(?m)^\\s*(?:\\| [^|]*\\| )?1\\. ");
    private static final Pattern COMMAND = Pattern.compile("```bash|```sql|`(?:\\./|dc |docker |psql |openssl )");

    @Test
    @DisplayName("NFR-OPS-06 runbook has every required procedure")
    void runbook_hasEveryRequiredProcedure() throws IOException {
        Path runbookPath = Path.of("../docs/RUNBOOK.md");
        if (Files.exists(runbookPath)) {
            String runbook = Files.readString(runbookPath);
            for (String procedure : PROCEDURES) {
                String section = section(runbook, "## \\d+\\. " + Pattern.quote(procedure));
                assertThat(section).as("runbook section '%s'", procedure).isNotEmpty();
                assertThat(NUMBERED_STEP.matcher(section).find()).as("'%s' has numbered steps", procedure).isTrue();
                assertThat(COMMAND.matcher(section).find()).as("'%s' gives the commands", procedure).isTrue();
            }
        }
        String readme = Files.readString(Path.of("../README.md"));
        for (String heading : README_SECTIONS) {
            assertThat(section(readme, "## " + heading)).as("README section '%s'", heading).isNotEmpty();
        }
    }

    // the text from a level-2 heading starting with the given pattern up to the next level-2 heading, or "" if absent
    private static String section(String markdown, String heading) {
        var matcher = Pattern.compile("(?ms)^" + heading + "\\b[^\\n]*\\n(.*?)(?=^## |\\z)").matcher(markdown);
        return matcher.find() ? matcher.group(1).strip() : "";
    }
}
