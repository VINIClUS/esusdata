package esusdata.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.GateCheck;
import esusdata.indicator.model.GateId;
import esusdata.indicator.model.GateStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.reconciliation.Comparison;
import esusdata.indicator.reconciliation.PackVerdict.Status;
import esusdata.indicator.reconciliation.PortaoDEvidenceChecks;
import esusdata.indicator.reconciliation.PortaoDEvidenceFixtures;
import esusdata.indicator.reconciliation.PortaoDEvidenceFixtures.Tree;
import esusdata.indicator.reconciliation.ReferencePolicy;
import esusdata.indicator.reconciliation.SummaryWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The build-time half of the registry's validation (ADR 0032). The jar carries the registry but
 * not the documents its evidence points at, so the loader checks their shape only; here, against
 * the repository, every registered pack has an entry at the rule version compiled into this
 * release, and every evidence reference of a passed gate exists and still has the recorded SHA-256.
 * A bumped {@code rule_version} without a new entry fails this test — the approval is voided, not
 * silently carried over.
 */
class ReleaseGatesConsistencyTest {

    private static final Path REPO = Path.of("..", "..");
    private static final Path SOURCE = REPO.resolve("contracts/indicators/release-gates.json");
    private static final PackDescriptor C4 = new C4Pack().descriptor();
    private static final String SUMMARY_STATUS = "\"status\": \"PASSED\"";
    // the row verdicts the set summary prints
    private static final String ROW_PASSES = "passa";
    private static final String ROW_FAILS = "reprova";
    private static final String ROW_NOT_EVALUATED = "não avaliada";

    static Stream<PackDescriptor> registered() {
        return ReleaseGateRegistry.registeredPacks().stream();
    }

    @Test
    void thePackagedRegistryIsTheContractFileOfTheRepository() throws Exception {
        try (InputStream packaged = getClass().getResourceAsStream(ReleaseGateRegistry.RESOURCE)) {
            assertThat(packaged).isNotNull();
            assertThat(packaged.readAllBytes()).isEqualTo(Files.readAllBytes(SOURCE));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registered")
    void everyRegisteredPackHasAnEntryForItsCurrentRuleVersion(PackDescriptor descriptor) throws Exception {
        JsonNode root = new ObjectMapper().readTree(Files.readString(SOURCE, StandardCharsets.UTF_8));
        assertThat(root.get("packs")).as("entries of %s", descriptor.id()).anySatisfy(entry -> {
            assertThat(entry.get("pack").asString()).isEqualTo(descriptor.id());
            assertThat(entry.get("rule_version").asString()).isEqualTo(descriptor.ruleVersion());
        });
        GateStatus status = ReleaseGateRegistry.bundled().statusOf(descriptor);
        assertThat(status.stale())
                .as("%s: the registry only knows an older rule_version", descriptor.id())
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registered")
    void everyEvidenceReferenceOfAPassedGateExistsAndMatchesItsHash(PackDescriptor descriptor) throws Exception {
        GateStatus status = ReleaseGateRegistry.bundled().statusOf(descriptor);
        for (GateId id : GateId.values()) {
            for (GateCheck.Evidence evidence : status.check(id).evidence()) {
                Path document = REPO.resolve(evidence.ref());
                assertThat(document)
                        .as("%s gate %s evidence %s", descriptor.id(), id, evidence.ref())
                        .exists();
                assertThat(sha256(document))
                        .as("%s gate %s: %s changed since its check passed", descriptor.id(), id, evidence.ref())
                        .isEqualTo(evidence.sha256());
            }
        }
    }

    @Test
    void noPackShipsWithAGatePassedByAnAgentWithoutItsEvidence() {
        for (PackDescriptor descriptor : ReleaseGateRegistry.registeredPacks()) {
            GateStatus status = ReleaseGateRegistry.bundled().statusOf(descriptor);
            for (GateId id : GateId.values()) {
                if (status.check(id).isPassed()) {
                    assertThat(status.check(id).evidence()).isNotEmpty();
                    assertThat(status.check(id).check()).isNotBlank();
                }
            }
        }
    }

    // ---- D, decided against the pre-registered SIAPS reference set (ADR 0034)

    private static List<String> problemsOf(Tree tree) throws Exception {
        return PortaoDEvidenceChecks.registryProblems(
                new ObjectMapper().readTree(Files.readString(tree.registry(), StandardCharsets.UTF_8)),
                tree.loadedPolicy(),
                tree.root());
    }

    /** Edits the summary and cites its new hash in the registry, so that only the edit is wrong. */
    private static void editSummary(Tree tree, String target, String replacement) throws Exception {
        String before = SummaryWriter.sha256(tree.summaryJson());
        PortaoDEvidenceFixtures.replaceIn(tree.summaryJson(), target, replacement);
        PortaoDEvidenceFixtures.replaceIn(tree.registry(), before, SummaryWriter.sha256(tree.summaryJson()));
    }

    @Test
    void everyDecidedDOfTheRealRegistryStandsOnTheCurrentSetOrThereIsNone() throws Exception {
        ReferencePolicy policy = PortaoDEvidenceChecks.policyOf(REPO);

        assertThat(PortaoDEvidenceChecks.registryProblems(
                        new ObjectMapper().readTree(Files.readString(SOURCE, StandardCharsets.UTF_8)), policy, REPO))
                .isEmpty();
    }

    @Test
    void aDecidedDWrittenByTheRealWritersPassesBothWays(@TempDir Path workspace) throws Exception {
        assertThat(problemsOf(PortaoDEvidenceFixtures.decided(workspace.resolve("p"), C4, Status.PASSED)))
                .isEmpty();
        assertThat(problemsOf(PortaoDEvidenceFixtures.decided(workspace.resolve("f"), C4, Status.FAILED)))
                .isEmpty();
    }

    @Test
    void anOldCheckIsRefusedForD(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.PASSED);
        PortaoDEvidenceFixtures.replaceIn(
                tree.registry(), "siaps-distribuicao-por-classe@2", "siaps-distribuicao-por-classe@1");

        assertThat(problemsOf(tree))
                .anyMatch(problem -> problem.contains("the check is siaps-distribuicao-por-classe@1"));
    }

    @Test
    void aSummaryOfAnotherGateSetThanTheCurrentPolicyIsRefused(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace.resolve("summary"), C4, Status.PASSED);
        editSummary(tree, tree.verdict().gateSetSha256(), "0".repeat(64));
        assertThat(problemsOf(tree)).anyMatch(problem -> problem.contains("gate_set_sha256 of the summary"));

        // the policy moved on after D was decided: a GATE declaration changed
        Tree moved = PortaoDEvidenceFixtures.decided(workspace.resolve("policy"), C4, Status.PASSED);
        PortaoDEvidenceFixtures.replaceIn(
                moved.policy(), "\"compatibility\":\"EXACT\"", "\"compatibility\":\"EQUIVALENT_FOR_REFERENCE\"");
        assertThat(problemsOf(moved))
                .anyMatch(problem -> problem.contains("gate_set_sha256 of the summary"))
                .anyMatch(problem -> problem.contains("dossier verdict is not the declared compatibility"));
    }

    @Test
    void aSummaryThatDoesNotListExactlyTheGateReferencesOfTheSetIsRefused(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.PASSED);
        editSummary(tree, "\"reference_id\": \"" + tree.referenceId() + "\"", "\"reference_id\": \"zz-other\"");

        assertThat(problemsOf(tree)).anyMatch(problem -> problem.contains("does not list exactly the GATE references"));
    }

    @Test
    void aSummaryWhoseStatusIsNotTheRecordedOneOrDoesNotFollowFromItsReferencesIsRefused(@TempDir Path workspace)
            throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace.resolve("recorded"), C4, Status.PASSED);
        editSummary(tree, SUMMARY_STATUS, "\"status\": \"FAILED\"");
        assertThat(problemsOf(tree))
                .anyMatch(problem -> problem.contains("status of the summary is not the one recorded"));

        Tree follows = PortaoDEvidenceFixtures.decided(workspace.resolve("follows"), C4, Status.PASSED);
        editSummary(follows, "\"status\": \"PASSED\",\n      \"reason\"", "\"status\": \"PENDING\",\n      \"reason\"");
        assertThat(problemsOf(follows)).anyMatch(problem -> problem.contains("does not follow from the statuses"));
    }

    @Test
    void aSummaryThatPointsAtAFileThePolicyDoesNotPinIsRefused(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.PASSED);
        editSummary(
                tree,
                "\"reference_manifest_sha256\": \"" + SummaryWriter.sha256(tree.manifest()),
                "\"reference_manifest_sha256\": \"" + "1".repeat(64) + "\", \"x\": \"");

        assertThat(problemsOf(tree)).anyMatch(problem -> problem.contains("manifest in the summary is not the one"));
    }

    @Test
    void aFailedSummaryRewrittenAsPassedIsRefusedByItsOwnRows(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.FAILED);
        String before = SummaryWriter.sha256(tree.summaryJson());
        Files.writeString(
                tree.summaryJson(),
                PortaoDEvidenceFixtures.read(tree.summaryJson()).replace("\"status\": \"FAILED\"", SUMMARY_STATUS),
                StandardCharsets.UTF_8);
        PortaoDEvidenceFixtures.replaceIn(tree.registry(), before, SummaryWriter.sha256(tree.summaryJson()));
        PortaoDEvidenceFixtures.replaceIn(tree.registry(), "\"status\": \"FAILED\"", SUMMARY_STATUS);

        assertThat(problemsOf(tree)).anyMatch(problem -> problem.contains("does not follow from its rows"));
    }

    @Test
    void aRowWhoseThresholdIsNotTheOneItsNsGivesIsRefused(@TempDir Path workspace) throws Exception {
        // below the mask every count has the same threshold, so a masked n_s still fixes t
        assertThat(IntStream.range(0, 10).map(Comparison::threshold)).containsOnly(2);
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.PASSED);
        editSummary(tree, "\"t\": \"2\"", "\"t\": \"9\"");

        assertThat(problemsOf(tree)).anyMatch(problem -> problem.contains("t is not the threshold its n_s gives"));
    }

    @Test
    void aNegativeDistanceIsNoVerdict(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.FAILED);
        String row = rowWith(tree, ROW_FAILS);
        editSummary(
                tree,
                row,
                row.replaceFirst("\"d\": \"[0-9]+\"", "\"d\": \"-1\"").replace(ROW_FAILS, ROW_PASSES));

        assertThat(problemsOf(tree)).anyMatch(problem -> problem.contains("verdict is not the one its d and t give"));
    }

    @Test
    void aRowWithTeamsCannotBeLeftUnevaluated(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.FAILED);
        String row = rowWith(tree, ROW_FAILS);
        editSummary(
                tree,
                row,
                row.replaceFirst("\"d\": \"[0-9]+\"", "\"d\": \"-\"")
                        .replaceFirst("\"t\": \"[0-9]+\"", "\"t\": \"-\"")
                        .replaceFirst("\"n_s\": \"[^\"]+\"", "\"n_s\": \"10\"")
                        .replace(ROW_FAILS, ROW_NOT_EVALUATED));

        assertThat(problemsOf(tree)).anyMatch(problem -> problem.contains("a row with teams is not evaluated"));
    }

    @Test
    void aPinnedDossierAboutAnotherReferenceIsRefused(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.PASSED);
        String summary = SummaryWriter.sha256(tree.summaryJson());
        String dossier = SummaryWriter.sha256(tree.dossierJson());
        String gateSet = tree.verdict().gateSetSha256();
        PortaoDEvidenceFixtures.replaceIn(
                tree.dossierJson(),
                "\"reference_id\": \"" + tree.referenceId() + "\"",
                "\"reference_id\": \"zz-other\"");
        // pinned again in the policy, the summary and the registry, so that only its scope is wrong
        String repinned = SummaryWriter.sha256(tree.dossierJson());
        replaceAll(tree.policy(), dossier, repinned);
        String regated = tree.loadedPolicy()
                .referenceSet(tree.packId(), tree.verdict().ruleVersion())
                .gateSetSha256();
        for (Path file : List.of(tree.summaryJson(), tree.registry())) {
            replaceAll(file, dossier, repinned);
            replaceAll(file, gateSet, regated);
        }
        replaceAll(tree.registry(), summary, SummaryWriter.sha256(tree.summaryJson()));

        assertThat(problemsOf(tree))
                .anyMatch(problem -> problem.contains("the dossier is of another reference"))
                .noneMatch(problem -> problem.contains("gate_set_sha256"))
                .noneMatch(problem -> problem.contains("missing or has changed"));
    }

    @Test
    void aSummaryThatLeavesOutAFailingRowIsRefused(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.FAILED);
        String row = Pattern.quote(rowWith(tree, ROW_FAILS));
        String before = SummaryWriter.sha256(tree.summaryJson());
        Files.writeString(
                tree.summaryJson(),
                PortaoDEvidenceFixtures.read(tree.summaryJson())
                        .replaceFirst("(?:" + row + ",\\s*|,\\s*" + row + ")", "")
                        .replace("\"status\": \"FAILED\"", SUMMARY_STATUS),
                StandardCharsets.UTF_8);
        PortaoDEvidenceFixtures.replaceIn(tree.registry(), before, SummaryWriter.sha256(tree.summaryJson()));
        PortaoDEvidenceFixtures.replaceIn(tree.registry(), "\"status\": \"FAILED\"", SUMMARY_STATUS);

        assertThat(problemsOf(tree)).anyMatch(problem -> problem.contains("not one per team type of the gate"));
    }

    @Test
    void anNsRaisedBeyondTheTeamsOfTheManifestIsRefused(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.PASSED);
        String row = rowWith(tree, ROW_PASSES);
        // with its threshold, so that only the count is wrong
        editSummary(
                tree,
                row,
                row.replaceFirst("\"n_s\": \"[^\"]+\"", "\"n_s\": \"99\"")
                        .replaceFirst("\"t\": \"[0-9]+\"", "\"t\": \"" + Comparison.threshold(99) + "\""));

        assertThat(problemsOf(tree))
                .anyMatch(problem -> problem.contains("add up to more teams than the manifest has"))
                .noneMatch(problem -> problem.contains("t is not the threshold"));
    }

    /** The JSON object of the first row of the summary with that verdict, as the writer printed it. */
    private static String rowWith(Tree tree, String verdict) throws Exception {
        String text = PortaoDEvidenceFixtures.read(tree.summaryJson());
        int end = text.indexOf('}', text.indexOf("\"row_verdict\": \"" + verdict + "\""));
        return text.substring(text.lastIndexOf('{', end), end + 1);
    }

    private static void replaceAll(Path file, String target, String replacement) throws Exception {
        Files.writeString(
                file, PortaoDEvidenceFixtures.read(file).replace(target, replacement), StandardCharsets.UTF_8);
    }

    @Test
    void aSummaryThatClaimsAnotherLocalSourceThanItsDossierIsRefused(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.PASSED);
        editSummary(
                tree,
                "\"local_source_fingerprint\": \"" + PortaoDEvidenceFixtures.FINGERPRINT + "\"",
                "\"local_source_fingerprint\": \"sha256:" + "2".repeat(64) + "\"");

        assertThat(problemsOf(tree))
                .anyMatch(problem -> problem.contains("local source in the summary is not the one the dossier"));
    }

    @Test
    void aCitedManifestOrDossierThatIsMissingOrChangedIsRefused(@TempDir Path workspace) throws Exception {
        Tree changed = PortaoDEvidenceFixtures.decided(workspace.resolve("changed"), C4, Status.PASSED);
        PortaoDEvidenceFixtures.replaceIn(changed.manifest(), "\"row_count\"", "\"row_count \"");
        assertThat(problemsOf(changed)).anyMatch(problem -> problem.contains("manifest is missing or has changed"));

        Tree missing = PortaoDEvidenceFixtures.decided(workspace.resolve("missing"), C4, Status.PASSED);
        Files.delete(missing.dossierJson());
        assertThat(problemsOf(missing)).anyMatch(problem -> problem.contains("dossier is missing or has changed"));

        Tree summary = PortaoDEvidenceFixtures.decided(workspace.resolve("summary"), C4, Status.PASSED);
        PortaoDEvidenceFixtures.append(summary.summaryJson(), " ");
        assertThat(problemsOf(summary))
                .anyMatch(problem -> problem.contains("set summary is missing or is not the one"));
    }

    @Test
    void aDThatCitesNoSummaryOrAnotherPathIsRefused(@TempDir Path workspace) throws Exception {
        Tree tree = PortaoDEvidenceFixtures.decided(workspace, C4, Status.PASSED);
        PortaoDEvidenceFixtures.replaceIn(tree.registry(), "\"kind\": \"conciliacao-siaps\"", "\"kind\": \"outro\"");

        assertThat(problemsOf(tree)).anyMatch(problem -> problem.contains("does not cite the set summary"));
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
