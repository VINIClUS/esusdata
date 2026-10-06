package esusdata.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.GateChecks;
import esusdata.indicator.model.GateStatus;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.run.worker.RunExecutor;
import org.junit.jupiter.api.Test;

/**
 * The package rules worth enforcing by code (ADR 0013, ADR 0030). Everything else about package
 * layout is convention: one folder per topic, no mandatory layers.
 *
 * <ul>
 *   <li>Tech Spec §1.5 / ENG-35: the indicator engine is pure Java — no JDBC, no HTTP, no PEC.</li>
 *   <li>ENG-29 / ADR 0017: production code never reaches the PostgreSQL driver — every PEC read
 *       and the source diagnostic go through the execution plane; pgJDBC is test scope only.</li>
 *   <li>Source registry and auth never depend on the run pipeline; the dependency goes the other
 *       way.</li>
 * </ul>
 */
class ModuleBoundaryTest {

    private static final String BASE = "esusdata";

    @SuppressWarnings("PMD.LooseCoupling") // ArchUnit's own collection type, no interface to use
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

    /**
     * §2.4 / MET-20: the C2–C7 scores are already on the 0–100 scale; multiplying them by 100 again
     * is the error the Tech Spec warns about. Only C1, a real percentage, may call asPercentage.
     */
    @Test
    void scoredPacksNeverMultiplyByOneHundredAgain() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        BASE + ".indicator.pack.c2..",
                        BASE + ".indicator.pack.c3..",
                        BASE + ".indicator.pack.c4..",
                        BASE + ".indicator.pack.c5..",
                        BASE + ".indicator.pack.c6..",
                        BASE + ".indicator.pack.c7..",
                        BASE + ".indicator.pack.componente3..")
                .should()
                .callMethod(ExactRatio.class, "asPercentage")
                .check(CLASSES);
    }

    /**
     * ADR 0032: the release gates are applied in one place, the executor. A pack returns its outcome
     * ungated, and neither applies the gates nor reads the registry (the Nota Final receives its
     * {@code GateStatus} from the service, as data).
     */
    @Test
    void packsNeitherApplyTheReleaseGatesNorDependOnTheRegistry() {
        noClasses()
                .that()
                .resideInAPackage(BASE + ".indicator.pack..")
                .should()
                .callMethod(RuleOutcomes.class, "gate", GateStatus.class, IndicatorResult.class)
                .orShould()
                .callMethod(RuleOutcomes.class, "gate", GateStatus.class, RuleOutcome.class)
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(ReleaseGateRegistry.class.getName())
                .orShould()
                .dependOnClassesThat()
                .haveFullyQualifiedName(GateChecks.class.getName())
                .check(CLASSES);
    }

    /** ADR 0032: only the executor may ask a rule for an outcome; what it returns is not publishable. */
    @Test
    void onlyTheExecutorEvaluatesARule() {
        noClasses()
                .that()
                .doNotHaveFullyQualifiedName(RunExecutor.class.getName())
                .and()
                .resideOutsideOfPackage(BASE + ".indicator.pack..")
                .should()
                .callMethod(IndicatorRule.class, "evaluate", CanonicalDataset.class, EvaluationContext.class)
                .check(CLASSES);
    }

    @Test
    void productionCodeNeverReachesThePostgresDriver() {
        noClasses()
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
