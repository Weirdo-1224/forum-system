package org.example.forum_system.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ArchitectureRuleGuardTest {

    @Test
    void controllerRepositoryRuleDetectsSampleViolation() {
        var classes = new ClassFileImporter().importPackages(
                "org.example.forum_system.architecture.fixtures.sample");
        ArchRule rule = noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..");

        assertThrows(AssertionError.class, () -> rule.check(classes));
    }
}