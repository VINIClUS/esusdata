package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.GateCheck;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Kind;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Outcome;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Output;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Profiles;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.ReplayResult;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Report;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Sources;
import esusdata.run.worker.AcquisitionInputs;
import esusdata.run.worker.FixturePec;
import esusdata.run.worker.ReferenceScopedExtracts;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The compatibility run, the replay and the gate check, end to end and offline: synthetic official
 * exports are captured, a fixture PEC stands in for the execution plane, and the methodology
 * profiles are the invented ones of {@link CompatibilityFixtures}, so that the verdicts are the ones
 * the profile and the probes dictate. What is proved: every pack and the Nota Final get a dossier
 * whose verdict follows the profile and the probes; the Nota Final carries the verdicts of its
 * siblings; a probe that throws or a profile that is missing never takes the run down and never
 * becomes a verdict; a rerun is byte-identical; the local detail never lands under {@code docs/};
 * the replay reproduces the dossiers without a PEC and refuses to acquire; the gate check refuses a
 * dirty tree, a {@code HEAD} that is not merged, a GATE declaration that is not {@code HEAD}'s and a
 * source that is not the decided one; it decides every set from the cache alone, aborts without
 * writing anything when a verdict cannot be computed, and records D with evidence the offline
 * checks accept.
 */
class PortaoDCompatibilityRunTest {

    private static final String IBGE = SiapsTeamExportFixtures.MUNICIPALITY_IBGE;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC);
    private static final Quadrimestre Q3_2025 = new Quadrimestre(2025, 3);
    private static final Quadrimestre Q1_2026 = new Quadrimestre(2026, 1);
    private static final Set<YearMonth> ALL_MONTHS = months(YearMonth.of(2025, 9), YearMonth.of(2026, 4));
    private static final GatePack C1 = GatePack.all().getFirst();
    private static final GatePack C2 = GatePack.all().get(1);
    private static final GatePack C3 = GatePack.all().get(2);
    private static final String C1_REFERENCE = "zz-9999990-2026q1-c1-team-r1";
    private static final String NOTA_FINAL_REFERENCE = "zz-9999990-2026q1-ciii-team-r1";
    private static final String VERDICT = "verdict";
    private static final String RELEASE_GATES = "contracts/indicators/release-gates.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern INE_SHAPE = Pattern.compile("(?<![0-9a-f])\\d{10}(?![0-9a-f])");

    /** The type of every team-month read from the audit trail: the fixture extracts hold no team history. */
    private static final PortaoDCompatibilityRun.Coverage AUDITED =
            (inputs, teams) -> new TeamTypeCoverage(40, 40, 0, 0, 0, 0);

    @TempDir
    Path workspace;

    private static Set<YearMonth> months(YearMonth from, YearMonth to) {
        Set<YearMonth> months = new HashSet<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    private Path repo() {
        return workspace.resolve("repo");
    }

    private Path manifests() {
        return repo().resolve(ReferencePolicy.MANIFEST_DIR);
    }

    private Path dossiers() {
        return repo().resolve(ReferencePolicy.DOSSIER_DIR);
    }

    private Path artifacts() {
        return workspace.resolve("artifacts");
    }

    private Path local() {
        return artifacts().resolve("compatibilidade");
    }

    /** The synthetic official exports of 2025Q3 and 2026Q1, captured into the manifests directory. */
    private void capture() throws IOException {
        Path exports = Files.createDirectories(workspace.resolve("exports"));
        Files.write(
                exports.resolve("q3-2025.csv"),
                SiapsTeamExportFixtures.export()
                        .competence("Q3/25")
                        .standardTeams()
                        .bytes());
        Files.write(
                exports.resolve("q1-2026.csv"),
                SiapsTeamExportFixtures.standard().bytes());
        new ReferenceCapture(artifacts(), manifests(), "zz", CLOCK).capture(exports, IBGE);
    }

    private ReferenceScopedExtracts extracts() {
        return new ReferenceScopedExtracts(artifacts().resolve("extratos"), CLOCK);
    }

    private PortaoDCompatibilityRun open(Profiles profiles, Function<String, List<MethodologyProbe>> probes)
            throws IOException {
        return PortaoDCompatibilityRun.open(manifests(), artifacts(), Set.of(), extracts(), profiles, probes, AUDITED);
    }

    /** One profile for every compiled pack and the Nota Final, whose first quadrimestre reads as given. */
    private static Profiles profiles(OfficialReading first, OfficialReading second, boolean convention) {
        return (packId, ruleVersion) ->
                Optional.of(CompatibilityFixtures.profile(packId, ruleVersion, first, second, convention));
    }

    private static MethodologyProbe probe(String id, Function<ProbeContext, ProbeResult> answer) {
        return new MethodologyProbe() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Set<String> packs() {
                return Set.of();
            }

            @Override
            public ProbeResult evaluate(ProbeContext context) {
                return answer.apply(context);
            }
        };
    }

    /** The probes of the fixture profile: clean everywhere, except as {@code override} says for a pack. */
    private static Function<String, List<MethodologyProbe>> probes(
            Map<String, Function<ProbeContext, ProbeResult>> firstProbeOverrides) {
        return packId -> {
            Function<ProbeContext, ProbeResult> first = firstProbeOverrides.getOrDefault(
                    packId, context -> CompatibilityFixtures.clean(CompatibilityFixtures.FIRST_PROBE));
            return List.of(
                    probe(CompatibilityFixtures.FIRST_PROBE, first),
                    probe(
                            CompatibilityFixtures.SECOND_PROBE,
                            context -> CompatibilityFixtures.clean(CompatibilityFixtures.SECOND_PROBE)),
                    probe(
                            CompatibilityFixtures.CONVENTION_PROBE,
                            context -> CompatibilityFixtures.clean(CompatibilityFixtures.CONVENTION_PROBE)));
        };
    }

    private static Function<String, List<MethodologyProbe>> cleanProbes() {
        return probes(Map.of());
    }

    private Report run(FixturePec pec, Profiles profiles, Function<String, List<MethodologyProbe>> probes)
            throws IOException {
        return open(profiles, probes)
                .run(Sources.fixed(pec.sourceIdentity()), ALL_MONTHS, pec.inputs(), new Output(dossiers(), local()));
    }

    private JsonNode dossier(String referenceId, GatePack pack) throws IOException {
        return JSON.readTree(Files.readString(dossiers().resolve(referenceId + "-" + pack.packId() + ".json")));
    }

    private String reasonOf(GatePack pack) {
        try {
            return dossier(id(Q1_2026, pack), pack).path("reason").asString();
        } catch (IOException e) {
            return e.toString();
        }
    }

    private static String id(Quadrimestre period, GatePack pack) {
        return "zz-9999990-" + period.year() + "q" + period.index() + "-"
                + pack.code().toLowerCase(java.util.Locale.ROOT) + "-team-r1";
    }

    private static Optional<Outcome> outcome(Report report, Quadrimestre period, GatePack pack) {
        return report.outcomes().stream()
                .filter(each -> each.period().equals(period) && each.pack().equals(pack.code()))
                .findFirst();
    }

    private static List<Path> filesOf(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    // ---- the dossiers and their verdicts

    @Test
    void everyPackAndTheNotaFinalOfEveryCapturedPeriodGetsADossierWhoseVerdictFollowsTheProfile() throws IOException {
        capture();

        Report report =
                run(new FixturePec(IBGE), profiles(OfficialReading.SAME, OfficialReading.SAME, false), cleanProbes());

        assertThat(report.errors()).isEmpty();
        assertThat(report.outcomes()).hasSize(2 * GatePack.allWithNotaFinal().size());
        assertThat(report.outcomes())
                .allSatisfy(outcome -> assertThat(outcome.kind()).isEqualTo(Kind.DOSSIER));
        // 2026Q1 has an official edition in the profile, 2025Q3 does not: that one cannot be decided
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            assertThat(outcome(report, Q1_2026, pack).orElseThrow().verdict())
                    .as("%s 2026Q1: %s", pack.code(), reasonOf(pack))
                    .contains(ReferenceCompatibility.EXACT);
            assertThat(outcome(report, Q3_2025, pack).orElseThrow().verdict())
                    .as("%s 2025Q3", pack.code())
                    .contains(ReferenceCompatibility.INCONCLUSIVE);
        }
        assertThat(dossier(C1_REFERENCE, C1).path(VERDICT).asString()).isEqualTo("EXACT");
        assertThat(dossier(C1_REFERENCE, C1).has("official_field_comparison")).isTrue();
        assertThat(filesOf(dossiers()))
                .hasSize(2 * 2 * GatePack.allWithNotaFinal().size());
    }

    @Test
    void withoutATeamHistoryInTheExtractsTheTypeStaysOpenAndTheReferenceIsInconclusive() throws IOException {
        capture();
        FixturePec pec = new FixturePec(IBGE);

        Report report = PortaoDCompatibilityRun.open(
                        manifests(),
                        artifacts(),
                        Set.of(),
                        extracts(),
                        profiles(OfficialReading.SAME, OfficialReading.SAME, false),
                        cleanProbes())
                .run(Sources.fixed(pec.sourceIdentity()), ALL_MONTHS, pec.inputs(), new Output(dossiers(), local()));

        assertThat(report.errors()).isEmpty();
        assertThat(outcome(report, Q1_2026, C1).orElseThrow().verdict()).contains(ReferenceCompatibility.INCONCLUSIVE);
        assertThat(dossier(C1_REFERENCE, C1).path("reason").asString()).contains("team type");
    }

    @Test
    void aDeclaredConventionMakesEveryPackEquivalentAndTheNotaFinalCarriesTheSevenSiblingVerdicts() throws IOException {
        capture();

        Report report =
                run(new FixturePec(IBGE), profiles(OfficialReading.SAME, OfficialReading.SAME, true), cleanProbes());

        assertThat(report.errors()).isEmpty();
        for (GatePack pack : GatePack.all()) {
            assertThat(outcome(report, Q1_2026, pack).orElseThrow().verdict())
                    .contains(ReferenceCompatibility.EQUIVALENT_FOR_REFERENCE);
        }
        JsonNode notaFinal = dossier(NOTA_FINAL_REFERENCE, GatePack.NOTA_FINAL);
        assertThat(notaFinal.path(VERDICT).asString()).isEqualTo("EQUIVALENT_FOR_REFERENCE");
        JsonNode siblings = notaFinal.path("sibling_verdicts");
        assertThat(siblings.size()).isEqualTo(GatePack.all().size());
        GatePack.all()
                .forEach(pack ->
                        assertThat(siblings.path(pack.packId()).asString()).isEqualTo("EQUIVALENT_FOR_REFERENCE"));
        assertThat(dossier(C1_REFERENCE, C1).has("sibling_verdicts")).isFalse();
    }

    @Test
    void aCompleteDivergenceIsIncompatibleAndTheNotaFinalWaitsForItsSiblings() throws IOException {
        capture();
        Function<String, List<MethodologyProbe>> probes = probes(Map.of(
                C1.packId(),
                context -> ProbeResult.complete(CompatibilityFixtures.FIRST_PROBE, 12, 3, List.of("a team"))));

        Report report =
                run(new FixturePec(IBGE), profiles(OfficialReading.DIFFERENT, OfficialReading.SAME, false), probes);

        assertThat(outcome(report, Q1_2026, C1).orElseThrow().verdict()).contains(ReferenceCompatibility.INCOMPATIBLE);
        assertThat(outcome(report, Q1_2026, C2).orElseThrow().verdict())
                .contains(ReferenceCompatibility.EQUIVALENT_FOR_REFERENCE);
        JsonNode notaFinal = dossier(NOTA_FINAL_REFERENCE, GatePack.NOTA_FINAL);
        assertThat(notaFinal.path(VERDICT).asString()).isEqualTo("INCONCLUSIVE");
        assertThat(notaFinal.path("sibling_verdicts").path(C1.packId()).asString())
                .isEqualTo("INCOMPATIBLE");
    }

    @Test
    void aProbeThatThrowsIsAFailedProbeInTheLocalLogAndNoResultAndTheRunGoesOn() throws IOException {
        capture();
        Function<String, List<MethodologyProbe>> probes = probes(Map.of(C2.packId(), context -> {
            throw new IllegalStateException("broke on " + SiapsTeamExportFixtures.ESF_1);
        }));

        Report report =
                run(new FixturePec(IBGE), profiles(OfficialReading.DIFFERENT, OfficialReading.SAME, false), probes);

        assertThat(report.errors()).isEmpty();
        assertThat(report.localLog())
                .anySatisfy(line -> assertThat(line)
                        .contains(id(Q1_2026, C2))
                        .contains(CompatibilityFixtures.FIRST_PROBE)
                        .contains("failed"));
        assertThat(outcome(report, Q1_2026, C2).orElseThrow().verdict()).contains(ReferenceCompatibility.INCONCLUSIVE);
        assertThat(outcome(report, Q1_2026, C3).orElseThrow().verdict())
                .contains(ReferenceCompatibility.EQUIVALENT_FOR_REFERENCE);
        assertThat(dossier(id(Q1_2026, C2), C2).path("reason").asString())
                .contains(CompatibilityFixtures.FIRST_DIMENSION);
        assertThat(dossier(id(Q1_2026, C2), C2).toString()).doesNotContain(SiapsTeamExportFixtures.ESF_1);
    }

    @Test
    void aPackWithoutAProfileIsAGapAndNeverAVerdictAndTheNotaFinalDoesNotInventIt() throws IOException {
        capture();
        Profiles withoutC3 = (packId, ruleVersion) -> C3.packId().equals(packId)
                ? Optional.empty()
                : profiles(OfficialReading.SAME, OfficialReading.SAME, false).of(packId, ruleVersion);

        Report report = run(new FixturePec(IBGE), withoutC3, cleanProbes());

        Outcome gap = outcome(report, Q1_2026, C3).orElseThrow();
        assertThat(gap.kind()).isEqualTo(Kind.GAP);
        assertThat(gap.verdict()).isEmpty();
        assertThat(gap.detail()).contains("no methodology profile");
        assertThat(dossiers().resolve(id(Q1_2026, C3) + "-" + C3.packId() + ".json"))
                .doesNotExist();
        assertThat(outcome(report, Q1_2026, GatePack.NOTA_FINAL).orElseThrow().verdict())
                .as(outcome(report, Q1_2026, GatePack.NOTA_FINAL).toString())
                .contains(ReferenceCompatibility.INCONCLUSIVE);
        assertThat(dossier(NOTA_FINAL_REFERENCE, GatePack.NOTA_FINAL)
                        .path("reason")
                        .asString())
                .contains(C3.packId());
    }

    @Test
    void aProfileOfAnotherPackIsAScopeMismatchThatLeavesTheReferenceInconclusive() throws IOException {
        capture();
        Profiles wrongPack = (packId, ruleVersion) -> C1.packId().equals(packId)
                ? Optional.of(CompatibilityFixtures.profile(
                        "fx-pack", "fx-pack@1.0.0", OfficialReading.SAME, OfficialReading.SAME, false))
                : profiles(OfficialReading.SAME, OfficialReading.SAME, false).of(packId, ruleVersion);

        Report report = run(new FixturePec(IBGE), wrongPack, cleanProbes());

        assertThat(outcome(report, Q1_2026, C1).orElseThrow().verdict()).contains(ReferenceCompatibility.INCONCLUSIVE);
        assertThat(dossier(C1_REFERENCE, C1).path("reason").asString()).contains("scope");
    }

    @Test
    void aPeriodTheLocalSourceLacksMonthsOfIsAGapWithTheMonthsAndHasNoDossier() throws IOException {
        capture();
        FixturePec pec = new FixturePec(IBGE);
        Set<YearMonth> coverage = new HashSet<>(ALL_MONTHS);
        coverage.remove(YearMonth.of(2026, 3));

        Report report = open(profiles(OfficialReading.SAME, OfficialReading.SAME, false), cleanProbes())
                .run(Sources.fixed(pec.sourceIdentity()), coverage, pec.inputs(), new Output(dossiers(), local()));

        assertThat(report.outcomes().stream().filter(outcome -> outcome.period().equals(Q1_2026)))
                .hasSize(GatePack.allWithNotaFinal().size())
                .allSatisfy(outcome -> {
                    assertThat(outcome.kind()).isEqualTo(Kind.GAP);
                    assertThat(outcome.detail()).contains("2026-03");
                });
        assertThat(filesOf(dossiers()).stream().map(file -> file.getFileName().toString()))
                .allMatch(name -> name.contains("2025q3"));
    }

    // ---- determinism and the local detail

    @Test
    void theSameInputsGiveByteIdenticalDossiersAndTheDossiersCarryNoIne() throws IOException {
        capture();
        FixturePec pec = new FixturePec(IBGE);
        Profiles profiles = profiles(OfficialReading.SAME, OfficialReading.SAME, true);
        run(pec, profiles, cleanProbes());
        int requested = pec.requests().size();
        Path second = workspace.resolve("second");

        open(profiles, cleanProbes())
                .run(Sources.recorded(extracts()), ALL_MONTHS, AcquisitionInputs.none(), new Output(second, local()));

        assertThat(requested).isPositive();
        assertThat(pec.requests()).hasSize(requested);
        for (Path file : filesOf(dossiers())) {
            assertThat(Files.readAllBytes(second.resolve(file.getFileName())))
                    .as(file.getFileName().toString())
                    .isEqualTo(Files.readAllBytes(file));
            assertThat(Files.readString(file)).doesNotContainPattern(INE_SHAPE);
        }
    }

    @Test
    void thePerTeamDetailGoesToTheLocalDirectoryAndNeverUnderDocs() throws IOException {
        capture();
        FixturePec pec = new FixturePec(IBGE);

        run(pec, profiles(OfficialReading.SAME, OfficialReading.SAME, false), cleanProbes());

        assertThat(filesOf(local().resolve("detalhe"))).isNotEmpty();
        assertThat(filesOf(dossiers()))
                .extracting(file -> file.getFileName().toString())
                .allMatch(name -> name.endsWith(".json") || name.endsWith(".md"));

        Path underDocs = workspace.resolve("docs").resolve("local");
        Report refused = open(profiles(OfficialReading.SAME, OfficialReading.SAME, false), cleanProbes())
                .run(
                        Sources.recorded(extracts()),
                        ALL_MONTHS,
                        AcquisitionInputs.none(),
                        new Output(workspace.resolve("third"), underDocs));
        assertThat(refused.errors()).isNotEmpty();
        assertThat(refused.errors())
                .allSatisfy(error -> assertThat(error.detail()).contains("docs/"));
        assertThat(filesOf(underDocs)).isEmpty();
    }

    // ---- the replay

    @Test
    void theReplayReproducesEveryDossierWithoutAPecAndFlagsTamperingAndMissingFiles() throws IOException {
        capture();
        FixturePec pec = new FixturePec(IBGE);
        Profiles profiles = profiles(OfficialReading.SAME, OfficialReading.SAME, true);
        run(pec, profiles, cleanProbes());
        int requested = pec.requests().size();

        ReplayResult same = replay(profiles, workspace.resolve("scratch-1"));

        assertThat(same.identical()).isTrue();
        assertThat(same.regenerated()).hasSize(filesOf(dossiers()).size());
        assertThat(pec.requests()).hasSize(requested);

        Path tampered = dossiers().resolve(C1_REFERENCE + "-" + C1.packId() + ".md");
        Files.writeString(tampered, Files.readString(tampered) + "x", StandardCharsets.UTF_8);
        Files.delete(dossiers().resolve(NOTA_FINAL_REFERENCE + "-" + GatePack.NOTA_FINAL.packId() + ".json"));
        ReplayResult broken = replay(profiles, workspace.resolve("scratch-2"));

        assertThat(broken.identical()).isFalse();
        assertThat(broken.different()).containsExactly(tampered.getFileName().toString());
        assertThat(broken.extra()).containsExactly(NOTA_FINAL_REFERENCE + "-" + GatePack.NOTA_FINAL.packId() + ".json");
        assertThat(broken.missing()).isEmpty();
    }

    @Test
    void aReferenceThatIsAGapInAReplayedPeriodFailsTheReplay() throws IOException {
        capture();
        Profiles profiles = profiles(OfficialReading.SAME, OfficialReading.SAME, false);
        run(new FixturePec(IBGE), profiles, cleanProbes());
        Profiles withoutC3 = (packId, ruleVersion) ->
                C3.packId().equals(packId) ? Optional.empty() : profiles.of(packId, ruleVersion);

        ReplayResult result = replay(withoutC3, workspace.resolve("scratch"));

        assertThat(result.identical()).isFalse();
        assertThat(result.gaps()).extracting(Outcome::pack).contains(C3.code());
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void theReplayRefusesToAcquireAndFailsWhenAPartitionIsGone() throws IOException {
        capture();
        FixturePec pec = new FixturePec(IBGE);
        Profiles profiles = profiles(OfficialReading.SAME, OfficialReading.SAME, false);
        run(pec, profiles, cleanProbes());
        int requested = pec.requests().size();
        deleteTree(partitionOf(C2, YearMonth.of(2026, 2)));

        ReplayResult result = replay(profiles, workspace.resolve("scratch"));

        assertThat(result.identical()).isFalse();
        assertThat(result.errors()).extracting(Outcome::pack).contains(C2.code());
        assertThat(result.errors())
                .allSatisfy(error -> assertThat(error.detail()).contains("nothing may be acquired"));
        assertThat(pec.requests()).hasSize(requested);
    }

    private ReplayResult replay(Profiles profiles, Path scratch) throws IOException {
        return PortaoDCompatibilityRun.replay(
                manifests(), artifacts(), extracts(), profiles, cleanProbes(), AUDITED, dossiers(), scratch);
    }

    /** The directory of a month of a pack of 2026Q1 in the cache. */
    private Path partitionOf(GatePack pack, YearMonth month) throws IOException {
        try (Stream<Path> tree =
                Files.walk(artifacts().resolve("extratos").resolve(IBGE).resolve("2026Q1"))) {
            return tree.filter(Files::isDirectory)
                    .filter(directory -> directory.getFileName().toString().equals(month.toString()))
                    .filter(directory -> directory.toString().contains(File.separator + pack.packId() + "@"))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    // ---- the gate check

    /** The real policy with one GATE declaration for the C1 reference of 2026Q1, over the dossier just written. */
    private void declareGate(String compatibility, String fingerprintOverride) throws IOException {
        Path dossierFile = dossiers().resolve(C1_REFERENCE + "-" + C1.packId() + ".json");
        if (fingerprintOverride != null) {
            JsonNode tree = JSON.readTree(Files.readString(dossierFile));
            Files.writeString(
                    dossierFile,
                    Files.readString(dossierFile)
                            .replace(tree.path("local_source_fingerprint").asString(), fingerprintOverride));
        }
        String manifestSha = SummaryWriter.sha256(Files.readAllBytes(manifests().resolve(C1_REFERENCE + ".json")));
        String dossierSha = SummaryWriter.sha256(Files.readAllBytes(dossierFile));
        tools.jackson.databind.node.ObjectNode policy = (tools.jackson.databind.node.ObjectNode)
                JSON.readTree(Files.readString(Path.of("..", "..").resolve(GateCheck.POLICY_FILE)));
        tools.jackson.databind.node.ObjectNode declaration = JSON.createObjectNode();
        declaration.put("reference_id", C1_REFERENCE);
        declaration.put("quadrimestre", "2026Q1");
        declaration.put("municipality_ibge", IBGE);
        declaration.put("source_kind", "OFFICIAL_TEAM_EXPORT_CSV");
        declaration.put("purpose", "GATE");
        declaration.put("required", true);
        declaration.put("status", "ACTIVE");
        declaration.put("compatibility", compatibility);
        declaration.put("reference_manifest_sha256", manifestSha);
        declaration.put("compatibility_evidence_ref", ReferencePolicy.dossierPath(C1_REFERENCE, C1.packId()));
        declaration.put("compatibility_evidence_sha256", dossierSha);
        ((tools.jackson.databind.node.ArrayNode)
                        policy.path("reference_sets").get(0).path("references"))
                .add(declaration);
        Path file = repo().resolve(GateCheck.POLICY_FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, JSON.writeValueAsString(policy));
        Files.copy(
                Path.of("..", "..").resolve(RELEASE_GATES),
                Files.createDirectories(repo().resolve(RELEASE_GATES).getParent())
                        .resolve("release-gates.json"));
    }

    private static GateCheck.Repository git(boolean clean, String head) {
        return git(clean, true, head);
    }

    private static GateCheck.Repository git(boolean clean, boolean merged, String head) {
        return new GateCheck.Repository() {
            @Override
            public boolean isClean() {
                return clean;
            }

            @Override
            public boolean isMerged() {
                return merged;
            }

            @Override
            public Optional<String> committed(String repositoryPath) {
                return Optional.ofNullable(head);
            }
        };
    }

    private GateCheck.Summary check(GateCheck.Repository repository) throws IOException {
        return GateCheck.check(
                repo(), repository, artifacts(), extracts(), ReleaseGateRegistry.registeredPacks(), CLOCK);
    }

    private String committedPolicy() throws IOException {
        return Files.readString(repo().resolve(GateCheck.POLICY_FILE));
    }

    private void campaign() throws IOException {
        capture();
        run(new FixturePec(IBGE), profiles(OfficialReading.SAME, OfficialReading.SAME, false), cleanProbes());
    }

    private JsonNode gateD(String pack) throws IOException {
        for (JsonNode entry :
                JSON.readTree(Files.readString(repo().resolve(RELEASE_GATES))).path("packs")) {
            if (entry.path("pack").asString().equals(pack)) {
                return entry.path("gates").path("D");
            }
        }
        throw new AssertionError(pack);
    }

    @Test
    void theGateCheckAcceptsAGateReferenceWhoseDossierAndCachedSourceAgreeAndRecordsDWithEvidenceTheChecksAccept()
            throws IOException {
        campaign();
        declareGate("EXACT", null);

        GateCheck.Summary summary = check(git(true, committedPolicy()));

        assertThat(summary.gateReferences()).isEqualTo(1);
        assertThat(summary.ok()).as(summary.markdown()).isTrue();
        assertThat(summary.packs()).hasSize(8);
        assertThat(summary.packs().getFirst().gateSetSha256()).matches("[0-9a-f]{64}");
        assertThat(summary.packs().get(1).references()).isEmpty();
        assertThat(Files.readString(artifacts().resolve("gate").resolve("resumo.md")))
                .contains("veredito do conjunto", "gate_set_sha256")
                .contains("OK `" + C1_REFERENCE + "`");

        // the set of C1 is decided from the cache, the others have no GATE reference
        ReferenceSetVerdict verdict = summary.packs().getFirst().verdict();
        assertThat(verdict.status()).isNotEqualTo(PackVerdict.Status.PENDING);
        assertThat(verdict.references()).singleElement().satisfies(outcome -> {
            assertThat(outcome.status()).isEqualTo(verdict.status());
            assertThat(outcome.localSourceFingerprint()).startsWith("sha256:");
        });
        JsonNode d = gateD(C1.packId());
        assertThat(d.path("status").asString()).isEqualTo(verdict.status().name());
        assertThat(d.path("check").asString()).isEqualTo("siaps-distribuicao-por-classe@2");
        assertThat(d.path("checked_at").asString()).isEqualTo("2026-10-08");
        assertThat(d.path("evidence")).hasSize(1);
        for (GatePack other : GatePack.allWithNotaFinal().subList(1, 8)) {
            assertThat(gateD(other.packId()).path("status").asString()).isEqualTo("PENDING");
        }
        Path summaryJson = repo().resolve(SummaryWriter.SET_SUMMARY_DIR)
                .resolve(SummaryWriter.summaryFileName(verdict.ruleVersion(), ".json"));
        assertThat(summaryJson).exists();
        assertThat(repo().resolve(SummaryWriter.SET_SUMMARY_DIR).toFile().list())
                .hasSize(2);

        // what the runner wrote, over what PR B's writers wrote, passes the checks CI runs
        ReferencePolicy policy = PortaoDEvidenceChecks.policyOf(repo());
        assertThat(PortaoDEvidenceChecks.registryProblems(
                        JSON.readTree(Files.readString(repo().resolve(RELEASE_GATES))), policy, repo()))
                .isEmpty();
        assertThat(PortaoDEvidenceChecks.privacyProblems(repo())).isEmpty();
        // the fixture policy declares one reference only: the other manifests and dossiers are uncited
        assertThat(PortaoDEvidenceChecks.fileProblems(policy, repo()))
                .isNotEmpty()
                .allSatisfy(problem -> assertThat(problem).endsWith("is cited by no declaration"));
    }

    @Test
    void anEmptyGateSetIsReportedAsEmptyIsPendingAndIsNotAFailure() throws IOException {
        Files.createDirectories(repo().resolve(GateCheck.POLICY_FILE).getParent());
        Files.copy(Path.of("..", "..").resolve(GateCheck.POLICY_FILE), repo().resolve(GateCheck.POLICY_FILE));
        Files.copy(Path.of("..", "..").resolve(RELEASE_GATES), repo().resolve(RELEASE_GATES));

        GateCheck.Summary summary = check(git(true, committedPolicy()));

        assertThat(summary.ok()).isTrue();
        assertThat(summary.gateReferences()).isZero();
        assertThat(summary.markdown()).contains("conjunto de gate vazio");
        assertThat(summary.packs()).allSatisfy(pack -> {
            assertThat(pack.verdict().status()).isEqualTo(PackVerdict.Status.PENDING);
            assertThat(pack.verdict().reason()).isEqualTo("sem referência GATE ativa e obrigatória");
            assertThat(gateD(pack.pack()).path("status").asString()).isEqualTo("PENDING");
        });
        assertThat(repo().resolve(SummaryWriter.SET_SUMMARY_DIR)).doesNotExist();
        // every D is already PENDING in the registry: recording PENDING changes not a byte of it
        assertThat(Files.readString(repo().resolve(RELEASE_GATES)))
                .isEqualTo(Files.readString(Path.of("..", "..").resolve(RELEASE_GATES)));
    }

    @Test
    void aDirtyWorkingTreeIsRefusedBeforeAnythingIsRead() throws IOException {
        campaign();
        declareGate("EXACT", null);

        assertThatThrownBy(() -> check(git(false, committedPolicy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not clean");
        assertThat(artifacts().resolve("gate")).doesNotExist();
    }

    @Test
    void aHeadThatIsNotMergedIntoMainIsRefusedAndNothingIsWritten() throws IOException {
        campaign();
        declareGate("EXACT", null);
        byte[] registry = Files.readAllBytes(repo().resolve(RELEASE_GATES));

        assertThatThrownBy(() -> check(git(true, false, committedPolicy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("origin/main");
        assertThat(artifacts().resolve("gate")).doesNotExist();
        assertThat(repo().resolve(SummaryWriter.SET_SUMMARY_DIR)).doesNotExist();
        assertThat(Files.readAllBytes(repo().resolve(RELEASE_GATES))).isEqualTo(registry);
    }

    @Test
    void aGateDeclarationThatIsNotTheOneHeadHoldsIsRefused() throws IOException {
        campaign();
        declareGate("EXACT", null);
        String working = committedPolicy();
        String head = Files.readString(Path.of("..", "..").resolve(GateCheck.POLICY_FILE));

        assertThat(working).isNotEqualTo(head);
        assertThatThrownBy(() -> check(git(true, head)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("differ")
                .hasMessageContaining("HEAD");
    }

    @Test
    void aDossierDecidedOnAnotherLocalSourceThanTheCachedOneFailsTheReference() throws IOException {
        campaign();
        declareGate("EXACT", "sha256:" + "c".repeat(64));

        GateCheck.Summary summary = check(git(true, committedPolicy()));

        assertThat(summary.ok()).isFalse();
        assertThat(summary.packs().getFirst().references().getFirst().detail())
                .contains("not the one the dossier was decided on");
        // a source the dossier never covered decides nothing: the set is pending, not failed
        assertThat(summary.packs().getFirst().verdict().status()).isEqualTo(PackVerdict.Status.PENDING);
        assertThat(gateD(C1.packId()).path("status").asString()).isEqualTo("PENDING");
        assertThat(gateD(C1.packId()).path("evidence")).isEmpty();
    }

    @Test
    void aVerdictThatDoesNotAuthorizeTheGateFailsTheReference() throws IOException {
        campaign();
        declareGate("INCONCLUSIVE", null);

        // a declaration of a GATE reference with a verdict that does not authorize is refused by the policy itself
        assertThatThrownBy(() -> check(git(true, committedPolicy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXACT or EQUIVALENT_FOR_REFERENCE");
    }

    @Test
    void aGateReferenceWhoseCachedExtractsAreGoneFailsTheReferenceAndAcquiresNothing() throws IOException {
        campaign();
        declareGate("EXACT", null);
        deleteTree(partitionOf(C1, YearMonth.of(2026, 1)));

        GateCheck.Summary summary = check(git(true, committedPolicy()));

        assertThat(summary.ok()).isFalse();
        assertThat(summary.packs().getFirst().references().getFirst().detail()).contains("cache cannot stand");
        assertThat(summary.packs().getFirst().verdict().status()).isEqualTo(PackVerdict.Status.PENDING);
    }

    @Test
    void aVerdictThatCannotBeComputedAbortsTheWholeRunBeforeAnythingIsWritten() throws IOException {
        campaign();
        declareGate("EXACT", null);
        byte[] registry = Files.readAllBytes(repo().resolve(RELEASE_GATES));
        // the cache still stands for the dossier's source, but the stored reference to compare with is gone
        deleteTree(artifacts().resolve(IBGE).resolve("2026Q1"));

        assertThatThrownBy(() -> check(git(true, committedPolicy()))).isInstanceOf(Exception.class);

        assertThat(artifacts().resolve("gate")).doesNotExist();
        assertThat(repo().resolve(SummaryWriter.SET_SUMMARY_DIR)).doesNotExist();
        assertThat(Files.readAllBytes(repo().resolve(RELEASE_GATES))).isEqualTo(registry);
    }

    @Test
    void theNotaFinalGateReferenceIsCheckedAgainstTheSevenPacksOfTheCache() throws IOException {
        campaign();
        PortaoDCompatibilityRun run = open(profiles(OfficialReading.SAME, OfficialReading.SAME, false), cleanProbes());

        String fingerprint =
                run.cachedFingerprint(run.reference(NOTA_FINAL_REFERENCE).orElseThrow(), Sources.recorded(extracts()));

        assertThat(fingerprint)
                .isEqualTo(dossier(NOTA_FINAL_REFERENCE, GatePack.NOTA_FINAL)
                        .path("local_source_fingerprint")
                        .asString());
    }
}
