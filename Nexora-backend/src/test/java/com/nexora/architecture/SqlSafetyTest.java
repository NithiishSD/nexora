package com.nexora.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// NFR-SEC-10: values reach SQL only as bound parameters. In every class that talks to the database (JdbcTemplate,
// @Query, createQuery/createNativeQuery) a string literal joined with a non-constant expression is a finding, unless
// it is a reviewed internal fragment listed below (never anything a client sends).
class SqlSafetyTest {

    // file -> concatenated expressions reviewed as safe, with the reason
    private static final Map<String, Set<String>> REVIEWED = Map.of(
            // obligations(purpose) returns one of four constant CTEs chosen by the enum; table/column come from fixed
            // switch statements over that enum; purpose is only in an IllegalArgumentException message
            "finance/infrastructure/ObligationQueryRepository.java", Set.of("obligations(purpose)", "table", "column",
                    "purpose"),
            // where is "" or the constant " where pp.supplier_id = :supplier" (the id itself is a bound parameter)
            "procurement/infrastructure/PayableRepository.java", Set.of("where"));

    private static final Pattern DATABASE_CODE = Pattern.compile("JdbcTemplate|@Query\\(|createNativeQuery|createQuery");
    // "..." + expr + "..."   or   "..." + expr, / )
    private static final Pattern CONCATENATION = Pattern.compile(
            "(?:\"|\"\"\")\\s*\\+\\s*([^\"+;\\n]+?)\\s*\\+\\s*(?:\"|\\n\\s*\")|(?:\"|\"\"\")\\s*\\+\\s*([^\"+;\\n]+?)\\s*[,)]");
    private static final Pattern CONSTANT = Pattern.compile("[A-Z][A-Z0-9_]*(\\.formatted\\(.*\\))?");

    @Test
    @DisplayName("NFR-SEC-10 no native query concatenates input")
    void noNativeQueryConcatenatesInput() throws IOException {
        Path main = Path.of("src/main/java/com/nexora");
        List<String> findings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = main.relativize(file).toString().replace('\\', '/');
                findings.addAll(findings(name, Files.readString(file)));
            }
        }
        assertThat(findings).as("SQL built by concatenating a non-constant expression").isEmpty();

        // the scan itself catches an injection-shaped query
        assertThat(findings("x/infrastructure/Bad.java", """
                class Bad { JdbcTemplate jdbc;
                  List<?> find(String search) {
                    return jdbc.queryForList("select * from parties where name = '" + search + "'"); } }"""))
                .containsExactly("x/infrastructure/Bad.java: search");
    }

    static List<String> findings(String file, String source) {
        List<String> found = new ArrayList<>();
        if (!DATABASE_CODE.matcher(source).find()) {
            return found;
        }
        Matcher m = CONCATENATION.matcher(source);
        while (m.find()) {
            String expression = (m.group(1) != null ? m.group(1) : m.group(2)).strip();
            if (!CONSTANT.matcher(expression).matches()
                    && !REVIEWED.getOrDefault(file, Set.of()).contains(expression)) {
                found.add(file + ": " + expression);
            }
        }
        return found;
    }
}
