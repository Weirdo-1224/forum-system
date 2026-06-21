package org.example.forum_system.architecture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ProjectStructureTest {

    private static final Path BASE_PACKAGE =
            Path.of("src", "main", "java", "org", "example", "forum_system");

    static Stream<String> businessModules() {
        return Stream.of("auth", "user", "post", "comment", "reaction", "moderation");
    }

    @ParameterizedTest
    @MethodSource("businessModules")
    void businessModuleContainsRequiredPackages(String module) {
        Path modulePath = BASE_PACKAGE.resolve(module);

        assertAll(
                () -> assertTrue(Files.isDirectory(modulePath.resolve("controller"))),
                () -> assertTrue(Files.isDirectory(modulePath.resolve("service"))),
                () -> assertTrue(Files.isDirectory(modulePath.resolve("repository"))),
                () -> assertTrue(Files.isDirectory(modulePath.resolve("entity"))),
                () -> assertTrue(Files.isDirectory(modulePath.resolve("dto"))));
    }

    @ParameterizedTest
    @MethodSource("commonPackages")
    void commonContainsPlannedInfrastructurePackages(String packageName) {
        assertTrue(Files.isDirectory(BASE_PACKAGE.resolve("common").resolve(packageName)));
    }

    static Stream<String> commonPackages() {
        return Stream.of("config", "error", "pagination", "security", "web");
    }
}