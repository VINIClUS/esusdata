package esusdata.indicator.reconciliation;

import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.ARTIFACT_DIR;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.BINARY;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.ENV_FILE;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.MANIFESTS_DIR;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.MANIFEST_OUTPUT;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.MODE;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.OFFICIAL_EXPORT_DIR;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.OFFICIAL_EXPORT_IBGE;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.PERIODS;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.POLICY;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.REGISTRY;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.REPO_ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Capture;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Diagnostic;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Mode;
import esusdata.testsupport.LivePecAssumptions;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The two runners of the Portão D are bounded by their configuration: the capture takes no PEC and
 * no registry, the diagnostic takes no registry, no policy and writes nowhere near {@code docs/},
 * and {@code periods} chooses what the diagnostic runs without ever being an authority for the gate.
 */
class PortaoDRunnerConfigurationTest {

    private static final Map<String, String> CAPTURE = Map.of(
            MODE, "capture",
            OFFICIAL_EXPORT_DIR, "exports",
            OFFICIAL_EXPORT_IBGE, "3541307",
            ARTIFACT_DIR, "artifacts",
            MANIFEST_OUTPUT, "manifests");

    private static final Map<String, String> DIAGNOSTIC = Map.of(
            MODE, "diagnostic",
            MANIFESTS_DIR, "manifests",
            ARTIFACT_DIR, "artifacts",
            ENV_FILE, "pec.env",
            BINARY, "execplane");

    private static Map<String, String> plus(Map<String, String> base, String property, String value) {
        Map<String, String> properties = new HashMap<>(base);
        properties.put(property, value);
        return properties;
    }

    private static Map<String, String> without(Map<String, String> base, String property) {
        Map<String, String> properties = new HashMap<>(base);
        properties.remove(property);
        return properties;
    }

    private static List<String> componentsOf(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    // ---- the mode

    @Test
    void withoutAModeNeitherRunnerRuns() {
        assertThat(PortaoDRunnerConfiguration.modeOf(Map.<String, String>of()::get))
                .isEmpty();
    }

    @Test
    void captureAndDiagnosticAreTheModes() {
        assertThat(PortaoDRunnerConfiguration.modeOf(CAPTURE::get)).contains(Mode.CAPTURE);
        assertThat(PortaoDRunnerConfiguration.modeOf(DIAGNOSTIC::get)).contains(Mode.DIAGNOSTIC);
    }

    @ParameterizedTest(name = "mode \"{0}\" is refused")
    @ValueSource(strings = {"all", "evaluate", "compatibility", "gate", "CAPTURE", "Diagnostic", "", " capture"})
    void anyOtherModeIsRefusedNotIgnored(String mode) {
        Map<String, String> properties = plus(CAPTURE, MODE, mode);

        assertThatThrownBy(() -> PortaoDRunnerConfiguration.modeOf(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown " + MODE);
    }

    // ---- the capture runner

    @Test
    void theCaptureIsParsedIntoItsFourInputs() {
        Capture capture = Capture.parse(CAPTURE::get);

        assertThat(capture)
                .isEqualTo(new Capture(Path.of("exports"), "3541307", Path.of("artifacts"), Path.of("manifests")));
    }

    @ParameterizedTest(name = "the capture takes no {0}")
    @ValueSource(strings = {ENV_FILE, BINARY, REGISTRY, REPO_ROOT, POLICY, MANIFESTS_DIR, PERIODS})
    void theCaptureAcceptsNoPecNoExecutionPlaneNoRegistryAndNoPolicy(String property) {
        Map<String, String> properties = plus(CAPTURE, property, "anything");

        assertThatThrownBy(() -> Capture.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
    }

    @ParameterizedTest(name = "the capture needs {0}")
    @ValueSource(strings = {OFFICIAL_EXPORT_DIR, OFFICIAL_EXPORT_IBGE, ARTIFACT_DIR, MANIFEST_OUTPUT})
    void theCaptureNeedsEachOfItsInputs(String property) {
        Map<String, String> properties = without(CAPTURE, property);
        Map<String, String> blank = plus(CAPTURE, property, " ");

        assertThatThrownBy(() -> Capture.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
        assertThatThrownBy(() -> Capture.parse(blank::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
    }

    @Test
    void theCaptureWantsTheSevenDigitIbgeCode() {
        Map<String, String> siaps = plus(CAPTURE, OFFICIAL_EXPORT_IBGE, "354130");

        assertThatThrownBy(() -> Capture.parse(siaps::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7-digit");
    }

    @Test
    void theCaptureRecordHasNoRoomForAPecOrARegistry() {
        assertThat(componentsOf(Capture.class))
                .containsExactly("exportDir", "municipalityIbge", "artifactDir", "manifestOutput");
    }

    @ParameterizedTest(name = "the capture does not store the raw exports under {0}")
    @ValueSource(strings = {"docs", "docs/out", "repo/docs/portoes", "repo/docs/../docs/x", "../docs"})
    void theCaptureNeverStoresTheRawExportsUnderDocs(String artifactDir) {
        Map<String, String> properties = plus(CAPTURE, ARTIFACT_DIR, artifactDir);

        assertThatThrownBy(() -> Capture.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs/");
    }

    @Test
    void theManifestsOfTheCaptureMayBeWrittenUnderDocs() {
        Capture capture = Capture.parse(plus(CAPTURE, MANIFEST_OUTPUT, "docs/indicadores/portoes/references")::get);

        assertThat(capture.manifestOutput()).isEqualTo(Path.of("docs/indicadores/portoes/references"));
    }

    // ---- the diagnostic runner

    @Test
    void theDiagnosticIsParsedIntoItsInputsAndRunsEveryCapturedPeriodByDefault() {
        Diagnostic diagnostic = Diagnostic.parse(DIAGNOSTIC::get);

        assertThat(diagnostic.manifestsDir()).isEqualTo(Path.of("manifests"));
        assertThat(diagnostic.artifactDir()).isEqualTo(Path.of("artifacts"));
        assertThat(diagnostic.envFile()).isEqualTo(Path.of("pec.env"));
        assertThat(diagnostic.binary()).isEqualTo("execplane");
        assertThat(diagnostic.periods()).isEmpty();
    }

    @Test
    void theSecretFileDefaultsToTheOneTheOtherLiveTestsUse() {
        Diagnostic diagnostic = Diagnostic.parse(without(DIAGNOSTIC, ENV_FILE)::get);

        assertThat(diagnostic.envFile()).isEqualTo(LivePecAssumptions.ENV_FILE);
    }

    @ParameterizedTest(name = "the diagnostic takes no {0}")
    @ValueSource(strings = {REGISTRY, REPO_ROOT, POLICY, OFFICIAL_EXPORT_DIR, OFFICIAL_EXPORT_IBGE, MANIFEST_OUTPUT})
    void theDiagnosticAcceptsNoRegistryNoPolicyAndNoOutputOfTheCapture(String property) {
        Map<String, String> properties = plus(DIAGNOSTIC, property, "anything");

        assertThatThrownBy(() -> Diagnostic.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
    }

    @ParameterizedTest(name = "the diagnostic needs {0}")
    @ValueSource(strings = {MANIFESTS_DIR, ARTIFACT_DIR, BINARY})
    void theDiagnosticNeedsEachOfItsInputs(String property) {
        Map<String, String> properties = without(DIAGNOSTIC, property);

        assertThatThrownBy(() -> Diagnostic.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
    }

    @ParameterizedTest(name = "the diagnostic does not write under {0}")
    @ValueSource(strings = {"docs", "docs/out", "repo/docs/portoes", "repo/docs/../docs/x", "../docs"})
    void theDiagnosticNeverWritesUnderDocs(String artifactDir) {
        Map<String, String> properties = plus(DIAGNOSTIC, ARTIFACT_DIR, artifactDir);

        assertThatThrownBy(() -> Diagnostic.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs/");
    }

    @Test
    void aDirectoryThatMerelyHasDocsInItsNameIsNotTheDocsDirectory() {
        Diagnostic diagnostic = Diagnostic.parse(plus(DIAGNOSTIC, ARTIFACT_DIR, "docs-copy/out")::get);

        assertThat(diagnostic.artifactDir()).isEqualTo(Path.of("docs-copy/out"));
    }

    @Test
    void theDiagnosticRecordHasNoRoomForARegistryAPolicyOrAnOutputBesideTheArtifactDirectory() {
        assertThat(componentsOf(Diagnostic.class))
                .containsExactly("manifestsDir", "artifactDir", "envFile", "binary", "periods");
    }

    // ---- periods choose, they never authorize

    @Test
    void periodsSelectWhichCapturedPeriodsRun() {
        Diagnostic diagnostic = Diagnostic.parse(plus(DIAGNOSTIC, PERIODS, "2025Q3, 2026Q1")::get);

        assertThat(diagnostic.periods())
                .containsExactlyInAnyOrder(new Quadrimestre(2025, 3), new Quadrimestre(2026, 1));
    }

    @ParameterizedTest(name = "periods \"{0}\" is refused")
    @ValueSource(strings = {"2026Q4", "26Q1", "2026Q1,2026Q1", "2026Q1,", ",2026Q1", "latest"})
    void aPeriodThatIsNotAQuadrimestreOrIsRepeatedIsRefused(String periods) {
        Map<String, String> properties = plus(DIAGNOSTIC, PERIODS, periods);

        assertThatThrownBy(() -> Diagnostic.parse(properties::get)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "periods \"{0}\" is a diagnostic")
    @ValueSource(strings = {"", "2025Q3", "2026Q1", "2025Q3,2026Q1"})
    void periodsNeverGrantGateAuthority(String periods) {
        Diagnostic diagnostic = Diagnostic.parse(plus(DIAGNOSTIC, PERIODS, periods)::get);

        assertThat(diagnostic.purpose()).isEqualTo(ReferencePurpose.DIAGNOSTIC);
    }
}
