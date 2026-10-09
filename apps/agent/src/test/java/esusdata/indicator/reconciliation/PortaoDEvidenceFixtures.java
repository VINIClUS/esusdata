package esusdata.indicator.reconciliation;

import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.reconciliation.Comparison.RowResult;
import esusdata.indicator.reconciliation.MethodologyProfile.Source;
import esusdata.indicator.reconciliation.PackVerdict.Status;
import esusdata.indicator.reconciliation.RegistryUpdater.CitedFile;
import esusdata.indicator.reconciliation.RegistryUpdater.EvidenceBundle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A repository tree of the evidence of a decided Portão D, made by the real writers and never by
 * hand: the manifests by {@link ReferenceCapture}, the dossier by {@link CompatibilityDossierWriter},
 * the policy with one {@code GATE} declaration that pins both, the set summary by {@link
 * SummaryWriter} and {@code release-gates.json} by {@link RegistryUpdater}. The checks of {@link
 * PortaoDEvidenceChecks} are tested on it, mutating one byte or planting an identifier, so that a
 * check cannot drift from what the writers emit.
 *
 * <p>The dossier's official source has a URL with a long digit run, as the real ones do (a DOU
 * number): the dossier writer allows it, so no check may refuse it.
 */
public final class PortaoDEvidenceFixtures {

    /** The day D is recorded as checked on. */
    public static final LocalDate DAY = LocalDate.of(2026, 10, 9);

    /** The quadrimestre of the reference in the fixture. */
    public static final String QUADRIMESTRE = "2026Q1";

    /** The local source the dossier was decided on. */
    public static final String FINGERPRINT = CompatibilityFixtures.FINGERPRINT;

    private static final String JSON = ".json";
    private static final String UF = "zz";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC);
    private static final Path REAL_REPO = Path.of("..", "..");
    private static final String POLICY = "contracts/indicators/siaps-reference-policy.json";
    private static final String REGISTRY = "contracts/indicators/release-gates.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PortaoDEvidenceFixtures() {}

    /**
     * The tree and what was decided in it.
     *
     * @param root the repository root
     * @param packId the pack the set is of
     * @param verdict the verdict of the set, as recorded
     * @param referenceId the one GATE reference
     */
    public record Tree(Path root, String packId, ReferenceSetVerdict verdict, String referenceId) {

        public Path policy() {
            return root.resolve(POLICY);
        }

        public Path registry() {
            return root.resolve(REGISTRY);
        }

        public Path manifest() {
            return root.resolve(ReferencePolicy.manifestPath(referenceId));
        }

        public Path dossierJson() {
            return root.resolve(ReferencePolicy.dossierPath(referenceId, packId));
        }

        public Path dossierMarkdown() {
            return root.resolve(ReferencePolicy.dossierPath(referenceId, packId).replace(JSON, ".md"));
        }

        public Path summaryJson() {
            return root.resolve(SummaryWriter.SET_SUMMARY_DIR)
                    .resolve(SummaryWriter.summaryFileName(verdict.ruleVersion(), JSON));
        }

        public Path summaryMarkdown() {
            return root.resolve(SummaryWriter.SET_SUMMARY_DIR)
                    .resolve(SummaryWriter.summaryFileName(verdict.ruleVersion(), ".md"));
        }

        /** What D of the tree cites: the summary as it is now, and the manifest and dossier the policy pins. */
        public EvidenceBundle bundle() throws IOException {
            ReferenceSet set = loadedPolicy().referenceSet(packId, verdict.ruleVersion());
            ReferenceDeclaration declaration = set.references().getFirst();
            return new EvidenceBundle(
                    SummaryWriter.SET_SUMMARY_DIR + SummaryWriter.summaryFileName(verdict.ruleVersion(), JSON),
                    SummaryWriter.sha256(summaryJson()),
                    set.gateSetSha256(),
                    List.of(
                            new CitedFile(
                                    ReferencePolicy.manifestPath(referenceId), declaration.referenceManifestSha256()),
                            new CitedFile(
                                    declaration.compatibilityEvidenceRef(),
                                    declaration.compatibilityEvidenceSha256())));
        }

        /** The policy of the tree, as the loader reads it. */
        public ReferencePolicy loadedPolicy() throws IOException {
            return ReferencePolicy.load(policy(), ReleaseGateRegistry.registeredPacks());
        }
    }

    /**
     * Builds the tree of a pack whose set has one GATE reference, an {@code EXACT} dossier, and the
     * given decided status recorded as D.
     *
     * @param workspace an empty directory; the repository goes under {@code repo} and the capture's
     *     artifact store under {@code artifacts}
     * @param status {@code PASSED} or {@code FAILED}
     */
    public static Tree decided(Path workspace, PackDescriptor descriptor, Status status) throws IOException {
        Path root = Files.createDirectories(workspace.resolve("repo"));
        String referenceId = capture(workspace, root, descriptor);
        byte[] manifest = Files.readAllBytes(root.resolve(ReferencePolicy.manifestPath(referenceId)));
        String dossierSha = writeDossier(root, descriptor, referenceId, SummaryWriter.sha256(manifest));
        declare(root, descriptor, referenceId, SummaryWriter.sha256(manifest), dossierSha);
        Files.copy(REAL_REPO.resolve(REGISTRY), root.resolve(REGISTRY));
        Tree partial = new Tree(root, descriptor.id(), null, referenceId);
        ReferenceSet set = partial.loadedPolicy().referenceSet(descriptor.id(), descriptor.ruleVersion());
        DossierEvidence dossier = DossierEvidence.read(
                        ReferencePolicy.dossierPath(referenceId, descriptor.id()),
                        Files.readAllBytes(partial.dossierJson()))
                .orElseThrow();
        ReferenceSetVerdict verdict = ReferenceSetVerdict.aggregate(
                set, Map.of(referenceId, localVerdict(descriptor, status)), Map.of(referenceId, dossier));
        Tree tree = new Tree(root, descriptor.id(), verdict, referenceId);
        record(tree);
        return tree;
    }

    /** The packs's ids: the Nota Final or one of C1 to C7. */
    private static GatePack packOf(PackDescriptor descriptor) {
        return GatePack.byPackId(descriptor.id()).orElse(GatePack.NOTA_FINAL);
    }

    private static String capture(Path workspace, Path root, PackDescriptor descriptor) throws IOException {
        Path exports = Files.createDirectories(workspace.resolve("exports"));
        Files.write(
                exports.resolve("q1-2026.csv"),
                SiapsTeamExportFixtures.standard().bytes());
        new ReferenceCapture(
                        Files.createDirectories(workspace.resolve("artifacts")),
                        Files.createDirectories(root.resolve(ReferencePolicy.MANIFEST_DIR)),
                        UF,
                        CLOCK)
                .capture(exports, SiapsTeamExportFixtures.MUNICIPALITY_IBGE);
        String referenceId = UF + "-" + SiapsTeamExportFixtures.MUNICIPALITY_IBGE + "-2026q1-"
                + ReferencePolicy.packCode(descriptor) + "-team-r1";
        // the capture registers every pack of the export; the tree holds the one reference the set cites
        try (Stream<Path> manifests = Files.list(root.resolve(ReferencePolicy.MANIFEST_DIR))) {
            for (Path manifest : manifests.toList()) {
                if (!manifest.getFileName().toString().equals(referenceId + JSON)) {
                    Files.delete(manifest);
                }
            }
        }
        return referenceId;
    }

    private static String writeDossier(Path root, PackDescriptor descriptor, String referenceId, String manifestSha)
            throws IOException {
        CompatibilityDossier clean = CompatibilityFixtures.evidence(
                        CompatibilityFixtures.profile(OfficialReading.SAME, OfficialReading.SAME, false))
                .dossier();
        Source source = new Source(
                "dou-2026",
                "Portaria GM/MS",
                "https://www.in.gov.br/web/dou/-/portaria-gm-ms-705373819",
                LocalDate.of(2026, 5, 13),
                Source.DocumentKind.ORDINANCE);
        CompatibilityDossier dossier = new CompatibilityDossier(
                referenceId,
                descriptor.id(),
                descriptor.ruleVersion(),
                QUADRIMESTRE,
                manifestSha,
                FINGERPRINT,
                List.of(source),
                clean.normativeDeltas(),
                clean.probeResults(),
                clean.officialFieldComparison(),
                clean.coverage(),
                clean.verdict(),
                clean.reason(),
                clean.declaredLimitations(),
                clean.declaredConventions(),
                clean.siblingVerdicts());
        String json = ReferencePolicy.dossierPath(referenceId, descriptor.id());
        return CompatibilityDossierWriter.write(root.resolve(json), root.resolve(json.replace(JSON, ".md")), dossier)
                .jsonSha256();
    }

    /** The policy of the repository plus one GATE declaration in the set of the pack. */
    private static void declare(
            Path root, PackDescriptor descriptor, String referenceId, String manifestSha, String dossierSha)
            throws IOException {
        ObjectNode policy = (ObjectNode) MAPPER.readTree(Files.readString(REAL_REPO.resolve(POLICY)));
        ObjectNode declaration = MAPPER.createObjectNode();
        declaration.put("reference_id", referenceId);
        declaration.put("quadrimestre", QUADRIMESTRE);
        declaration.put("municipality_ibge", SiapsTeamExportFixtures.MUNICIPALITY_IBGE);
        declaration.put("source_kind", "OFFICIAL_TEAM_EXPORT_CSV");
        declaration.put("purpose", "GATE");
        declaration.put("required", true);
        declaration.put("status", "ACTIVE");
        declaration.put("compatibility", "EXACT");
        declaration.put("reference_manifest_sha256", manifestSha);
        declaration.put("compatibility_evidence_ref", ReferencePolicy.dossierPath(referenceId, descriptor.id()));
        declaration.put("compatibility_evidence_sha256", dossierSha);
        for (tools.jackson.databind.JsonNode set : policy.path("reference_sets")) {
            if (descriptor.ruleVersion().equals(set.path("rule_version").asString())) {
                ((ArrayNode) set.path("references")).add(declaration);
            }
        }
        Path file = root.resolve(POLICY);
        Files.createDirectories(file.getParent());
        Files.writeString(file, MAPPER.writeValueAsString(policy) + "\n", StandardCharsets.UTF_8);
    }

    /** A local verdict for the one reference: agreeing figures for PASSED, a distribution far off for FAILED. */
    private static PackVerdict localVerdict(PackDescriptor descriptor, Status status) {
        // as many teams as the standard export has (two eSF, one eAP); a failed set fails on eSF only
        ClassCounts esf = new ClassCounts(0, 0, 0, 2);
        ClassCounts eap = new ClassCounts(0, 0, 1, 0);
        boolean passed = status == Status.PASSED;
        List<RowResult> rows = new ArrayList<>();
        rows.add(Comparison.row(SiapsParser.ESF, esf, passed ? esf : new ClassCounts(2, 0, 0, 0), 0));
        rows.add(Comparison.row(SiapsParser.EAP, eap, passed ? eap : new ClassCounts(0, 1, 0, 0), 0));
        return new PackVerdict(
                packOf(descriptor),
                descriptor.ruleVersion(),
                ReferencePurpose.GATE,
                status,
                "",
                QUADRIMESTRE,
                rows,
                0,
                List.of(),
                FINGERPRINT);
    }

    /** Writes the summary and then D, through the same bundle the gate runner builds. */
    private static void record(Tree tree) throws IOException {
        SummaryWriter.writeSet(tree.root().resolve(SummaryWriter.SET_SUMMARY_DIR), tree.verdict(), DAY);
        RegistryUpdater.record(tree.registry(), tree.root(), tree.verdict(), DAY, tree.bundle());
    }

    /** The text of a file of the tree. */
    public static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** Appends {@code text} to a file. */
    public static void append(Path file, String text) throws IOException {
        Files.writeString(file, read(file) + text, StandardCharsets.UTF_8);
    }

    /** Replaces the first occurrence of {@code target} in a file, which must be there. */
    public static void replaceIn(Path file, String target, String replacement) throws IOException {
        String text = read(file);
        if (!text.contains(target)) {
            throw new IllegalArgumentException(file.getFileName() + " does not contain " + target);
        }
        Files.writeString(
                file,
                text.replaceFirst(
                        java.util.regex.Pattern.quote(target), java.util.regex.Matcher.quoteReplacement(replacement)),
                StandardCharsets.UTF_8);
    }

    /** The first declaration of the set of the pack that is a GATE one. */
    public static Optional<ReferenceDeclaration> gateDeclaration(ReferencePolicy policy, String packId, String rule) {
        return policy.referenceSet(packId, rule).references().stream()
                .filter(declaration -> declaration.purpose() == ReferencePurpose.GATE)
                .findFirst();
    }
}
