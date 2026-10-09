package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.CompatibilityDossier.ConventionEntry;
import esusdata.indicator.reconciliation.CompatibilityDossier.NormativeDelta;
import esusdata.indicator.reconciliation.CompatibilityDossier.ProbeEntry;
import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredLimitation;
import esusdata.indicator.reconciliation.MethodologyProfile.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes a {@link CompatibilityDossier} as deterministic UTF-8 JSON (keys sorted, {@code "\n"}
 * line ends, one trailing newline, so the same dossier is the same bytes on every machine) and as a
 * Markdown rendering of that JSON. Both hold masked counts only (below 10 reads {@code <10}).
 *
 * <p>Privacy guard (spec section 8.4): nothing is written if any key or value of the versioned JSON
 * looks like an INE or a CNES (a run of 7 or more digits), a person key (a key named for one, a
 * UUID, a hex digest that is not the manifest hash or the source fingerprint) or a raw source
 * record id. The message names the path in the document, never the offending value.
 *
 * <p>The local detail of the probes and of the field comparison, which carries INEs, is written
 * only when a target is given, and never under a {@code docs} directory.
 */
public final class CompatibilityDossierWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ID = "id";
    private static final String LOCAL_READING = "local_reading";
    private static final String DECISION_REFS = "decision_refs";
    private static final String PROBE_ID = "probe_id";
    private static final String PROBE_RESULT = "probe_result";
    private static final String REASON = "reason";
    private static final String TITLE = "title";
    private static final String URL = "url";
    private static final String DASH = "-";

    /** Hashes the dossier carries as such; a hex digest anywhere else could be a person key. */
    private static final Map<String, Pattern> HASH_KEYS = Map.of(
            "reference_manifest_sha256", Pattern.compile("[0-9a-f]{64}"),
            "local_source_fingerprint", Pattern.compile("sha256:[0-9a-f]{64}"));

    private static final Pattern REFERENCE_ID =
            Pattern.compile("[a-z]{2}-\\d{7}-\\d{4}q[1-3]-(c[1-7]|ciii)-(team|agg|aggu)-r[1-9]\\d*");
    /**
     * The number of a public document of the Ministry in its SEI system, as the titles of the fichas
     * and notes cite it ({@code SEI 0055690090}): a document, never a person, a team or a record.
     */
    private static final Pattern SEI_DOCUMENT = Pattern.compile("\\bSEI (?:n[º°o] ?)?\\d{7,}");

    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{7,}");
    private static final Pattern UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HEX_DIGEST = Pattern.compile("[0-9a-fA-F]{32,}");
    private static final Pattern PERSON_OR_RECORD_KEY = Pattern.compile(
            "(^|_)(ine|cnes|cns|cpf|person|pessoa|cidadao|cidadaos|record_id|raw_id|co_seq\\w*|nu_\\w+)(_|$)");

    private CompatibilityDossierWriter() {}

    /** What was written. */
    public record WrittenDossier(Path json, Path markdown, Optional<Path> localDetail, String jsonSha256) {}

    /** Writes the JSON and the Markdown. */
    public static WrittenDossier write(Path json, Path markdown, CompatibilityDossier dossier) throws IOException {
        return write(json, markdown, dossier, Optional.empty());
    }

    /**
     * Writes the JSON and the Markdown and, when {@code localDetail} is given, the local detail
     * file beside them.
     *
     * @throws IllegalArgumentException when the dossier would expose an identifier, or {@code
     *     localDetail} is under a {@code docs} directory
     */
    public static WrittenDossier write(
            Path json, Path markdown, CompatibilityDossier dossier, Optional<Path> localDetail) throws IOException {
        localDetail.ifPresent(CompatibilityDossierWriter::requireOutsideDocs);
        JsonNode tree = sorted(toJson(dossier));
        requireNoIdentifiers(tree);
        byte[] jsonBytes =
                (MAPPER.writer().with(printer()).writeValueAsString(tree) + "\n").getBytes(StandardCharsets.UTF_8);
        store(json, jsonBytes);
        store(markdown, markdown(tree).getBytes(StandardCharsets.UTF_8));
        if (localDetail.isPresent()) {
            store(localDetail.get(), localDetail(dossier).getBytes(StandardCharsets.UTF_8));
        }
        return new WrittenDossier(json, markdown, localDetail, SummaryWriter.sha256(jsonBytes));
    }

    /** The JSON of the dossier as it would be written, keys sorted, or the refusal. */
    public static String jsonOf(CompatibilityDossier dossier) {
        JsonNode tree = sorted(toJson(dossier));
        requireNoIdentifiers(tree);
        return MAPPER.writer().with(printer()).writeValueAsString(tree) + "\n";
    }

    private static void store(Path file, byte[] content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, content);
    }

    private static void requireOutsideDocs(Path file) {
        for (Path part : file.toAbsolutePath().normalize()) {
            if ("docs".equals(part.toString())) {
                throw new IllegalArgumentException(
                        "the local detail carries INEs and is never written under docs/: " + file);
            }
        }
    }

    /** The printer every versioned JSON of the Portão D uses: two spaces and an explicit {@code "\n"}. */
    static DefaultPrettyPrinter printer() {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(Separators.createDefaultInstance()
                .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator(""));
        printer.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        printer.indentArraysWith(new DefaultIndenter("  ", "\n"));
        return printer;
    }

    // The JSON

    private static ObjectNode toJson(CompatibilityDossier dossier) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schema_version", ReferencePolicy.DOSSIER_SCHEMA_VERSION);
        root.put("reference_id", dossier.referenceId());
        root.put("pack", dossier.pack());
        root.put("rule_version", dossier.ruleVersion());
        root.put("quadrimestre", dossier.quadrimestre());
        root.put("reference_manifest_sha256", dossier.referenceManifestSha256());
        root.put("local_source_fingerprint", dossier.localSourceFingerprint());
        ArrayNode sources = root.putArray("official_methodology_sources");
        dossier.officialMethodologySources().forEach(source -> sources.add(sourceJson(source)));
        ArrayNode deltas = root.putArray("normative_deltas");
        dossier.normativeDeltas().forEach(delta -> deltas.add(deltaJson(delta)));
        ArrayNode probes = root.putArray("probe_results");
        dossier.probeResults().forEach(entry -> probes.add(probeJson(entry)));
        root.set(
                "official_field_comparison",
                MAPPER.valueToTree(dossier.officialFieldComparison().versionedForm()));
        root.set("coverage", MAPPER.valueToTree(dossier.coverage().versionedForm()));
        root.put("verdict", dossier.verdict().name());
        root.put(REASON, dossier.reason());
        ArrayNode limitations = root.putArray("declared_limitations");
        dossier.declaredLimitations().forEach(limitation -> limitations.add(limitationJson(limitation)));
        ArrayNode conventions = root.putArray("declared_conventions");
        dossier.declaredConventions().forEach(convention -> conventions.add(conventionJson(convention)));
        if (!dossier.siblingVerdicts().isEmpty()) {
            ObjectNode siblings = root.putObject("sibling_verdicts");
            new TreeMap<>(dossier.siblingVerdicts()).forEach((pack, verdict) -> siblings.put(pack, verdict.name()));
        }
        return root;
    }

    private static ObjectNode sourceJson(Source source) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(ID, source.id());
        node.put(TITLE, source.title());
        node.put(URL, source.url());
        node.put("published", source.published().toString());
        node.put("kind", source.kind().name());
        return node;
    }

    private static ObjectNode deltaJson(NormativeDelta delta) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("dimension_id", delta.dimensionId());
        node.put(LOCAL_READING, delta.localReading());
        node.put("official_reading", delta.officialReading().name());
        delta.officialReadingText().ifPresent(text -> node.put("official_reading_text", text));
        node.put("source_id", delta.sourceId());
        delta.probeId().ifPresent(id -> node.put(PROBE_ID, id));
        refs(node, delta.decisionRefs());
        return node;
    }

    private static ObjectNode probeJson(ProbeEntry entry) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("role", entry.role().name().toLowerCase(Locale.ROOT));
        node.put("subject_id", entry.subjectId());
        entry.result().versionedForm().forEach(node::put);
        return node;
    }

    private static ObjectNode limitationJson(DeclaredLimitation limitation) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(ID, limitation.id());
        node.put("what", limitation.what());
        refs(node, limitation.decisionRefs());
        return node;
    }

    private static ObjectNode conventionJson(ConventionEntry entry) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(ID, entry.convention().id());
        node.put(LOCAL_READING, entry.convention().localReading());
        node.put("research_ref", entry.convention().researchRef());
        refs(node, entry.convention().decisionRefs());
        entry.convention().probeId().ifPresent(id -> node.put(PROBE_ID, id));
        entry.result().ifPresent(result -> node.set(PROBE_RESULT, MAPPER.valueToTree(result.versionedForm())));
        return node;
    }

    private static void refs(ObjectNode node, List<String> decisionRefs) {
        ArrayNode refs = node.putArray(DECISION_REFS);
        decisionRefs.forEach(refs::add);
    }

    /** The same tree with the keys of every object in alphabetical order. */
    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = MAPPER.createObjectNode();
            new TreeSet<>(node.propertyNames()).forEach(name -> copy.set(name, sorted(node.get(name))));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = MAPPER.createArrayNode();
            node.values().forEach(element -> copy.add(sorted(element)));
            return copy;
        }
        return node;
    }

    // The privacy guard

    private static void requireNoIdentifiers(JsonNode root) {
        List<String> found = identifiersIn(root);
        if (!found.isEmpty()) {
            throw new IllegalArgumentException(
                    "the dossier would expose an identifier (an INE, a CNES, a person key or a raw record id) at "
                            + String.join(", ", found));
        }
    }

    /**
     * Where {@code root} would expose an identifier, by path in the document; empty when it is
     * clean. The check the writer applies before it writes, for the checks that read a dossier back.
     */
    static List<String> identifiersIn(JsonNode root) {
        List<String> found = new ArrayList<>();
        scan(root, "$", found);
        return List.copyOf(found);
    }

    private static void scan(JsonNode node, String path, List<String> found) {
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                String child = path + "." + name;
                if (PERSON_OR_RECORD_KEY.matcher(name).find() || looksLikeIdentifier(name)) {
                    found.add(child + " (key)");
                }
                scanValue(name, node.get(name), child, found);
            }
        } else if (node.isArray()) {
            int index = 0;
            for (JsonNode element : node.values()) {
                scanValue("", element, path + "[" + index++ + "]", found);
            }
        }
    }

    private static void scanValue(String key, JsonNode value, String path, List<String> found) {
        if (value.isObject() || value.isArray()) {
            scan(value, path, found);
        } else if (value.isString()) {
            if (!isAllowedValue(key, value.stringValue())) {
                found.add(path);
            }
        } else {
            found.add(path + " (not text: counts are written masked, as text)");
        }
    }

    private static boolean isAllowedValue(String key, String value) {
        Pattern hash = HASH_KEYS.get(key);
        if (hash != null) {
            return hash.matcher(value).matches();
        }
        return URL.equals(key) ? !looksLikeIdentifier(withoutDigitRuns(value)) : !looksLikeIdentifier(value);
    }

    private static String withoutDigitRuns(String value) {
        return DIGIT_RUN.matcher(value).replaceAll("0");
    }

    /** True when {@code text} holds a long digit run, a UUID or a hex digest, once reference ids and SEI numbers are set aside. */
    static boolean looksLikeIdentifier(String text) {
        String rest =
                SEI_DOCUMENT.matcher(REFERENCE_ID.matcher(text).replaceAll("")).replaceAll("");
        return DIGIT_RUN.matcher(rest).find()
                || UUID.matcher(rest).find()
                || HEX_DIGEST.matcher(rest).find();
    }

    // The Markdown

    /**
     * The Markdown of a dossier's JSON bytes, as {@link #write} wrote it beside them: what a check
     * compares a committed {@code .md} with.
     */
    static String renderMarkdown(byte[] json) {
        return markdown(MAPPER.readTree(json));
    }

    private static String markdown(JsonNode root) {
        List<String> lines = new ArrayList<>();
        lines.add("# Dossiê de compatibilidade metodológica: " + field(root, "pack") + " × "
                + field(root, "reference_id"));
        lines.add("");
        lines.add("- Veredito: **" + field(root, "verdict") + "**");
        lines.add("- Motivo: " + field(root, REASON));
        lines.add("- Regra: `" + field(root, "rule_version") + "`");
        lines.add("- Quadrimestre: " + field(root, "quadrimestre"));
        lines.add("- Manifesto da referência (SHA-256): `" + field(root, "reference_manifest_sha256") + "`");
        lines.add("- Fingerprint da fonte local: `" + field(root, "local_source_fingerprint") + "`");
        lines.add("- Esquema: " + field(root, "schema_version"));
        lines.add("");
        section(
                lines,
                "Fontes oficiais",
                root.get("official_methodology_sources"),
                List.of(ID, TITLE, "kind", "published", URL));
        section(
                lines,
                "Diferenças normativas",
                root.get("normative_deltas"),
                List.of(
                        "dimension_id",
                        LOCAL_READING,
                        "official_reading",
                        "official_reading_text",
                        "source_id",
                        PROBE_ID));
        section(
                lines,
                "Resultados dos probes",
                root.get("probe_results"),
                List.of(
                        "role",
                        "subject_id",
                        PROBE_ID,
                        "observability",
                        "affected",
                        "divergent",
                        "limitations",
                        REASON));
        keyValues(
                lines,
                "Comparação com os campos oficiais (diagnóstico, fora do veredito)",
                root.get("official_field_comparison"));
        keyValues(lines, "Cobertura do tipo de equipe", root.get("coverage"));
        section(lines, "Limitações declaradas", root.get("declared_limitations"), List.of(ID, "what", DECISION_REFS));
        section(
                lines,
                "Convenções declaradas",
                root.get("declared_conventions"),
                List.of(ID, LOCAL_READING, "research_ref", PROBE_ID, DECISION_REFS));
        if (root.has("sibling_verdicts")) {
            keyValues(lines, "Vereditos dos packs irmãos", root.get("sibling_verdicts"));
        }
        lines.add("Contagens abaixo de 10 aparecem como `<10`. Nenhum INE, CNES ou chave de pessoa entra neste"
                + " documento nem no JSON versionado; o detalhe por equipe fica só no diretório local.");
        return String.join("\n", lines) + "\n";
    }

    private static void section(List<String> lines, String title, JsonNode rows, List<String> columns) {
        lines.add("## " + title);
        lines.add("");
        if (rows.isEmpty()) {
            lines.add("Nenhum.");
            lines.add("");
            return;
        }
        lines.add("| " + String.join(" | ", columns) + " |");
        lines.add("|" + "---|".repeat(columns.size()));
        for (JsonNode row : rows.values()) {
            List<String> cells = new ArrayList<>();
            for (String column : columns) {
                cells.add(cell(row.get(column)));
            }
            lines.add("| " + String.join(" | ", cells) + " |");
        }
        lines.add("");
    }

    private static void keyValues(List<String> lines, String title, JsonNode object) {
        lines.add("## " + title);
        lines.add("");
        for (String name : object.propertyNames()) {
            lines.add("- " + name + ": " + cell(object.get(name)));
        }
        lines.add("");
    }

    private static String field(JsonNode node, String name) {
        return node.get(name).stringValue();
    }

    private static String cell(JsonNode value) {
        if (value == null || value.isNull()) {
            return DASH;
        }
        String text;
        if (value.isArray()) {
            List<String> parts = new ArrayList<>();
            value.values().forEach(part -> parts.add(part.stringValue()));
            text = String.join(", ", parts);
        } else {
            text = value.stringValue();
        }
        return text.isEmpty() ? DASH : text.replace("|", "\\|").replace("\n", " ");
    }

    // The local detail

    private static String localDetail(CompatibilityDossier dossier) {
        List<String> lines = new ArrayList<>();
        lines.add("# Local only: never commit. " + dossier.referenceId() + " x " + dossier.pack());
        for (ProbeEntry entry : dossier.probeResults()) {
            entry.result()
                    .localDetail()
                    .forEach(line -> lines.add("[" + entry.result().probeId() + "] " + line));
        }
        dossier.officialFieldComparison()
                .localDetail()
                .forEach(line -> lines.add("[official-field-comparison] " + line));
        return String.join("\n", lines) + "\n";
    }
}
