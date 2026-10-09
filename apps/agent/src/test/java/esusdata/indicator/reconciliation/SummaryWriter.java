package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.Comparison.RowResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The summary document of one pack's Portão D result, the evidence the gate points at: one row per
 * indicator and team type with N_S and N_L (written {@code <10} below 10), D, T and the verdict.
 * Per-class counts and per-INE classes never appear here (see {@link RawWriter}).
 */
public final class SummaryWriter {

    static final String MASKED = "<10";
    static final int MASK_BELOW = 10;

    /** Where the summary of each reference set lives, by rule version, relative to the repository root. */
    static final String SET_SUMMARY_DIR = "docs/indicadores/portoes/resultado-d/";

    /** The {@code schema_version} of the set summary. */
    static final String SET_SUMMARY_SCHEMA_VERSION = "1";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** The d and t of a row that was not evaluated. */
    static final String DASH = "-";

    /** The verdict of a row whose distance is within the threshold. */
    static final String ROW_PASSES = "passa";

    /** The verdict of a row whose distance exceeds the threshold. */
    static final String ROW_FAILS = "reprova";

    /** The verdict of a row with nothing to compare. */
    static final String ROW_NOT_EVALUATED = "não avaliada";

    private static final String STATUS = "status";
    private static final String REASON = "reason";
    private static final String RULE_VERSION = "rule_version";
    private static final String GATE_SET_SHA256 = "gate_set_sha256";
    private static final String COMPATIBILITY = "compatibility";
    private static final String LOCAL_SOURCE_FINGERPRINT = "local_source_fingerprint";
    private static final String OFFICIAL_FIELD_COMPARISON = "official_field_comparison";
    private static final String ROWS = "rows";
    private static final String REFERENCES = "references";
    private static final String TEAM_TYPE = "team_type";

    private SummaryWriter() {}

    /** A count as the document shows it. */
    public static String mask(int count) {
        return count < MASK_BELOW ? MASKED : Integer.toString(count);
    }

    /** The file name of a verdict's summary. */
    public static String fileName(PackVerdict verdict) {
        return (verdict.purpose() == ReferencePurpose.DIAGNOSTIC ? "diagnostico-" : "") + "portao-d-"
                + verdict.pack().packId() + "-" + verdict.quadrimestre() + ".md";
    }

    /**
     * Writes the summary of a verdict that compared something into {@code directory}; a pending
     * verdict without a reference writes nothing (a pending gate carries no evidence).
     */
    public static Optional<Path> write(Path directory, PackVerdict verdict, LocalDate date) throws IOException {
        if (verdict.quadrimestre() == null) {
            return Optional.empty();
        }
        Files.createDirectories(directory);
        Path file = directory.resolve(fileName(verdict));
        Files.writeString(file, render(verdict, date), StandardCharsets.UTF_8);
        return Optional.of(file);
    }

    public static String render(PackVerdict verdict, LocalDate date) {
        StringBuilder text = new StringBuilder(2048);
        text.append("# Portão D: conciliação com o SIAPS, %s (%s)\n\n"
                .formatted(verdict.pack().code(), verdict.quadrimestre()));
        if (verdict.purpose() == ReferencePurpose.DIAGNOSTIC) {
            text.append("""
                    > **Modo diagnóstico: não é evidência do Portão D.** A referência não decide o portão (não é
                    > uma referência de gate, ou a execução foi exploratória) e este documento nunca entra no
                    > registro de portões.

                    """);
        }
        String reason = verdict.reason().isEmpty() ? "" : " (" + verdict.reason() + ")";
        text.append("""
                - Check: `%s`
                - Pack: `%s`, regra `%s`
                - Quadrimestre de referência no SIAPS: %s
                - Data: %s
                %s- Veredito do pack: **%s**%s

                | Indicador | Tipo | N_S | N_L | Sem classe local | D | T | Veredito |
                |---|---|---|---|---|---|---|---|
                """.formatted(
                        verdict.pack().checkId(),
                        verdict.pack().packId(),
                        verdict.ruleVersion(),
                        verdict.quadrimestre(),
                        date,
                        fingerprintLine(verdict),
                        verdict.status(),
                        reason));
        for (RowResult row : verdict.rows()) {
            text.append(tableRow(verdict.pack().code(), row)).append('\n');
        }
        text.append(
                """

                Equipes locais fora da lista do SIAPS (excluídas): %s.

                Contagens por classe e classes por equipe ficam só no diretório local ignorado pelo controle de versão.
                Regra: `%s`.
                """.formatted(mask(verdict.localNotInSiaps()), verdict.pack().ruleDocument()));
        return text.toString();
    }

    /** The line that says which local extracts the figures come from, when the verdict read any. */
    private static String fingerprintLine(PackVerdict verdict) {
        return verdict.localSourceFingerprint().isEmpty()
                ? ""
                : "- Fingerprint da fonte local: `" + verdict.localSourceFingerprint() + "`\n";
    }

    private static String tableRow(String code, RowResult row) {
        String distance = row.evaluated() ? Integer.toString(row.distance()) : "-";
        String threshold = row.evaluated() ? Integer.toString(row.threshold()) : "-";
        return "| "
                + String.join(
                        " | ",
                        code,
                        row.teamType(),
                        mask(row.siapsTeams()),
                        mask(row.localTeams()),
                        mask(row.semClasseLocal()),
                        distance,
                        threshold,
                        verdictOf(row))
                + " |";
    }

    private static String verdictOf(RowResult row) {
        if (row.evaluated()) {
            return row.passed() ? ROW_PASSES : ROW_FAILS;
        }
        return ROW_NOT_EVALUATED;
    }

    // ---- the summary of a reference set

    /** The files of one set summary: the JSON is the evidence, the Markdown is rendered from it. */
    public record WrittenSetSummary(Path json, Path markdown, String jsonSha256) {}

    /** The file name of the summary of a rule version, with its extension ({@code .json} or {@code .md}). */
    public static String summaryFileName(String ruleVersion, String extension) {
        return ruleVersion + extension;
    }

    /**
     * Writes the summary of a set into {@code directory}: the JSON, which is the evidence D cites, and
     * the Markdown rendered from those very bytes.
     */
    public static WrittenSetSummary writeSet(Path directory, ReferenceSetVerdict verdict, LocalDate checkedAt)
            throws IOException {
        Files.createDirectories(directory);
        byte[] json = summaryJson(verdict, checkedAt);
        Path jsonFile = directory.resolve(summaryFileName(verdict.ruleVersion(), ".json"));
        Path markdownFile = directory.resolve(summaryFileName(verdict.ruleVersion(), ".md"));
        Files.write(jsonFile, json);
        Files.writeString(markdownFile, renderSetMarkdown(json), StandardCharsets.UTF_8);
        return new WrittenSetSummary(jsonFile, markdownFile, sha256(json));
    }

    /**
     * The JSON of a set summary: UTF-8, {@code "\n"} line ends, one trailing newline. Counts are text,
     * masked below 10 as in the per-pack summary. The per-team lines of the local verdicts, which
     * carry INEs, never appear: only the figures per team type and the dossier's own masked comparison.
     */
    public static byte[] summaryJson(ReferenceSetVerdict verdict, LocalDate checkedAt) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schema_version", SET_SUMMARY_SCHEMA_VERSION);
        root.put("pack", verdict.pack());
        root.put(RULE_VERSION, verdict.ruleVersion());
        root.put("check", verdict.check());
        root.put(GATE_SET_SHA256, verdict.gateSetSha256());
        root.put(STATUS, verdict.status().name());
        root.put(REASON, verdict.reason());
        root.put("checked_at", checkedAt.toString());
        ArrayNode references = root.putArray(REFERENCES);
        verdict.references().forEach(outcome -> references.add(outcomeJson(outcome)));
        return (MAPPER.writer().with(CompatibilityDossierWriter.printer()).writeValueAsString(root) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static ObjectNode outcomeJson(ReferenceSetVerdict.ReferenceOutcome outcome) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("reference_id", outcome.referenceId());
        node.put("reference_manifest_ref", ReferencePolicy.manifestPath(outcome.referenceId()));
        node.put("reference_manifest_sha256", outcome.referenceManifestSha256());
        node.put("compatibility_evidence_ref", outcome.dossierRef());
        node.put("compatibility_evidence_sha256", outcome.dossierSha256());
        node.put(COMPATIBILITY, outcome.compatibility().name());
        node.put(LOCAL_SOURCE_FINGERPRINT, outcome.localSourceFingerprint());
        node.put(STATUS, outcome.status().name());
        node.put(REASON, outcome.reason());
        ArrayNode rows = node.putArray(ROWS);
        outcome.rows().forEach(row -> rows.add(rowJson(row)));
        if (!outcome.officialFieldComparison().isMissingNode()) {
            node.set(
                    OFFICIAL_FIELD_COMPARISON, outcome.officialFieldComparison().deepCopy());
        }
        return node;
    }

    private static ObjectNode rowJson(RowResult row) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(TEAM_TYPE, row.teamType());
        node.put("n_s", mask(row.siapsTeams()));
        node.put("n_l", mask(row.localTeams()));
        node.put("sem_classe_local", mask(row.semClasseLocal()));
        node.put("d", row.evaluated() ? Integer.toString(row.distance()) : DASH);
        node.put("t", row.evaluated() ? Integer.toString(row.threshold()) : DASH);
        node.put("row_verdict", verdictOf(row));
        return node;
    }

    /**
     * The Markdown of a set summary, rendered from its JSON bytes alone: what the committed {@code
     * .md} must be, byte for byte.
     */
    public static String renderSetMarkdown(byte[] json) {
        JsonNode root = MAPPER.readTree(json);
        List<String> lines = new ArrayList<>();
        lines.add("# Portão D: resultado do conjunto de referências, " + field(root, RULE_VERSION));
        lines.add("");
        lines.add("- Check: `" + field(root, "check") + "`");
        lines.add("- Pack: `" + field(root, "pack") + "`, regra `" + field(root, RULE_VERSION) + "`");
        lines.add("- Hash do conjunto de gate (gate_set_sha256): `" + field(root, GATE_SET_SHA256) + "`");
        lines.add("- Data: " + field(root, "checked_at"));
        lines.add("- Veredito do conjunto: **" + field(root, STATUS) + "**" + reasonOf(root));
        lines.add("- Esquema: " + field(root, "schema_version"));
        lines.add("");
        for (JsonNode reference : root.get(REFERENCES).values()) {
            referenceMarkdown(lines, reference);
        }
        lines.add("Contagens abaixo de 10 aparecem como `<10`. Contagens por classe e classes por equipe ficam só no"
                + " diretório local ignorado pelo controle de versão.");
        return String.join("\n", lines) + "\n";
    }

    private static void referenceMarkdown(List<String> lines, JsonNode reference) {
        lines.add("## " + field(reference, "reference_id"));
        lines.add("");
        lines.add("- Veredito: **" + field(reference, STATUS) + "**" + reasonOf(reference));
        lines.add("- Compatibilidade: " + field(reference, COMPATIBILITY));
        lines.add("- Manifesto: `" + field(reference, "reference_manifest_ref") + "` (SHA-256 `"
                + field(reference, "reference_manifest_sha256") + "`)");
        lines.add("- Dossiê: `" + field(reference, "compatibility_evidence_ref") + "` (SHA-256 `"
                + field(reference, "compatibility_evidence_sha256") + "`)");
        lines.add("- Fingerprint da fonte local: `" + field(reference, LOCAL_SOURCE_FINGERPRINT) + "`");
        lines.add("");
        if (reference.get(ROWS).isEmpty()) {
            lines.add("Sem linhas avaliadas.");
        } else {
            lines.add("| Tipo | N_S | N_L | Sem classe local | D | T | Veredito |");
            lines.add("|---|---|---|---|---|---|---|");
            for (JsonNode row : reference.get(ROWS).values()) {
                lines.add("| "
                        + String.join(
                                " | ",
                                field(row, TEAM_TYPE),
                                field(row, "n_s"),
                                field(row, "n_l"),
                                field(row, "sem_classe_local"),
                                field(row, "d"),
                                field(row, "t"),
                                field(row, "row_verdict"))
                        + " |");
            }
        }
        lines.add("");
        if (reference.has(OFFICIAL_FIELD_COMPARISON)) {
            lines.add("Comparação com os campos oficiais (diagnóstico, fora do veredito):");
            lines.add("");
            JsonNode comparison = reference.get(OFFICIAL_FIELD_COMPARISON);
            for (String name : comparison.propertyNames()) {
                lines.add("- " + name + ": " + field(comparison, name));
            }
            lines.add("");
        }
    }

    private static String field(JsonNode node, String name) {
        return node.get(name).stringValue();
    }

    private static String reasonOf(JsonNode node) {
        String reason = field(node, REASON);
        return reason.isEmpty() ? "" : " (" + reason + ")";
    }

    /** The lowercase hex SHA-256 of a file. */
    public static String sha256(Path file) throws IOException {
        return sha256(Files.readAllBytes(file));
    }

    /** The lowercase hex SHA-256 of {@code content}. */
    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
