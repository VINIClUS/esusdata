package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.result.model.InputFingerprint;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.FileExtractStore;
import esusdata.run.worker.SensitivityExtracts;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import esusdata.testsupport.LivePecAssumptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Portão D, {@code siaps-distribuicao-por-classe@1} for C1–C7 and, after them, {@code
 * siaps-nota-final-por-classe@1} for the Nota Final do Componente III: the on-demand check against the
 * public SIAPS (rules: docs/indicadores/portoes/portao-d-conciliacao-siaps.md and
 * portao-d-nota-final-siaps.md). C1 is read as in production, with its team supplement ({@link
 * SensitivityExtracts}). Developer tooling: the product
 * never calls the SIAPS, and CI never runs this test, which is skipped unless {@code
 * -Dobservatorio.gate.d.live=true}.
 *
 * <p>Properties (besides the PEC ones the other live tests use, {@code
 * -Dobservatorio.execution-plane.binary} and the secret file):
 *
 * <ul>
 *   <li>{@code observatorio.gate.d.snapshot=<file>} reads the SIAPS snapshot from a file instead of
 *       calling the SIAPS (up to 10 anonymous requests per quadrimestre, saved to {@code
 *       target/portao-d/snapshot-<quadrimestre>.json}).
 *   <li>{@code observatorio.gate.d.quadrimestre=2026Q1} compares that quadrimestre; one that is not
 *       the pack's reference (most recent eligible published) makes that pack's run a diagnostic.
 *   <li>{@code observatorio.gate.d.uf=SP} (default: derived from the municipality code).
 *   <li>{@code observatorio.gate.d.registry=<release-gates.json>} records the D of each pack run
 *       as the gate there (never a diagnostic one). Absent, nothing is recorded.
 *   <li>{@code observatorio.gate.d.repo-root=<dir>} (default: found from the working directory).
 * </ul>
 *
 * <p>Output: summaries of gate runs in {@code docs/indicadores/portoes} (masked, the evidence);
 * diagnostic summaries and every raw file (class counts, class by INE) in {@code
 * target/portao-d}, which git ignores.
 *
 * <p>This is the public-aggregate flow, kept until the retrospective reconciliation replaces it.
 * The public SIAPS answer has no historical team universe (its team list is today's directory), so
 * a run over it is a diagnostic by construction: the comparison fails closed, and a pack run as
 * the gate over it is PENDING ("o agregado público não traz o universo histórico de equipes"),
 * never PASSED or FAILED. Only the official team export ({@link OfficialTeamExportCsvParser}) can
 * give the gate a universe. Each verdict carries the {@code InputFingerprint} of the extracts of
 * its quadrimestre.
 */
class PortaoDLiveTest {

    private static final Logger log = LoggerFactory.getLogger(PortaoDLiveTest.class);

    private static final String LIVE = "observatorio.gate.d.live";
    private static final String SNAPSHOT = "observatorio.gate.d.snapshot";
    private static final String QUADRIMESTRE = "observatorio.gate.d.quadrimestre";
    private static final String UF = "observatorio.gate.d.uf";
    private static final String REGISTRY = "observatorio.gate.d.registry";
    private static final String REPO_ROOT = "observatorio.gate.d.repo-root";
    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String ENV_FILE_PROPERTY = "observatorio.execution-plane.live-pec.env-file";
    private static final String EVIDENCE_DIR = "docs/indicadores/portoes";
    private static final String MANIFEST_SUFFIX = ".manifest.json";
    private static final String UF_CODES =
            "11RO12AC13AM14RR15PA16AP17TO21MA22PI23CE24RN25PB26PE27AL28SE29BA31MG32ES33RJ"
                    + "35SP41PR42SC43RS50MS51MT52GO53DF";

    /** The PEC the monthly results are acquired from. */
    private record Pec(Map<String, String> environment, Path envFile, String binary) {

        /** Opt-in, secret file, binary and tunnel, or the test is skipped. */
        static Pec assumed() throws IOException {
            String binary = System.getProperty(BINARY_PROPERTY);
            Assumptions.assumeTrue(
                    binary != null && Files.isExecutable(Path.of(binary)),
                    "Skipping: -D" + BINARY_PROPERTY + " not set");
            String configured = System.getProperty(ENV_FILE_PROPERTY);
            Path envFile =
                    configured == null || configured.isBlank() ? LivePecAssumptions.ENV_FILE : Path.of(configured);
            Assumptions.assumeTrue(Files.exists(envFile), "Skipping: no PEC secret file at " + envFile);
            Map<String, String> environment = Files.readAllLines(envFile).stream()
                    .filter(line -> line.contains("=") && !line.strip().startsWith("#"))
                    .collect(Collectors.toMap(
                            line -> line.substring(0, line.indexOf('=')).trim(),
                            line -> line.substring(line.indexOf('=') + 1).trim(),
                            (first, last) -> last));
            Assumptions.assumeTrue(
                    LivePecAssumptions.isReachable(
                            environment.get("PEC_DB_HOST"), Integer.parseInt(environment.get("PEC_DB_PORT"))),
                    "Skipping: the PEC is not reachable — tunnel likely down");
            return new Pec(environment, envFile, binary);
        }
    }

    @Test
    void comparesTheLocalClassesWithThePublicSiaps() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(LIVE), "Skipping: set -D" + LIVE + "=true");
        Path out = Path.of("target", "portao-d");
        Files.createDirectories(out);
        SiapsClient client = new SiapsClient();
        String snapshotFile = System.getProperty(SNAPSHOT);
        List<String> published = snapshotFile == null
                ? SiapsParser.publishedQuadrimestres(client.competencias())
                : readSnapshot(snapshotFile).published();

        List<PackVerdict> verdicts = new ArrayList<>();
        Map<GatePack, Quadrimestre> references = new LinkedHashMap<>();
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            Optional<Quadrimestre> reference = reference(pack, published);
            if (reference.isPresent()) {
                references.put(pack, reference.get());
            } else {
                verdicts.add(PackVerdict.pending(
                        pack, ruleVersion(pack), ReferencePurpose.GATE, Eligibility.waitingFor(pack)));
            }
        }
        if (!references.isEmpty()) {
            verdicts.addAll(compare(references, published, Pec.assumed(), client, snapshotFile, out));
        }
        record(verdicts, out, LocalDate.now(ZoneId.of("America/Sao_Paulo")));
        assertThat(verdicts).hasSize(GatePack.allWithNotaFinal().size());
    }

    /** Fetches (or reads) one snapshot per quadrimestre, acquires the months and compares each pack. */
    private static List<PackVerdict> compare(
            Map<GatePack, Quadrimestre> references,
            List<String> published,
            Pec pec,
            SiapsClient client,
            String snapshotFile,
            Path out)
            throws IOException {
        Map<Quadrimestre, SiapsSnapshot> snapshots = new LinkedHashMap<>();
        Map<Quadrimestre, Map<String, Map<YearMonth, List<TeamResult>>>> local = new LinkedHashMap<>();
        Map<Quadrimestre, String> fingerprints = new LinkedHashMap<>();
        for (Quadrimestre quadrimestre : new HashSet<>(references.values())) {
            SiapsSnapshot snapshot = snapshotFile == null
                    ? fetched(client, pec.environment(), SiapsFormats.quadrimestre(quadrimestre), out)
                    : readSnapshot(snapshotFile);
            assertThat(snapshot.quadrimestre())
                    .as("the snapshot file must hold the quadrimestre compared")
                    .isEqualTo(SiapsFormats.quadrimestre(quadrimestre));
            snapshots.put(quadrimestre, snapshot);
            local.put(quadrimestre, acquire(quadrimestre, out, pec));
            fingerprints.put(quadrimestre, fingerprintOf(out.resolve("extratos"), quadrimestre));
        }
        List<PackVerdict> verdicts = new ArrayList<>();
        for (Map.Entry<GatePack, Quadrimestre> entry : references.entrySet()) {
            GatePack pack = entry.getKey();
            Quadrimestre quadrimestre = entry.getValue();
            SiapsSnapshot snapshot = snapshots.get(quadrimestre);
            String fingerprint = fingerprints.get(quadrimestre);
            if (pack.isNotaFinal()) {
                // after the seven pack checks (GatePack.allWithNotaFinal puts it last)
                verdicts.add(notaFinalVerdict(quadrimestre, published, snapshot, local.get(quadrimestre), fingerprint));
            } else {
                Map<YearMonth, List<TeamResult>> months =
                        local.get(quadrimestre).getOrDefault(pack.packId(), Map.of());
                verdicts.add(verdict(pack, quadrimestre, published, snapshot, months, fingerprint));
            }
        }
        return verdicts;
    }

    /** {@code siaps-nota-final-por-classe@1}: the Nota Final of each team from the seven packs' months. */
    private static PackVerdict notaFinalVerdict(
            Quadrimestre quadrimestre,
            List<String> published,
            SiapsSnapshot snapshot,
            Map<String, Map<YearMonth, List<TeamResult>>> byPack,
            String fingerprint) {
        GatePack pack = GatePack.NOTA_FINAL;
        ReferencePurpose purpose = purposeOf(pack, quadrimestre, published);
        List<String> missing = LocalClasses.missingNotaFinalInputs(quadrimestre, byPack);
        if (!missing.isEmpty()) {
            return PackVerdict.pending(pack, ruleVersion(pack), purpose, "faltam as entradas locais de " + missing);
        }
        return PackVerdict.evaluate(
                pack,
                ruleVersion(pack),
                purpose,
                ValidatedReference.fromPublicAggregate(snapshot, pack),
                LocalClasses.ofNotaFinal(quadrimestre, byPack),
                fingerprint);
    }

    /** A gate run only for the reference of the pack; any other quadrimestre is a diagnostic. */
    private static ReferencePurpose purposeOf(GatePack pack, Quadrimestre quadrimestre, List<String> published) {
        return Eligibility.isReference(pack, quadrimestre, published)
                ? ReferencePurpose.GATE
                : ReferencePurpose.DIAGNOSTIC;
    }

    private static PackVerdict verdict(
            GatePack pack,
            Quadrimestre quadrimestre,
            List<String> published,
            SiapsSnapshot snapshot,
            Map<YearMonth, List<TeamResult>> months,
            String fingerprint) {
        ReferencePurpose purpose = purposeOf(pack, quadrimestre, published);
        IndicatorRule rule = rule(pack);
        List<YearMonth> missing = LocalClasses.missingMonths(quadrimestre, months);
        if (!missing.isEmpty()) {
            return PackVerdict.pending(
                    pack, rule.descriptor().ruleVersion(), purpose, "faltam as entradas locais de " + missing);
        }
        return PackVerdict.evaluate(
                pack,
                rule.descriptor().ruleVersion(),
                purpose,
                ValidatedReference.fromPublicAggregate(snapshot, pack),
                LocalClasses.of(rule, quadrimestre, months),
                fingerprint);
    }

    /**
     * What the local classes of a quadrimestre were computed from: the {@code InputFingerprint} of
     * the extraction id and checksum of each extract manifest of its four months (C1's {@code -team}
     * supplement among them) in {@code extracts}.
     */
    private static String fingerprintOf(Path extracts, Quadrimestre quadrimestre) throws IOException {
        FileExtractStore store = new FileExtractStore(extracts);
        Map<String, String> checksums = new TreeMap<>();
        try (Stream<Path> files = Files.list(extracts)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(MANIFEST_SUFFIX)) {
                    continue;
                }
                ExtractionManifest manifest =
                        store.readManifest(name.substring(0, name.length() - MANIFEST_SUFFIX.length()));
                if (quadrimestre.months().stream()
                        .anyMatch(month -> manifest.extractionId().contains(month.toString()))) {
                    checksums.put(manifest.extractionId(), manifest.checksum());
                }
            }
        }
        return InputFingerprint.compute(checksums);
    }

    private static SiapsSnapshot readSnapshot(String file) throws IOException {
        return SiapsParser.snapshot(Files.readString(Path.of(file), StandardCharsets.UTF_8));
    }

    /** The quadrimestre of a pack: the property's, or the most recent eligible published one. */
    private static Optional<Quadrimestre> reference(GatePack pack, List<String> published) {
        String requested = System.getProperty(QUADRIMESTRE);
        if (requested == null || requested.isBlank()) {
            return Eligibility.reference(pack, published);
        }
        return Optional.of(SiapsFormats.quadrimestre(requested));
    }

    private static SiapsSnapshot fetched(
            SiapsClient client, Map<String, String> environment, String quadrimestre, Path out) throws IOException {
        String ibge = environment.get("PEC_MUNICIPALITY_IBGE");
        String uf = System.getProperty(UF, ufOf(ibge));
        String json = client.snapshot(uf, SiapsFormats.ibgeOfSiaps(ibge), quadrimestre);
        Files.writeString(out.resolve("snapshot-" + quadrimestre + ".json"), json, StandardCharsets.UTF_8);
        return SiapsParser.snapshot(json);
    }

    private static String ufOf(String ibge) {
        for (int at = 0; at < UF_CODES.length(); at += 4) {
            if (UF_CODES.startsWith(ibge.substring(0, 2), at)) {
                return UF_CODES.substring(at + 2, at + 4);
            }
        }
        throw new IllegalStateException("unknown state code in " + ibge + "; set -D" + UF);
    }

    /** The ungated monthly team results of every pack for the four months, by pack and month. */
    private static Map<String, Map<YearMonth, List<TeamResult>>> acquire(Quadrimestre quadrimestre, Path out, Pec pec)
            throws IOException {
        Path extracts = out.resolve("extratos");
        Files.createDirectories(extracts);
        Map<String, Map<YearMonth, List<TeamResult>>> byPack = new LinkedHashMap<>();
        for (YearMonth month : quadrimestre.months()) {
            Set<String> have = new HashSet<>();
            for (PackInput input : SensitivityExtracts.fromDirectory(extracts, month)) {
                have.add(input.rule().descriptor().id());
            }
            Set<String> missing = new HashSet<>();
            GatePack.all().forEach(pack -> missing.add(pack.packId()));
            missing.removeAll(have);
            if (!missing.isEmpty()) {
                SensitivityExtracts.acquire(extracts, month, missing, pec.environment(), pec.envFile(), pec.binary());
            }
            for (PackInput input : SensitivityExtracts.fromDirectory(extracts, month)) {
                List<TeamResult> teams =
                        input.rule().evaluate(input.data(), input.context()).teams();
                byPack.computeIfAbsent(input.rule().descriptor().id(), id -> new LinkedHashMap<>())
                        .put(month, teams);
            }
        }
        return byPack;
    }

    private static void record(List<PackVerdict> verdicts, Path out, LocalDate today) throws IOException {
        Path root = repoRoot();
        String registry = System.getProperty(REGISTRY);
        for (PackVerdict verdict : verdicts) {
            Path directory = verdict.isGateEvidence() ? root.resolve(EVIDENCE_DIR) : out.resolve("diagnostico");
            Optional<Path> summary = SummaryWriter.write(directory, verdict, today);
            RawWriter.write(out, verdict);
            log.info(
                    "Portão D {} {}: {}{}",
                    verdict.pack().code(),
                    verdict.purpose(),
                    verdict.status(),
                    verdict.reason().isEmpty() ? "" : " (" + verdict.reason() + ")");
            if (registry != null && !registry.isBlank() && verdict.purpose() == ReferencePurpose.GATE) {
                String ref = summary.map(
                                file -> root.relativize(file.toAbsolutePath().normalize())
                                        .toString()
                                        .replace('\\', '/'))
                        .orElse(null);
                String sha = summary.isPresent() ? SummaryWriter.sha256(summary.get()) : null;
                RegistryUpdater.record(Path.of(registry), root, verdict, today, ref, sha);
            }
        }
    }

    private static Path repoRoot() {
        String configured = System.getProperty(REPO_ROOT);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve(EVIDENCE_DIR))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("repository root not found; set -D" + REPO_ROOT);
        }
        return dir;
    }

    private static IndicatorRule rule(GatePack pack) {
        return IndicatorRuleRegistry.all().stream()
                .filter(rule -> rule.descriptor().id().equals(pack.packId()))
                .findFirst()
                .orElseThrow();
    }

    private static String ruleVersion(GatePack pack) {
        return pack.isNotaFinal()
                ? ComponentIII.RULE_VERSION
                : rule(pack).descriptor().ruleVersion();
    }
}
