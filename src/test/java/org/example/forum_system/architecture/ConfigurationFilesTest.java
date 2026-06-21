package org.example.forum_system.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigurationFilesTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void profileConfigurationFilesExist() {
        assertAll(
                () -> assertTrue(Files.isRegularFile(RESOURCES.resolve("application.yml"))),
                () -> assertTrue(Files.isRegularFile(RESOURCES.resolve("application-local.yml"))),
                () -> assertTrue(Files.isRegularFile(RESOURCES.resolve("application-test.yml"))),
                () -> assertTrue(Files.isRegularFile(RESOURCES.resolve("application-prod.yml"))));
    }

    @Test
    void productionSecretsComeFromEnvironment() throws IOException {
        Path productionFile = RESOURCES.resolve("application-prod.yml");
        assertTrue(Files.isRegularFile(productionFile));
        String production = Files.readString(productionFile);

        assertAll(
                () -> assertTrue(production.contains("${DB_PASSWORD}")),
                () -> assertTrue(production.contains("${JWT_SECRET}")),
                () -> assertFalse(production.contains("password: admin")),
                () -> assertFalse(production.contains("secret: changeme")));
    }

    @Test
    void localEnvironmentTemplatesExist() {
        assertAll(
                () -> assertTrue(Files.isRegularFile(Path.of("docker-compose.yml"))),
                () -> assertTrue(Files.isRegularFile(Path.of(".env.example"))));
    }
}