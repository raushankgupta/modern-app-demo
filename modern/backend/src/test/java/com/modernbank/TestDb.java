package com.modernbank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.test.context.DynamicPropertyRegistry;

/** Test helper: points bank.db.path at a throw-away temp file (never the real modern or legacy DB). */
public final class TestDb {

    private TestDb() {
    }

    public static void useTempFile(DynamicPropertyRegistry registry) {
        try {
            Path file = Files.createTempFile("modernbank-test", ".db");
            file.toFile().deleteOnExit();
            registry.add("bank.db.path", file::toString);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
