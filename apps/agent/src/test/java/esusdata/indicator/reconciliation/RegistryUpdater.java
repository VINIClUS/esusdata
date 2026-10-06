package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.PackVerdict.Mode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Records the Portão D of a pack in {@code contracts/indicators/release-gates.json} (ADR 0032, schema
 * {@code release-gates.schema.json}): the root is {@code {schema_version, packs: [...]}} and the
 * entry of the pack and rule version gets {@code gates.D}. A decided gate is {@code {status, check,
 * checked_at, evidence: [{kind, ref, sha256}]}}; a PENDING one carries only {@code status} and an
 * empty {@code evidence}, as the schema requires. Everything else (gate A, {@code
 * blocking_gaps_closed}, other entries) is left as it was. An entry that does not exist is never
 * created, an informative verdict is never recorded, and the evidence document must exist in the
 * repository with the given SHA-256, the same check {@code ReleaseGatesConsistencyTest} runs.
 */
public final class RegistryUpdater {

    static final String EVIDENCE_KIND = "conciliacao-siaps";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RegistryUpdater() {}

    /**
     * Records the D gate of one pack.
     *
     * @param repoRoot the repository root the evidence reference is relative to
     * @param evidenceRef the repository-relative path of the summary document
     * @param evidenceSha256 its SHA-256, lowercase hex
     */
    public static void record(
            Path registry,
            Path repoRoot,
            PackVerdict verdict,
            LocalDate checkedAt,
            String evidenceRef,
            String evidenceSha256)
            throws IOException {
        if (verdict.mode() == Mode.INFORMATIVO) {
            throw new IllegalArgumentException("an informative result is never written to the registry");
        }
        boolean decided = verdict.status() != PackVerdict.Status.PENDING;
        if (decided) {
            requireEvidence(repoRoot, evidenceRef, evidenceSha256);
        }
        JsonNode root = MAPPER.readTree(Files.readString(registry, StandardCharsets.UTF_8));
        ObjectNode entry = find(root, verdict.pack().packId(), verdict.ruleVersion());
        JsonNode gates = entry.path("gates");
        if (!(gates instanceof ObjectNode gatesObject)) {
            throw new IllegalArgumentException("registry entry " + verdict.ruleVersion() + " has no gates object");
        }
        ObjectNode gate = MAPPER.createObjectNode();
        gate.put("status", verdict.status().name());
        if (decided) {
            gate.put("check", Comparison.CHECK_ID);
            gate.put("checked_at", checkedAt.toString());
        }
        ArrayNode evidence = gate.putArray("evidence");
        if (decided) {
            ObjectNode item = evidence.addObject();
            item.put("kind", EVIDENCE_KIND);
            item.put("ref", evidenceRef);
            item.put("sha256", evidenceSha256);
        }
        gatesObject.set("D", gate);
        Files.writeString(registry, write(root) + "\n", StandardCharsets.UTF_8);
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

    private static String write(JsonNode root) {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(Separators.createDefaultInstance()
                .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator(""));
        return MAPPER.writer().with(printer).writeValueAsString(root);
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
