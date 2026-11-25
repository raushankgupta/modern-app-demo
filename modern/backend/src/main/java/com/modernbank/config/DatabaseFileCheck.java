package com.modernbank.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fails fast at startup with a clear message when the SQLite database file is missing.
 */
@Component
public class DatabaseFileCheck implements InitializingBean {

    private final String dbPath;

    public DatabaseFileCheck(@Value("${bank.db.path}") String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public void afterPropertiesSet() {
        Path file = Path.of(dbPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("SQLite database file not found: " + file
                    + ". Create it with modern/database/init.sh or set bank.db.path (BANK_DB_PATH).");
        }
    }
}
