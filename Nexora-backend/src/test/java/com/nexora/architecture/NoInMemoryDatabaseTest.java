package com.nexora.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// NFR-TEST-01: tests run on PostgreSQL 17 in Testcontainers only; no in-memory database is even on the classpath.
class NoInMemoryDatabaseTest {

    @Test
    @DisplayName("NFR-TEST-01 test classpath has no h2 or hsqldb")
    void testClasspath_hasNoH2OrHsqldb() {
        List<String> found = List.of("org.h2.Driver", "org.hsqldb.jdbc.JDBCDriver", "org.apache.derby.jdbc.EmbeddedDriver")
                .stream().filter(NoInMemoryDatabaseTest::present).toList();
        assertThat(found).isEmpty();
        assertThat(present("org.postgresql.Driver")).as("the real driver is there").isTrue();
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, NoInMemoryDatabaseTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
