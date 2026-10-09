package esusdata.indicator.reconciliation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Records the Portão D of a pack in {@code contracts/indicators/release-gates.json} (ADR 0032 and
 * ADR 0034, schema {@code release-gates.schema.json}): the root is {@code {schema_version, packs:
 * [...]}} and the entry of the pack and rule version gets {@code gates.D}, from the verdict of its
 * pre-registered reference set ({@link ReferenceSetVerdict}). A decided gate is {@code {status,
 * check, checked_at, evidence: [{kind, ref, sha256}]}}, citing the set summary; a PENDING one
 * carries only {@code status} and an empty {@code evidence}, as the schema requires. Everything
 * else (gate A, {@code blocking_gaps_closed}, other entries) is left as it was. An entry that does
 * not exist is never created, and every file the decision stands on must exist in the repository
 * with the SHA-256 the bundle gives, the same check {@code ReleaseGatesConsistencyTest} runs.
 */
public final class RegistryUpdater {

    static final String EVIDENCE_KIND = "conciliacao-siaps";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RegistryUpdater() {}

    /** A file the decision stands on: its repository-relative path and its SHA-256, lowercase hex. */
    public record CitedFile(String ref, String sha256) {}

    /**
     * What a decided gate cites: the summary of the set (the evidence D carries), the hash of the set
     * it was decided for, and every manifest and dossier the summary stands on.
     *
     * @param summaryRef the repository-relative path of the set summary JSON
     * @param summarySha256 its SHA-256
     * @param gateSetSha256 the {@code gate_set_sha256} the summary was made for
     * @param cited the manifest and the dossier of every GATE reference
     */
    public record EvidenceBundle(String summaryRef, String summarySha256, String gateSetSha256, List<CitedFile> cited) {

        public EvidenceBundle {
            cited = List.copyOf(cited);
        }

        /** The bundle of a pending gate, which cites nothing. */
        public static EvidenceBundle none() {
            return new EvidenceBundle(null, null, null, List.of());
        }
    }

    /**
     * Records the D gate of one pack.
     *
     * @param repoRoot the repository root the evidence references are relative to
     * @param bundle what the decision stands on; ignored for a pending set
     * @throws IllegalArgumentException when the check is not the one of the set, the status does not
     *     follow from the references, the bundle is for another set, or a file it cites is missing or
     *     changed
     */
    public static void record(
            Path registry, Path repoRoot, ReferenceSetVerdict verdict, LocalDate checkedAt, EvidenceBundle bundle)
            throws IOException {
        if (!ReferencePolicy.checkFor(verdict.pack()).equals(verdict.check())) {
            throw new IllegalArgumentException("the check of " + verdict.pack() + " is "
                    + ReferencePolicy.checkFor(verdict.pack()) + ", not " + verdict.check());
        }
        if (ReferenceSetVerdict.statusOf(verdict.references()) != verdict.status()) {
            throw new IllegalArgumentException(
                    "the set verdict " + verdict.status() + " does not follow from the verdicts of its references");
        }
        boolean decided = verdict.status() != PackVerdict.Status.PENDING;
        if (decided) {
            requireBundle(repoRoot, verdict, bundle);
        }
        JsonNode root = MAPPER.readTree(Files.readString(registry, StandardCharsets.UTF_8));
        ObjectNode entry = find(root, verdict.pack(), verdict.ruleVersion());
        JsonNode gates = entry.path("gates");
        if (!(gates instanceof ObjectNode gatesObject)) {
            throw new IllegalArgumentException("registry entry " + verdict.ruleVersion() + " has no gates object");
        }
        ObjectNode gate = MAPPER.createObjectNode();
        gate.put("status", verdict.status().name());
        if (decided) {
            gate.put("check", verdict.check());
            gate.put("checked_at", checkedAt.toString());
        }
        ArrayNode evidence = gate.putArray("evidence");
        if (decided) {
            ObjectNode item = evidence.addObject();
            item.put("kind", EVIDENCE_KIND);
            item.put("ref", bundle.summaryRef());
            item.put("sha256", bundle.summarySha256());
        }
        gatesObject.set("D", gate);
        Files.writeString(registry, write(root) + "\n", StandardCharsets.UTF_8);
    }

    private static void requireBundle(Path repoRoot, ReferenceSetVerdict verdict, EvidenceBundle bundle)
            throws IOException {
        if (bundle == null || !verdict.gateSetSha256().equals(bundle.gateSetSha256())) {
            throw new IllegalArgumentException(
                    "the evidence is not for the gate set that was decided: gate_set_sha256");
        }
        requireEvidence(repoRoot, bundle.summaryRef(), bundle.summarySha256());
        for (CitedFile file : bundle.cited()) {
            requireEvidence(repoRoot, file.ref(), file.sha256());
        }
        for (ReferenceSetVerdict.ReferenceOutcome outcome : verdict.references()) {
            requireCited(
                    bundle, ReferencePolicy.manifestPath(outcome.referenceId()), outcome.referenceManifestSha256());
            requireCited(bundle, outcome.dossierRef(), outcome.dossierSha256());
        }
    }

    private static void requireCited(EvidenceBundle bundle, String ref, String sha256) {
        if (!bundle.cited().contains(new CitedFile(ref, sha256))) {
            throw new IllegalArgumentException("the evidence does not cite " + ref + " as the set pins it");
        }
    }

    private static void requireEvidence(Path repoRoot, String ref, String sha256) throws IOException {
        if (ref == null || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("a decided gate needs its evidence document and sha256");
        }
        Path document = repoRoot.resolve(ref).normalize();
        if (!document.startsWith(repoRoot.normalize()) || !Files.isRegularFile(document)) {
            throw new IllegalArgumentException("evidence document does not exist in the repository: " + ref);
        }
        if (!SummaryWriter.sha256(document).equals(sha256)) {
            throw new IllegalArgumentException("evidence sha256 does not match the document: " + ref);
        }
    }

    /**
     * The registry as the repository keeps it: two-space indentation, one array element per line, an
     * explicit {@code "\n"} (the printer of every versioned JSON of the Portão D). Recording a gate
     * that is already what the file says changes not a byte of it.
     */
    private static String write(JsonNode root) {
        return MAPPER.writer().with(CompatibilityDossierWriter.printer()).writeValueAsString(root);
    }

    private static ObjectNode find(JsonNode root, String pack, String ruleVersion) {
        for (JsonNode candidate : root.path("packs")) {
            if (pack.equals(candidate.path("pack").asString())
                    && ruleVersion.equals(candidate.path("rule_version").asString())
                    && candidate instanceof ObjectNode entry) {
                return entry;
            }
        }
        throw new IllegalArgumentException("no registry entry for " + pack + " " + ruleVersion);
    }
}
