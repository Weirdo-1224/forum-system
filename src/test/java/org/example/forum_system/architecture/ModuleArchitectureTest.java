package org.example.forum_system.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

@AnalyzeClasses(
        packages = "org.example.forum_system",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule api_types_must_not_expose_entities =
            noClasses().that().resideInAnyPackage("..controller..", "..dto..")
                    .should().dependOnClassesThat().resideInAPackage("..entity..");

    @ArchTest
    static final ArchRule business_modules_must_be_free_of_cycles =
            SlicesRuleDefinition.slices().matching("org.example.forum_system.(*)..")
                    .should().beFreeOfCycles();

    @ArchTest static final ArchRule auth_repository_is_internal = repositoryIsInternalTo("auth");
    @ArchTest static final ArchRule user_repository_is_internal = repositoryIsInternalTo("user");
    @ArchTest static final ArchRule post_repository_is_internal = repositoryIsInternalTo("post");
    @ArchTest static final ArchRule comment_repository_is_internal = repositoryIsInternalTo("comment");
    @ArchTest static final ArchRule reaction_repository_is_internal = repositoryIsInternalTo("reaction");
    @ArchTest static final ArchRule moderation_repository_is_internal = repositoryIsInternalTo("moderation");

    private static ArchRule repositoryIsInternalTo(String module) {
        return noClasses().that()
                .resideOutsideOfPackage("org.example.forum_system." + module + "..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.example.forum_system." + module + ".repository..");
    }
}