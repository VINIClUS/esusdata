package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.PackVerdict.Mode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Records the Portão D of a pack in a {@code release-gates.json}-shaped file: the entry of the pack
 * and rule version gets {@code gates.D = {status, check, checked_at, evidence}}, and nothing else
 * changes (gate A, {@code blocking_gaps_closed} and any unknown field survive). The root is an array
 * of entries or an object holding one. An entry that does not exist is never created, and an
 * informative verdict is never recorded.
 */
public final class RegistryUpdater {

    static final String EVIDENCE_KIND = "conciliacao-siaps";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RegistryUpdater() {}

    /**
     * Records the D gate of one pack.
     *
     * @param evidenceRef the repository-relative path of the summary document
     * @param evidenceSha256 its SHA-256, lowercase hex
     */
    public static void record(
            Path registry, PackVerdict verdict, LocalDate checkedAt, String evidenceRef, String evidenceSha256)
            throws IOException {
        if (verdict.mode() == Mode.INFORMATIVO) {
            throw new IllegalArgumentException("an informative result is never written to the registry");
        }
        boolean decided = verdict.status() != PackVerdict.Status.PENDING;
        if (decided && (evidenceRef == null || evidenceSha256 == null || !evidenceSha256.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("a decided gate needs its evidence document and sha256");
        }
        JsonNode root = MAPPER.readTree(Files.readString(registry, StandardCharsets.UTF_8));
        ObjectNode entry = find(root, verdict.pack().packId(), verdict.ruleVersion());
        JsonNode existing = entry.path("gates");
        ObjectNode gates = existing instanceof ObjectNode object ? object : entry.putObject("gates");
        ObjectNode gate = MAPPER.createObjectNode();
        gate.put("status", verdict.status().name());
        gate.put("check", Comparison.CHECK_ID);
        gate.put("checked_at", checkedAt.toString());
        ArrayNode evidence = gate.putArray("evidence");
        if (decided) {
            ObjectNode item = evidence.addObject();
            item.put("kind", EVIDENCE_KIND);
            item.put("ref", evidenceRef);
            item.put("sha256", evidenceSha256);
        }
        gates.set("D", gate);
        Files.writeString(
                registry,
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
                StandardCharsets.UTF_8);
    }

    private static ObjectNode find(JsonNode root, String pack, String ruleVersion) {
        for (JsonNode candidate : entries(root)) {
            if (pack.equals(candidate.path("pack").asString())
                    && ruleVersion.equals(candidate.path("rule_version").asString())
                    && candidate instanceof ObjectNode entry) {
                return entry;
            }
        }
        throw new IllegalArgumentException("no registry entry for " + pack + " " + ruleVersion);
    }

    /** The root itself when it is a list, otherwise the first list the root object holds. */
    private static JsonNode entries(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        for (String key : root.propertyNames()) {
            if (root.path(key).isArray()) {
                return root.path(key);
            }
        }
        return MAPPER.createArrayNode();
    }
}
