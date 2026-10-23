package com.nexora.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;

// ADR-025: a module uses another module only through its application services (and the plain value types those
// services speak: enums, records, exceptions). It never touches another module's repositories (infrastructure)
// or JPA entities (the persistent classes in domain). This test reads every source file under src/main/java
// and fails the build on any such reference, imported or written out in full.
// (Spring Modulith would need a new dependency, so a plain JUnit scan is used, as 03-ARCHITECTURE 3 allows.)
class ModuleBoundaryTest {

    private static final Path MAIN = Path.of("src/main/java");

    // com.nexora.<module>.(domain|infrastructure).<Class> anywhere in a file
    private static final Pattern REFERENCE =
            Pattern.compile("com\\.nexora\\.([a-z]+)\\.(domain|infrastructure)\\.([A-Z*][A-Za-z0-9]*)");

    @Test
    @DisplayName("FR-INV-01 modules only use other modules application layer")
    void modules_onlyUseOtherModulesApplicationLayer() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                violations.addAll(violations(moduleOf(file), file.toString(), Files.readString(file)));
            }
        }
        assertThat(violations).isEmpty();

        // the rule itself rejects what it should: procurement reaching into inventory's repository and entity
        String illegal = """
                import com.nexora.inventory.infrastructure.InventoryBalanceRepository;
                import com.nexora.inventory.domain.ProductType;
                class Bad { com.nexora.inventory.domain.InventoryBalance b; }
                """;
        assertThat(violations("procurement", "Bad.java", illegal)).hasSize(2)
                .anyMatch(v -> v.contains("InventoryBalanceRepository"))
                .anyMatch(v -> v.contains("InventoryBalance (entity)"));
        // inside its own module everything is allowed
        assertThat(violations("inventory", "Ok.java", illegal)).isEmpty();
    }

    static List<String> violations(String module, String file, String source) {
        List<String> found = new ArrayList<>();
        Matcher m = REFERENCE.matcher(source);
        while (m.find()) {
            String target = m.group(1);
            String layer = m.group(2);
            String type = m.group(3);
            if (target.equals(module) || target.equals("shared")) {
                continue;                                    // own module, or the shared kernel
            }
            if (layer.equals("infrastructure") || type.equals("*")) {
                found.add(file + " uses " + target + "." + layer + "." + type);
            } else if (isPersistent("com.nexora." + target + ".domain." + type)) {
                found.add(file + " uses " + target + ".domain." + type + " (entity)");
            }
        }
        return found;
    }

    // An @Entity / @Embeddable / @MappedSuperclass is persistence state that belongs to its module.
    private static boolean isPersistent(String className) {
        try {
            Class<?> type = Class.forName(className);
            return type.isAnnotationPresent(Entity.class) || type.isAnnotationPresent(Embeddable.class)
                    || type.isAnnotationPresent(MappedSuperclass.class);
        } catch (ClassNotFoundException e) {
            return true;                                     // unknown class: report it rather than guess
        }
    }

    // src/main/java/com/nexora/<module>/... -> <module>; files directly in com/nexora belong to no module.
    private static String moduleOf(Path file) {
        Path relative = MAIN.relativize(file);
        return relative.getNameCount() > 3 ? relative.getName(2).toString() : "";
    }
}
