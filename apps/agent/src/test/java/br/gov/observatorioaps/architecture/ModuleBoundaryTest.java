package br.gov.observatorioaps.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Tech Spec §1.5: "indicator-engine não depende de JDBC, HikariCP, HTTP ou leitura direta do PEC;
 * não executa SQL de usuário." ENG-35: "Teste de arquitetura impede JDBC/SQL na avaliação
 * metodológica; o motor funciona com fixture canônica sem banco PEC."
 *
 * <p>This is the one place these rules are enforced as code — not as a comment, not as a code
 * review convention. Two families of rules live here:
 *
 * <ul>
 *   <li><b>Module boundaries</b> (Tech Spec §1.5, ADR 0001): which module may know about which.</li>
 *   <li><b>Layer boundaries</b> (ADR 0009): inside a module, {@code domain} is pure Java,
 *       {@code application} orchestrates through ports, {@code infrastructure} holds adapters.</li>
 * </ul>
 */
class ModuleBoundaryTest {

    private static final String BASE = "br.gov.observatorioaps";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    /**
     * ADR 0009: application-layer classes that still embed SQL or reach an adapter directly.
     * Each entry is debt to be paid by pulling the SQL behind a port in the module's
     * {@code domain} package. Remove the name here once that happens — the rule then guards it.
     */
    private static final String HEXAGONAL_DEBT = String.join("|",
            "IndicatorRunExecutor", "SourceDiagnosticsService", "FailureClassifier",
            "AcquisitionGuard", "IdempotencyResolver", "CancellationRegistry", "JobWorker",
            "PublicationService", "ReproducibilityCheck",
            "SessionService", "BootstrapActivation", "LoginThrottle", "ScopeResolver",
            "UserProvisioning", "PasswordPolicy", "AuthenticationService", "AuthorizationVersionGuard",
            "PecSourceAcquisition");
    private static final String DEBT_REGEX = ".*\\.(" + HEXAGONAL_DEBT + ")(\\$.*)?";

    // ---------------------------------------------------------------- module boundaries (§1.5)

    @Test
    void indicatorEngineDoesNotDependOnJdbcOrSql() {
        noClasses()
                .that().resideInAPackage(BASE + ".indicatorengine..")
                .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..")
                .check(CLASSES);
    }

    @Test
    void indicatorEngineDoesNotDependOnHikariOrSpringJdbc() {
        noClasses()
                .that().resideInAPackage(BASE + ".indicatorengine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.zaxxer.hikari..", "org.springframework.jdbc..")
                .check(CLASSES);
    }

    @Test
    void indicatorEngineDoesNotDependOnPecAdapterOrSourceConnector() {
        noClasses()
                .that().resideInAPackage(BASE + ".indicatorengine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".pecadapter..", BASE + ".sourceconnector..")
                .check(CLASSES);
    }

    @Test
    void indicatorEngineDoesNotDependOnHttpClientOrSpringWeb() {
        noClasses()
                .that().resideInAPackage(BASE + ".indicatorengine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.net.http..", "org.springframework.web..")
                .check(CLASSES);
    }

    @Test
    void indicatorPacksDoNotDependOnJdbcSqlOrPecAdapter() {
        noClasses()
                .that().resideInAPackage(BASE + ".indicatorpacks..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.sql..", "javax.sql..",
                        "com.zaxxer.hikari..", "org.springframework.jdbc..",
                        BASE + ".pecadapter..", BASE + ".sourceconnector..")
                .check(CLASSES);
    }

    @Test
    void indicatorEngineAndPacksDoNotDependOnJobRunnerOrResultStore() {
        noClasses()
                .that().resideInAnyPackage(BASE + ".indicatorengine..", BASE + ".indicatorpacks..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".jobrunner..", BASE + ".resultstore..")
                .check(CLASSES);
    }

    /**
     * §1.12.1: result-store persists minimal evidence and results, but it is not the PEC
     * boundary — it must stay readable (history, evidence) with the PEC entirely disconnected,
     * exactly like extraction-store already is.
     */
    @Test
    void resultStoreDoesNotDependOnPecAdapterOrSourceConnector() {
        noClasses()
                .that().resideInAPackage(BASE + ".resultstore..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".pecadapter..", BASE + ".sourceconnector..")
                .check(CLASSES);
    }

    /**
     * §1.9.4 L365 revalidation at publish time goes through the {@code PublicationAuthorization}
     * seam defined in resultstore, implemented by identityaccess — never the reverse.
     */
    @Test
    void resultStoreDoesNotDependOnIdentityAccess() {
        noClasses()
                .that().resideInAPackage(BASE + ".resultstore..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".identityaccess..")
                .check(CLASSES);
    }

    @Test
    void resultStoreAndIndicatorPacksDoNotDependOnServletApiOrApiPackage() {
        noClasses()
                .that().resideInAnyPackage(BASE + ".resultstore..", BASE + ".indicatorpacks..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..", BASE + ".api..")
                .check(CLASSES);
    }

    /**
     * {@code api} is the HTTP boundary — it must never reach past jobrunner/resultstore into
     * the PEC-facing layers directly (§1.5). The one thing it may see from source-connector is
     * the <em>registry</em> of sources ({@code sourceconnector.domain}): configuration rows, never
     * a live connection, a budget or a secret resolver.
     */
    @Test
    void apiDoesNotDependOnPecAdapterOrSourceConnectorAdapters() {
        noClasses()
                .that().resideInAPackage(BASE + ".api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".pecadapter..",
                        BASE + ".sourceconnector.application..",
                        BASE + ".sourceconnector.infrastructure..")
                .check(CLASSES);
    }

    /** {@code identityaccess} stays framework-thin — no servlet type, ever. */
    @Test
    void identityAccessDoesNotDependOnServletApiOrPecAdapter() {
        noClasses()
                .that().resideInAPackage(BASE + ".identityaccess..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..",
                        BASE + ".pecadapter..",
                        BASE + ".sourceconnector..")
                .check(CLASSES);
    }

    @Test
    void indicatorEngineDoesNotDependOnServletApi() {
        noClasses()
                .that().resideInAPackage(BASE + ".indicatorengine..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.servlet..")
                .check(CLASSES);
    }

    /**
     * {@code jobrunner} stays the HTTP-free worker/executor layer; the SSE controller (fatia D)
     * must poll it through a plain method call, never by importing servlet types or reaching
     * back into {@code api}.
     */
    @Test
    void jobRunnerDoesNotDependOnServletApiOrApiPackage() {
        noClasses()
                .that().resideInAPackage(BASE + ".jobrunner..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..", BASE + ".api..")
                .check(CLASSES);
    }

    /** {@code platform} is shared plumbing (SQLite, process lock); it knows no module. */
    @Test
    void platformDoesNotDependOnAnyModule() {
        noClasses()
                .that().resideInAPackage(BASE + ".platform..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".api..", BASE + ".identityaccess..", BASE + ".sourceconnector..",
                        BASE + ".pecadapter..", BASE + ".extractionstore..",
                        BASE + ".indicatorengine..", BASE + ".indicatorpacks..",
                        BASE + ".jobrunner..", BASE + ".resultstore..")
                .check(CLASSES);
    }

    // ---------------------------------------------------------------- layer boundaries (ADR 0009)

    /** {@code domain} is plain Java: records, enums, pure rules and ports. No framework, no I/O driver. */
    @Test
    void domainLayersDependOnNothingButJavaAndOtherDomains() {
        noClasses()
                .that().resideInAPackage(BASE + "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "..application..", BASE + "..infrastructure..",
                        "java.sql..", "javax.sql..", "com.zaxxer..",
                        "org.springframework..", "jakarta..")
                .check(CLASSES);
    }

    /** {@code application} never sees HTTP: no servlet, no Spring MVC, no {@code api} package. */
    @Test
    void applicationLayersDoNotDependOnHttp() {
        noClasses()
                .that().resideInAPackage(BASE + "..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..", "org.springframework.web..",
                        "org.springframework.security.web..", BASE + ".api..")
                .check(CLASSES);
    }

    /**
     * {@code application} talks to persistence through ports in {@code domain}. Classes listed in
     * {@link #HEXAGONAL_DEBT} are the known exceptions; everything else is held to the rule.
     */
    @Test
    void applicationLayersUsePortsNotAdapters() {
        noClasses()
                .that().resideInAPackage(BASE + "..application..")
                .and().haveNameNotMatching(DEBT_REGEX)
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "..infrastructure..",
                        "java.sql..", "javax.sql..", "com.zaxxer..", "org.springframework.jdbc..")
                .check(CLASSES);
    }

    /** No module's {@code infrastructure} is reached from another module's {@code domain}. */
    @Test
    void infrastructureIsNotReachedFromOtherModulesDomain() {
        noClasses()
                .that().resideInAPackage(BASE + "..domain..")
                .should().dependOnClassesThat().resideInAPackage(BASE + "..infrastructure..")
                .check(CLASSES);
    }
}
