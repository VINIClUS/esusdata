package br.gov.observatorioaps.architecture;

import br.gov.observatorioaps.execution.adapter.out.pec.PecDataSourceFactory;
import br.gov.observatorioaps.execution.adapter.out.pec.PecSourceConnection;
import br.gov.observatorioaps.execution.application.SourceDiagnosticsService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ADR 0012: the codebase is organized into five capabilities (access, sources, execution,
 * indicators, results) plus platform, each internally layered {@code domain}/{@code
 * application}/{@code adapter/in}/{@code adapter/out} (ADR 0009's layer semantics, carried over —
 * see ADR 0012 for what changed and why). This is the one place these rules are enforced as code
 * — not as a comment, not as a code review convention.
 *
 * <ul>
 *   <li><b>Layer rules</b>: what {@code domain}/{@code application}/{@code adapter} may see,
 *       inside any capability.</li>
 *   <li><b>Capability rules</b>: what each capability may depend on.</li>
 *   <li><b>Cycle rule</b>: no capability/layer slice may depend on another that depends back.</li>
 * </ul>
 *
 * <p>Capability root packages ({@code access.IdentityAccessConfig}, {@code
 * execution.JobRunnerConfig}, {@code execution.AcquisitionConfig}, {@code sources.SourcesConfig},
 * {@code results.ResultsConfig}, and their {@code @ConfigurationProperties} records) hold only
 * Spring {@code @Configuration} wiring and are deliberately outside every rule below — same
 * precedent ADR 0009 set for {@code infrastructure/spring}: wiring is where the object graph for
 * the whole process gets assembled, so it is the one place allowed to depend on anything.
 */
class ModuleBoundaryTest {

    private static final String BASE = "br.gov.observatorioaps";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    /**
     * {@code application} classes that legitimately classify a persistence failure (SQLState,
     * {@code DataAccessException}) or, for the three still embedding raw SQL, have not yet had
     * that SQL pulled behind a domain port. Each reason is why the class remains here, not an
     * invitation to leave it here — the goal is to keep shrinking this list, per ADR 0009/ADR
     * 0012's "removing a name here is the criterion for having paid the debt."
     */
    private static final String APPLICATION_EMBEDS_SQL = String.join("|",
            // Embedded SQL via JdbcTemplate — not yet pulled behind a domain port.
            "BootstrapActivation", "ScopeResolver", "UserProvisioning", "SessionService",
            "LoginThrottle", "AuthorizationVersionGuard",
            // Publication is one SQLite transaction spanning results and the job row (ENG-23);
            // splitting it behind a port would either lose the single-transaction guarantee or
            // require a distributed-transaction port, neither of which the SQLite-only design
            // (ADR 0001) has room for.
            "PublicationService",
            // Classify a persistence failure by inspecting SQLState/DataAccessException — this is
            // reading a JDBC-shaped signal, not issuing SQL.
            "JobWorker", "FailureClassifier", "IdempotencyResolver",
            // Same failure-classification reasoning as above; also the one class that legitimately
            // reaches an adapter directly (see APPLICATION_REACHES_ADAPTER).
            "SourceDiagnosticsService");
    private static final String SQL_DEBT_REGEX = ".*\\.(" + APPLICATION_EMBEDS_SQL + ")(\\$.*)?";

    /**
     * {@code /sources/{id}/test} (§1.10) must reuse the exact {@code AllowedDestinations}/{@code
     * PecDataSourceFactory} the live acquisition path uses — a diagnostic through a second,
     * looser code path would prove nothing. That reuse is the one place {@code
     * execution.application} legitimately instantiates an {@code execution.adapter.out.pec} type
     * instead of going through {@link br.gov.observatorioaps.execution.domain.acquisition.AcquisitionPort}.
     */
    private static final String APPLICATION_REACHES_ADAPTER = "SourceDiagnosticsService";
    private static final String ADAPTER_DEBT_REGEX = ".*\\.(" + APPLICATION_REACHES_ADAPTER + ")(\\$.*)?";

    /**
     * The published result and the OpenAPI {@code Scope} shape are the same DTO
     * ({@code access.adapter.in.http.ScopeResponse}) on both the auth and the results surface —
     * {@code results.adapter.in.http.ResultController}/{@code ResultResponse} are the one place
     * {@code results}'s HTTP adapter reaches into {@code access}'s HTTP adapter rather than
     * {@code access}'s domain or application layer. Follow-up: give {@code ScopeResponse} a home
     * that isn't inside either capability's adapter package.
     */
    private static final String CROSS_ADAPTER_DEBT_REGEX = ".*\\.(ResultController|ResultResponse)(\\$.*)?";

    // ---------------------------------------------------------------------------- layer rules

    /** {@code domain} is plain Java: records, enums, pure rules and ports — no framework, no I/O driver. */
    @Test
    void domainLayersDependOnNothingButJavaAndOtherDomains() {
        noClasses()
                .that().resideInAPackage(BASE + "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + "..application..", BASE + "..adapter..",
                        "org.springframework..", "jakarta..",
                        "java.sql..", "javax.sql..", "com.zaxxer..")
                .check(CLASSES);
    }

    /** {@code application} never sees HTTP: no servlet, no Spring MVC, no Spring Security web. */
    @Test
    void applicationLayersDoNotDependOnHttp() {
        noClasses()
                .that().resideInAPackage(BASE + "..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..", "org.springframework.web..",
                        "org.springframework.security.web..")
                .check(CLASSES);
    }

    /**
     * {@code application} talks to persistence and to the PEC through ports in {@code domain},
     * never by instantiating an {@code adapter} type — {@link #APPLICATION_REACHES_ADAPTER} is
     * the one documented exception.
     */
    @Test
    void applicationLayersDoNotReachAdaptersDirectly() {
        noClasses()
                .that().resideInAPackage(BASE + "..application..")
                .and().haveNameNotMatching(ADAPTER_DEBT_REGEX)
                .should().dependOnClassesThat().resideInAPackage(BASE + "..adapter..")
                .check(CLASSES);
    }

    /**
     * {@code application} talks to persistence through ports in {@code domain}; {@link
     * #APPLICATION_EMBEDS_SQL} is the declared, reasoned exception list.
     */
    @Test
    void applicationLayersDoNotEmbedSql() {
        noClasses()
                .that().resideInAPackage(BASE + "..application..")
                .and().haveNameNotMatching(SQL_DEBT_REGEX)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.sql..", "javax.sql..", "com.zaxxer..",
                        "org.springframework.jdbc..", "org.springframework.dao..")
                .check(CLASSES);
    }

    /** HTTP adapters (in) must not reach persistence/PEC/process adapters (out) directly — only through application/domain. */
    @Test
    void adapterInDoesNotDependOnAdapterOut() {
        noClasses()
                .that().resideInAPackage(BASE + "..adapter.in..")
                .should().dependOnClassesThat().resideInAPackage(BASE + "..adapter.out..")
                .check(CLASSES);
    }

    // ----------------------------------------------------------------------- adapter privacy

    /**
     * A capability's {@code adapter} package is private to that capability — everything another
     * capability needs is exposed through {@code domain} or {@code application}. One rule per
     * capability keeps each violation's message naming the actual capability, not a generic
     * wildcard match.
     */
    @Test
    void onlyAccessDependsOnAccessAdapters() {
        noClasses()
                .that().resideOutsideOfPackage(BASE + ".access..")
                .and().haveNameNotMatching(CROSS_ADAPTER_DEBT_REGEX)
                .should().dependOnClassesThat().resideInAPackage(BASE + ".access.adapter..")
                .check(CLASSES);
    }

    @Test
    void onlySourcesDependsOnSourcesAdapters() {
        noClasses()
                .that().resideOutsideOfPackage(BASE + ".sources..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".sources.adapter..")
                .check(CLASSES);
    }

    @Test
    void onlyExecutionDependsOnExecutionAdapters() {
        noClasses()
                .that().resideOutsideOfPackage(BASE + ".execution..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".execution.adapter..")
                .check(CLASSES);
    }

    @Test
    void onlyIndicatorsDependsOnIndicatorsAdapters() {
        noClasses()
                .that().resideOutsideOfPackage(BASE + ".indicators..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".indicators.adapter..")
                .check(CLASSES);
    }

    @Test
    void onlyResultsDependsOnResultsAdapters() {
        noClasses()
                .that().resideOutsideOfPackage(BASE + ".results..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".results.adapter..")
                .check(CLASSES);
    }

    /** The PEC driver is the acquisition adapter's business, never anyone else's. */
    @Test
    void postgresDriverIsReachableOnlyFromExecutionPecAdapter() {
        noClasses()
                .that().resideOutsideOfPackage(BASE + ".execution.adapter.out.pec..")
                .should().dependOnClassesThat().resideInAPackage("org.postgresql..")
                .check(CLASSES);
    }

    // ------------------------------------------------------------------------ capability rules

    /**
     * §1.5/Tech Spec: the indicator engine stays pure — no JDBC, no PEC, no HTTP, no other
     * capability. The strict form (core only, whitelisting {@code java..}/{@code indicators..})
     * catches anything at all outside those two; the named form below additionally holds the HTTP
     * adapter to the same "no PEC/JDBC/other capability" bar, since Spring/Jakarta are the only
     * things {@code indicators.adapter.in.http} is allowed that the core is not.
     */
    @Test
    void indicatorsCoreIsPureJava() {
        noClasses()
                .that().resideInAPackage(BASE + ".indicators..")
                .and().resideOutsideOfPackage(BASE + ".indicators.adapter..")
                .should().dependOnClassesThat().resideOutsideOfPackages("java..", BASE + ".indicators..")
                .check(CLASSES);
    }

    @Test
    void indicatorsNeverDependsOnOtherCapabilitiesOrPecOrJdbc() {
        noClasses()
                .that().resideInAPackage(BASE + ".indicators..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".execution..", BASE + ".results..", BASE + ".sources..", BASE + ".access..",
                        "java.sql..", "javax.sql..", "jakarta.servlet..", "java.net.http..")
                .check(CLASSES);
    }

    /**
     * §1.12.1: {@code results} stays readable — history, evidence, publication — with the PEC
     * entirely disconnected, exactly like the old extraction-store already was. Scoped to {@code
     * results.domain}/{@code results.application}/{@code results.adapter.out} (persistence, which
     * has no legitimate reason to see another capability): {@code results.adapter.in.http} is
     * deliberately excluded because it legitimately uses {@code access.domain}/{@code
     * access.application} for the same auth check every other capability's HTTP adapter makes,
     * and {@code access.adapter.in.http.ScopeResponse} for the one documented DTO reuse (see
     * {@link #CROSS_ADAPTER_DEBT_REGEX}).
     */
    @Test
    void resultsCoreDoesNotDependOnExecutionCoreOrAccessOrSources() {
        noClasses()
                .that().resideInAnyPackage(
                        BASE + ".results.domain..", BASE + ".results.application..",
                        BASE + ".results.adapter.out..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".execution.application..", BASE + ".execution.adapter..",
                        BASE + ".execution.domain.acquisition..",
                        BASE + ".access..", BASE + ".sources..")
                .check(CLASSES);
    }

    /** {@code sources}'s own domain (the source registry) depends on no other capability. */
    @Test
    void sourcesDomainDependsOnNoCapability() {
        noClasses()
                .that().resideInAPackage(BASE + ".sources.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".access..", BASE + ".execution..", BASE + ".indicators..", BASE + ".results..")
                .check(CLASSES);
    }

    /**
     * §1.9.4 L365: {@code access} may implement {@code results.domain.PublicationAuthorization}
     * (that seam is how revalidation reaches publication without {@code results} depending on
     * {@code access} — see {@link #resultsCoreDoesNotDependOnExecutionCoreOrAccessOrSources}) but
     * otherwise knows no other capability.
     */
    @Test
    void accessCoreDependsOnNoCapabilityExceptResultsDomain() {
        noClasses()
                .that().resideInAnyPackage(BASE + ".access.domain..", BASE + ".access.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".sources..", BASE + ".execution..", BASE + ".indicators..",
                        BASE + ".results.application..", BASE + ".results.adapter..")
                .check(CLASSES);
    }

    /** {@code platform.sqlite}/{@code platform.lock} are shared plumbing — they know no capability. */
    @Test
    void platformSqliteAndLockDependOnNoCapability() {
        noClasses()
                .that().resideInAnyPackage(BASE + ".platform.sqlite..", BASE + ".platform.lock..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".access..", BASE + ".sources..", BASE + ".execution..",
                        BASE + ".indicators..", BASE + ".results..")
                .check(CLASSES);
    }

    /**
     * {@code platform.web} (the global {@code ScopeCheckedAdvice}, {@code ReadyController}, the
     * {@code ApiError} family) is the one sanctioned platform → capability edge — the process-wide
     * exception mapping has to name domain exceptions from every capability. It may see {@code
     * domain}/{@code application}, never an {@code adapter} — the global advice does not become a
     * dumping ground that also knows HTTP DTOs or persistence.
     */
    @Test
    void platformWebOnlyReachesCapabilityDomainOrApplication() {
        noClasses()
                .that().resideInAPackage(BASE + ".platform.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".access.adapter..", BASE + ".sources.adapter..",
                        BASE + ".execution.adapter..", BASE + ".indicators.adapter..",
                        BASE + ".results.adapter..")
                .check(CLASSES);
    }

    // --------------------------------------------------------------------------------- cycles

    /**
     * Capability × top-level-layer slices (e.g. {@code execution.domain}, {@code
     * execution.application}, {@code results.domain}) must not form a cycle. Capability root
     * wiring classes (one path segment below {@code BASE}, e.g. {@code execution.JobRunnerConfig})
     * do not match this slice pattern and are excluded — consistent with every rule above treating
     * wiring as the one place allowed to depend on anything.
     *
     * <p><b>This does not prove the whole capability graph is a DAG</b> — it proves the finer-grained
     * claim that no single {@code capability.layer} slice pair depends on each other both ways.
     * {@code execution} and {@code results} legitimately depend on each other at the coarser,
     * whole-capability level ({@code execution.application.IndicatorRunExecutor} calls {@code
     * results.application.PublicationService}; {@code results.application.PublicationService}
     * closes the job through {@code execution.domain.job.JobRepository} in the same SQLite
     * transaction, ENG-23) without those two edges ever landing on the same slice pair — see ADR
     * 0012's "Ciclos" section for why that specific relationship is accepted rather than removed.
     *
     * <p>{@code execution.adapter} and {@code execution.application} would otherwise form a
     * 2-slice cycle solely because both {@code adapter.in} and {@code adapter.out} collapse into
     * one {@code execution.adapter} slice at this granularity: {@code adapter.in} → {@code
     * application} is the ordinary HTTP-to-use-case direction, and {@link
     * SourceDiagnosticsService} → {@code adapter.out.pec} is the one already-documented, named
     * exception in {@link #applicationLayersDoNotReachAdaptersDirectly}. A run with these two
     * edges ignored and nothing else changed reports zero remaining violations, confirming they
     * are the entire cause, not a partial mask of a broader problem.
     */
    @Test
    void capabilityLayerSlicesAreFreeOfCycles() {
        SlicesRuleDefinition.slices()
                .matching(BASE + ".(*).(*)..")
                .should().beFreeOfCycles()
                .ignoreDependency(SourceDiagnosticsService.class, PecDataSourceFactory.class)
                .ignoreDependency(SourceDiagnosticsService.class, PecSourceConnection.class)
                .check(CLASSES);
    }
}
