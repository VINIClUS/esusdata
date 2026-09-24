package esusdata.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * The three package rules worth enforcing by code (ADR 0013). Everything else about package
 * layout is convention: one folder per topic, no mandatory layers.
 *
 * <ul>
 *   <li>Tech Spec §1.5 / ENG-35: the indicator engine is pure Java — no JDBC, no HTTP, no PEC.</li>
 *   <li>ENG-29: the PostgreSQL driver is only reachable from the PEC connection code and the
 *       acquisition that reads through it — never from job, result, auth or web code.</li>
 *   <li>Source registry and auth never depend on the run pipeline; the dependency goes the other
 *       way.</li>
 * </ul>
 */
class ModuleBoundaryTest {

    private static final String BASE = "esusdata";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    @Test
    void indicatorCoreIsPureJava() {
        classes()
                .that()
                .resideInAnyPackage(BASE + ".indicator.model..", BASE + ".indicator.pack..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("java..", BASE + ".indicator..")
                .check(CLASSES);
    }

    @Test
    void postgresDriverOnlyReachableFromPecConnectionAndAcquisition() {
        noClasses()
                .that()
                .resideOutsideOfPackages(BASE + ".source.pec..", BASE + ".run.acquisition..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.postgresql..")
                .check(CLASSES);
    }

    @Test
    void sourceAndAuthDoNotDependOnRun() {
        noClasses()
                .that()
                .resideInAnyPackage(BASE + ".source..", BASE + ".auth..", BASE + ".indicator..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(BASE + ".run..")
                .check(CLASSES);
    }
}
