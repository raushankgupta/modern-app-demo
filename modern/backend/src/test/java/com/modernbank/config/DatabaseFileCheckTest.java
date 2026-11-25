package com.modernbank.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.modernbank.ModernBankingApplication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

class DatabaseFileCheckTest {

    @Test
    void existingFileIsAccepted(@TempDir Path dir) throws Exception {
        Path db = Files.createFile(dir.resolve("ok.db"));
        assertDoesNotThrow(() -> new DatabaseFileCheck(db.toString()).afterPropertiesSet());
    }

    @Test
    void missingFileFailsWithClearMessage(@TempDir Path dir) {
        Path missing = dir.resolve("missing.db");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DatabaseFileCheck(missing.toString()).afterPropertiesSet());
        assertTrue(ex.getMessage().contains("SQLite database file not found"));
        assertTrue(ex.getMessage().contains("init.sh"));
    }

    @Test
    void applicationFailsToStartWhenDatabaseIsMissing(@TempDir Path dir) {
        SpringApplication app = new SpringApplication(ModernBankingApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        Throwable t = assertThrows(Throwable.class, () -> {
            try (ConfigurableApplicationContext ctx = app.run(
                    "--bank.db.path=" + dir.resolve("nope.db"), "--server.port=0")) {
                // not reached
            }
        });
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertTrue(root.getMessage().contains("SQLite database file not found"), root.getMessage());
    }
}
