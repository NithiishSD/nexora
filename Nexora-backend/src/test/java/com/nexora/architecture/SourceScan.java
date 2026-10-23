package com.nexora.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// Shared by the static checks: main source files with comments removed, so a rule explained in a comment
// ("never double") is not mistaken for code.
final class SourceScan {

    static final Path MAIN = Path.of("src/main/java/com/nexora");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private SourceScan() {
    }

    // "relative/path.java:line: text" for every code line matching the pattern, in files accepted by the filter.
    static List<String> find(Pattern pattern, Predicate<String> fileFilter) {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = MAIN.relativize(file).toString().replace('\\', '/');
                if (!fileFilter.test(name)) {
                    continue;
                }
                String[] lines = code(Files.readString(file)).split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (pattern.matcher(lines[i]).find()) {
                        found.add(name + ":" + (i + 1) + ": " + lines[i].strip());
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    // Comments replaced by blanks of the same line count, so line numbers stay right.
    static String code(String source) {
        String noBlocks = BLOCK_COMMENT.matcher(source).replaceAll(m -> "\n".repeat((int) m.group().chars()
                .filter(c -> c == '\n').count()));
        return LINE_COMMENT.matcher(noBlocks).replaceAll("");
    }
}
