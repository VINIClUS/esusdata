package esusdata.indicator.reconciliation;

import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.ARTIFACT_DIR;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.BINARY;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.DEFAULT_PROFILES;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.DOSSIER_DIR;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.ENV_FILE;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.GIT;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.MANIFESTS_DIR;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.MANIFEST_OUTPUT;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.MODE;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.OFFICIAL_EXPORT_DIR;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.OFFICIAL_EXPORT_IBGE;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.PERIODS;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.POLICY;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.PROFILES;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.REGISTRY;
import static esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.REPO_ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Capture;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Compatibility;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Diagnostic;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Mode;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Replay;
import esusdata.testsupport.LivePecAssumptions;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The runners of the Portão D are bounded by their configuration: the capture takes no PEC and no
 * registry, the diagnostic takes no registry, no policy and writes nowhere near {@code docs/}, the
 * compatibility run writes only dossiers there, the replay takes no PEC and the gate check takes no
 * period; and {@code periods} chooses what a run covers without ever being an authority for the gate.
 */
class PortaoDRunnerConfigurationTest {

    /** Outside any working tree, as the runners require: this module's directory is in one. */
    private static final Path ARTIFACTS =
            Path.of(System.getProperty("java.io.tmpdir"), "portao-d-runner-configuration", "artifacts");

    private static final Map<String, String> CAPTURE = Map.of(
            MODE, "capture",
            OFFICIAL_EXPORT_DIR, "exports",
            OFFICIAL_EXPORT_IBGE, "3541307",
            ARTIFACT_DIR, ARTIFACTS.toString(),
            MANIFEST_OUTPUT, "manifests");

    private static final Map<String, String> DIAGNOSTIC = Map.of(
            MODE, "diagnostic",
            MANIFESTS_DIR, "manifests",
            ARTIFACT_DIR, ARTIFACTS.toString(),
            ENV_FILE, "pec.env",
            BINARY, "execplane");

    private static final Map<String, String> COMPATIBILITY = Map.of(
            MODE, "compatibility",
            MANIFESTS_DIR, "manifests",
            ARTIFACT_DIR, ARTIFACTS.toString(),
            DOSSIER_DIR, "docs/indicadores/portoes/compatibilidade",
            ENV_FILE, "pec.env",
            BINARY, "execplane");

    private static final Map<String, String> REPLAY = Map.of(
            MODE, "replay",
            MANIFESTS_DIR, "manifests",
            ARTIFACT_DIR, ARTIFACTS.toString(),
            DOSSIER_DIR, "dossiers");

    private static final Map<String, String> GATE = Map.of(
            MODE, "gate",
            REPO_ROOT, "repo",
            ARTIFACT_DIR, ARTIFACTS.toString());

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
    void theFiveRunnersAreTheModes() {
        assertThat(PortaoDRunnerConfiguration.modeOf(CAPTURE::get)).contains(Mode.CAPTURE);
        assertThat(PortaoDRunnerConfiguration.modeOf(DIAGNOSTIC::get)).contains(Mode.DIAGNOSTIC);
        assertThat(PortaoDRunnerConfiguration.modeOf(COMPATIBILITY::get)).contains(Mode.COMPATIBILITY);
        assertThat(PortaoDRunnerConfiguration.modeOf(REPLAY::get)).contains(Mode.REPLAY);
        assertThat(PortaoDRunnerConfiguration.modeOf(GATE::get)).contains(Mode.GATE);
    }

    @ParameterizedTest(name = "mode \"{0}\" is refused")
    @ValueSource(
            strings = {"all", "evaluate", "Compatibility", "GATE", "replays", "CAPTURE", "Diagnostic", "", " capture"})
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

        assertThat(capture).isEqualTo(new Capture(Path.of("exports"), "3541307", ARTIFACTS, Path.of("manifests")));
    }

    @ParameterizedTest(name = "the capture takes no {0}")
    @ValueSource(
            strings = {ENV_FILE, BINARY, REGISTRY, REPO_ROOT, POLICY, MANIFESTS_DIR, PERIODS, DOSSIER_DIR, PROFILES})
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
        assertThat(diagnostic.artifactDir()).isEqualTo(ARTIFACTS);
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
    @ValueSource(
            strings = {
                REGISTRY,
                REPO_ROOT,
                POLICY,
                OFFICIAL_EXPORT_DIR,
                OFFICIAL_EXPORT_IBGE,
                MANIFEST_OUTPUT,
                DOSSIER_DIR,
                PROFILES
            })
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
        Path docsCopy = ARTIFACTS.resolveSibling("docs-copy").resolve("out");

        Diagnostic diagnostic = Diagnostic.parse(plus(DIAGNOSTIC, ARTIFACT_DIR, docsCopy.toString())::get);

        assertThat(diagnostic.artifactDir()).isEqualTo(docsCopy);
    }

    // ---- docs/ through a link

    @ParameterizedTest(name = "a link into docs/ is refused as {0}")
    @ValueSource(strings = {"artifacts", "artifacts/sp/2026q1"})
    void aLinkIntoDocsIsRefusedLikeDocsItself(String artifactDir, @TempDir Path temp) throws IOException {
        Path docs = Files.createDirectories(temp.resolve("repo/docs/private"));
        Files.createSymbolicLink(temp.resolve("artifacts"), docs);
        String throughTheLink = temp.resolve(artifactDir).toString();

        assertThatThrownBy(() -> Capture.parse(plus(CAPTURE, ARTIFACT_DIR, throughTheLink)::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs/");
        assertThatThrownBy(() -> Diagnostic.parse(plus(DIAGNOSTIC, ARTIFACT_DIR, throughTheLink)::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs/");
    }

    @Test
    void aLinkThatLeadsAnywhereElseIsAccepted(@TempDir Path temp) throws IOException {
        Path store = Files.createDirectories(temp.resolve("store"));
        Path link = Files.createSymbolicLink(temp.resolve("artifacts"), store);

        Capture capture = Capture.parse(plus(CAPTURE, ARTIFACT_DIR, link.toString())::get);

        assertThat(capture.artifactDir()).isEqualTo(link);
    }

    @Test
    void aLinkThatLeadsNowhereIsRefusedRatherThanGuessed(@TempDir Path temp) throws IOException {
        Path link = Files.createSymbolicLink(temp.resolve("artifacts"), temp.resolve("missing"));

        assertThatThrownBy(() -> Diagnostic.parse(plus(DIAGNOSTIC, ARTIFACT_DIR, link.toString())::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot resolve");
    }

    // ---- nowhere in a Git working tree

    @ParameterizedTest(name = "a working tree marked by a .git {0} is refused")
    @ValueSource(strings = {"directory", "file"})
    void noRunnerWritesInAGitWorkingTreeEvenOutsideDocs(String marker, @TempDir Path temp) throws IOException {
        Path repository = Files.createDirectories(temp.resolve("repo"));
        if ("directory".equals(marker)) {
            Files.createDirectory(repository.resolve(GIT));
        } else {
            // a linked worktree: its .git is a file that names the main repository's
            Files.writeString(repository.resolve(GIT), "gitdir: elsewhere\n");
        }
        String inside = repository.resolve("artifacts/sp").toString();
        String throughALink = Files.createSymbolicLink(
                        temp.resolve("store"), Files.createDirectories(repository.resolve("data")))
                .toString();

        for (String artifactDir : List.of(inside, throughALink)) {
            assertThatThrownBy(() -> Capture.parse(plus(CAPTURE, ARTIFACT_DIR, artifactDir)::get))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Git working tree");
            assertThatThrownBy(() -> Diagnostic.parse(plus(DIAGNOSTIC, ARTIFACT_DIR, artifactDir)::get))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Git working tree");
        }
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

    // ---- the compatibility runner

    @Test
    void theCompatibilityRunIsParsedIntoItsInputsAndTheDossiersMayGoUnderDocs() {
        Compatibility compatibility = Compatibility.parse(COMPATIBILITY::get);

        assertThat(compatibility.manifestsDir()).isEqualTo(Path.of("manifests"));
        assertThat(compatibility.artifactDir()).isEqualTo(ARTIFACTS);
        assertThat(compatibility.dossierDir()).isEqualTo(Path.of("docs/indicadores/portoes/compatibilidade"));
        assertThat(compatibility.envFile()).isEqualTo(Path.of("pec.env"));
        assertThat(compatibility.binary()).isEqualTo("execplane");
        assertThat(compatibility.periods()).isEmpty();
    }

    @Test
    void theProfilesDefaultToTheContractFileAndTheSecretFileToTheOneOfTheOtherLiveTests() {
        Compatibility compatibility = Compatibility.parse(without(COMPATIBILITY, ENV_FILE)::get);

        assertThat(compatibility.profiles()).endsWithRaw(Path.of(DEFAULT_PROFILES));
        assertThat(compatibility.envFile()).isEqualTo(LivePecAssumptions.ENV_FILE);
        assertThat(Compatibility.parse(plus(COMPATIBILITY, PROFILES, "p.json")::get)
                        .profiles())
                .isEqualTo(Path.of("p.json"));
    }

    @ParameterizedTest(name = "the compatibility run takes no {0}")
    @ValueSource(strings = {REGISTRY, REPO_ROOT, POLICY, OFFICIAL_EXPORT_DIR, OFFICIAL_EXPORT_IBGE, MANIFEST_OUTPUT})
    void theCompatibilityRunAcceptsNoRegistryNoPolicyAndNoOutputOfTheCapture(String property) {
        Map<String, String> properties = plus(COMPATIBILITY, property, "anything");

        assertThatThrownBy(() -> Compatibility.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
    }

    @ParameterizedTest(name = "the compatibility run needs {0}")
    @ValueSource(strings = {MANIFESTS_DIR, ARTIFACT_DIR, DOSSIER_DIR, BINARY})
    void theCompatibilityRunNeedsEachOfItsInputs(String property) {
        Map<String, String> properties = without(COMPATIBILITY, property);

        assertThatThrownBy(() -> Compatibility.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
    }

    @Test
    void theCompatibilityRunSelectsPeriodsAndNeverWritesTheLocalDetailUnderDocsOrInAWorkingTree(@TempDir Path temp)
            throws IOException {
        Compatibility compatibility = Compatibility.parse(plus(COMPATIBILITY, PERIODS, "2026Q1")::get);
        Path repository = Files.createDirectories(temp.resolve("repo"));
        Files.createDirectory(repository.resolve(GIT));

        assertThat(compatibility.periods()).containsExactly(new Quadrimestre(2026, 1));
        assertThatThrownBy(() -> Compatibility.parse(plus(COMPATIBILITY, ARTIFACT_DIR, "repo/docs/x")::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs/");
        assertThatThrownBy(() -> Compatibility.parse(plus(
                        COMPATIBILITY, ARTIFACT_DIR, repository.resolve("a").toString())::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Git working tree");
    }

    // ---- the replay

    @Test
    void theReplayIsParsedIntoItsFourInputsAndHasNoRoomForAPecOrAPeriod() {
        Replay replay = Replay.parse(REPLAY::get);

        assertThat(replay.manifestsDir()).isEqualTo(Path.of("manifests"));
        assertThat(replay.artifactDir()).isEqualTo(ARTIFACTS);
        assertThat(replay.dossierDir()).isEqualTo(Path.of("dossiers"));
        assertThat(componentsOf(Replay.class)).containsExactly("manifestsDir", "artifactDir", "dossierDir", "profiles");
    }

    @ParameterizedTest(name = "the replay takes no {0}")
    @ValueSource(strings = {ENV_FILE, BINARY, PERIODS, REGISTRY, REPO_ROOT, POLICY, MANIFEST_OUTPUT})
    void theReplayCannotBeToldOfAPecAPeriodOrARegistry(String property) {
        Map<String, String> properties = plus(REPLAY, property, "anything");

        assertThatThrownBy(() -> Replay.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
    }

    @ParameterizedTest(name = "the replay needs {0}")
    @ValueSource(strings = {MANIFESTS_DIR, ARTIFACT_DIR, DOSSIER_DIR})
    void theReplayNeedsEachOfItsInputs(String property) {
        Map<String, String> properties = without(REPLAY, property);

        assertThatThrownBy(() -> Replay.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
    }

    @Test
    void theReplayNeverWritesUnderDocs() {
        assertThatThrownBy(() -> Replay.parse(plus(REPLAY, ARTIFACT_DIR, "repo/docs/x")::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs/");
    }

    // ---- the gate check

    @Test
    void theGateCheckIsParsedIntoTheRepositoryAndTheArtifactDirectoryAndNothingElse() {
        PortaoDRunnerConfiguration.GateRun gate = PortaoDRunnerConfiguration.GateRun.parse(GATE::get);

        assertThat(gate).isEqualTo(new PortaoDRunnerConfiguration.GateRun(Path.of("repo"), ARTIFACTS));
        assertThat(componentsOf(PortaoDRunnerConfiguration.GateRun.class)).containsExactly("repoRoot", "artifactDir");
    }

    @ParameterizedTest(name = "the gate check takes no {0}")
    @ValueSource(
            strings = {
                PERIODS,
                REGISTRY,
                POLICY,
                ENV_FILE,
                BINARY,
                MANIFESTS_DIR,
                DOSSIER_DIR,
                PROFILES,
                OFFICIAL_EXPORT_DIR,
                MANIFEST_OUTPUT
            })
    void theGateCheckRefusesAnAdHocPeriodAndEverythingTheCompatibilityRunTakes(String property) {
        Map<String, String> properties = plus(GATE, property, "anything");

        assertThatThrownBy(() -> PortaoDRunnerConfiguration.GateRun.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
    }

    @ParameterizedTest(name = "the gate check needs {0}")
    @ValueSource(strings = {REPO_ROOT, ARTIFACT_DIR})
    void theGateCheckNeedsTheRepositoryAndTheArtifactDirectory(String property) {
        Map<String, String> properties = without(GATE, property);

        assertThatThrownBy(() -> PortaoDRunnerConfiguration.GateRun.parse(properties::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(property);
    }

    @Test
    void theGateCheckNeverWritesItsSummaryUnderDocs() {
        assertThatThrownBy(() -> PortaoDRunnerConfiguration.GateRun.parse(plus(GATE, ARTIFACT_DIR, "repo/docs/x")::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docs/");
    }

    @Test
    void theSingleFlowRunnerOfTheSignatureEraIsGone() {
        Path source = Path.of("src", "test", "java")
                .resolve(PortaoDRunnerConfigurationTest.class.getPackageName().replace('.', '/'));

        assertThat(source.resolve("PortaoDLiveTest.java")).doesNotExist();
        assertThat(source.resolve("PortaoDCompatibilityLiveTest.java")).exists();
        assertThat(source.resolve("PortaoDGateLiveTest.java")).exists();
        assertThat(source.resolve("PortaoDEvidenceReplayLiveTest.java")).exists();
    }
}
