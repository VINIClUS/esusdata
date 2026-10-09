package esusdata.run.worker;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The {@value #FILE} of a partition of {@link ReferenceScopedExtracts}, written when the partition
 * is acquired and the last thing in it: what the partition was acquired for and from, and the
 * identity of every extract in it. The extracts themselves know nothing of the reference revision,
 * the rule version or the source they were asked for, so without it a partition copied under
 * another reference, rule or source would look like any other; with it, that copy is a refusal.
 *
 * <p>The JSON is read and written strictly: fixed keys in a fixed order, two-space indentation and
 * an explicit {@code "\n"} (Jackson's default pretty printer would use the system line separator,
 * which would change the bytes on Windows). A document with other keys, other types or text after
 * it is not the sidecar this class wrote.
 *
 * @param municipalityIbge the municipality the partition is for
 * @param quadrimestre the SIAPS spelling of its quadrimestre, {@code 2026Q1}
 * @param referenceManifestSha256 the reference revision it was acquired for
 * @param pack the pack id
 * @param ruleVersion the rule version
 * @param month the competência, {@code yyyy-MM}
 * @param identity the PEC it was read from
 * @param acquiredAt when the acquisition finished
 * @param extracts every extract of the partition, the supplement of C1 included, in reading order
 */
record PartitionSidecar(
        String municipalityIbge,
        String quadrimestre,
        String referenceManifestSha256,
        String pack,
        String ruleVersion,
        String month,
        SourceIdentity identity,
        Instant acquiredAt,
        List<Extract> extracts) {

    /** The name of the sidecar in the directory of a partition. */
    static final String FILE = "source-identity.json";

    private static final String SCHEMA_VERSION = "1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper READER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final String MUNICIPALITY = "municipality_ibge";
    private static final String QUADRIMESTRE = "quadrimestre";
    private static final String REFERENCE_SHA = "reference_manifest_sha256";
    private static final String PACK = "pack";
    private static final String RULE_VERSION = "rule_version";
    private static final String MONTH = "month";
    private static final String IDENTITY = "identity";
    private static final String ACQUIRED_AT = "acquired_at";
    private static final String EXTRACTS = "extracts";
    private static final String PEC_SOURCE_ID = "pec_source_id";
    private static final String PEC_VERSION = "pec_version";
    private static final String POSTGRES_VERSION = "postgres_version";
    private static final String READ_MODEL = "read_model";
    private static final String EXTRACTION_ID = "extraction_id";
    private static final String CHECKSUM = "checksum";
    private static final String QUERY_CHECKSUM = "query_checksum";
    private static final String ADAPTER_VERSION = "adapter_version";

    private static final Set<String> ROOT_KEYS = Set.of(
            SCHEMA_VERSION_KEY,
            MUNICIPALITY,
            QUADRIMESTRE,
            REFERENCE_SHA,
            PACK,
            RULE_VERSION,
            MONTH,
            IDENTITY,
            ACQUIRED_AT,
            EXTRACTS);
    private static final Set<String> IDENTITY_KEYS =
            Set.of(PEC_SOURCE_ID, PEC_VERSION, POSTGRES_VERSION, MUNICIPALITY, READ_MODEL);
    private static final Set<String> EXTRACT_KEYS = Set.of(EXTRACTION_ID, CHECKSUM, QUERY_CHECKSUM, ADAPTER_VERSION);

    /**
     * One extract of the partition, as its manifest said when it was acquired.
     *
     * @param extractionId the id the extract was published under
     * @param checksum the SHA-256 of its data file
     * @param queryChecksum the checksum of the query (v1) or of the plan (v2)
     * @param adapterVersion the adapter version of the manifest
     */
    record Extract(String extractionId, String checksum, String queryChecksum, String adapterVersion) {

        Extract {
            Objects.requireNonNull(extractionId, EXTRACTION_ID);
            Objects.requireNonNull(checksum, CHECKSUM);
            Objects.requireNonNull(queryChecksum, QUERY_CHECKSUM);
            Objects.requireNonNull(adapterVersion, ADAPTER_VERSION);
        }
    }

    PartitionSidecar {
        Objects.requireNonNull(municipalityIbge, MUNICIPALITY);
        Objects.requireNonNull(quadrimestre, QUADRIMESTRE);
        Objects.requireNonNull(referenceManifestSha256, REFERENCE_SHA);
        Objects.requireNonNull(pack, PACK);
        Objects.requireNonNull(ruleVersion, RULE_VERSION);
        Objects.requireNonNull(month, MONTH);
        Objects.requireNonNull(identity, IDENTITY);
        Objects.requireNonNull(acquiredAt, ACQUIRED_AT);
        extracts = List.copyOf(extracts);
    }

    /** The sidecar of a partition acquired for {@code context}. */
    static PartitionSidecar of(ReferenceExecutionContext context, Instant acquiredAt, List<Extract> extracts) {
        return new PartitionSidecar(
                context.municipalityIbge(),
                context.quadrimestreName(),
                context.referenceManifestSha256(),
                context.packId(),
                context.ruleVersion(),
                context.month().toString(),
                context.sourceIdentity(),
                acquiredAt,
                extracts);
    }

    /**
     * What differs between this sidecar and what the partition is expected to be, one entry per
     * field (its name, never its value); empty when the partition is the one asked for.
     */
    List<String> differencesFrom(ReferenceExecutionContext expected) {
        List<String> differences = new ArrayList<>();
        addIfDifferent(differences, MUNICIPALITY, municipalityIbge, expected.municipalityIbge());
        addIfDifferent(differences, QUADRIMESTRE, quadrimestre, expected.quadrimestreName());
        addIfDifferent(differences, REFERENCE_SHA, referenceManifestSha256, expected.referenceManifestSha256());
        addIfDifferent(differences, PACK, pack, expected.packId());
        addIfDifferent(differences, RULE_VERSION, ruleVersion, expected.ruleVersion());
        addIfDifferent(differences, MONTH, month, expected.month().toString());
        SourceIdentity wanted = expected.sourceIdentity();
        addIfDifferent(differences, IDENTITY + "." + PEC_SOURCE_ID, identity.pecSourceId(), wanted.pecSourceId());
        addIfDifferent(differences, IDENTITY + "." + PEC_VERSION, identity.pecVersion(), wanted.pecVersion());
        addIfDifferent(
                differences, IDENTITY + "." + POSTGRES_VERSION, identity.postgresVersion(), wanted.postgresVersion());
        addIfDifferent(
                differences, IDENTITY + "." + MUNICIPALITY, identity.municipalityIbge(), wanted.municipalityIbge());
        addIfDifferent(differences, IDENTITY + "." + READ_MODEL, identity.readModel(), wanted.readModel());
        return List.copyOf(differences);
    }

    private static void addIfDifferent(List<String> differences, String field, String actual, String expected) {
        if (!expected.equals(actual)) {
            differences.add(field);
        }
    }

    /** The sidecar's text: fixed key order, two-space indentation, {@code "\n"} line ends. */
    String toJson() {
        ObjectNode json = MAPPER.createObjectNode();
        json.put(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        json.put(MUNICIPALITY, municipalityIbge);
        json.put(QUADRIMESTRE, quadrimestre);
        json.put(REFERENCE_SHA, referenceManifestSha256);
        json.put(PACK, pack);
        json.put(RULE_VERSION, ruleVersion);
        json.put(MONTH, month);
        ObjectNode source = json.putObject(IDENTITY);
        source.put(PEC_SOURCE_ID, identity.pecSourceId());
        source.put(PEC_VERSION, identity.pecVersion());
        source.put(POSTGRES_VERSION, identity.postgresVersion());
        source.put(MUNICIPALITY, identity.municipalityIbge());
        source.put(READ_MODEL, identity.readModel());
        json.put(ACQUIRED_AT, acquiredAt.toString());
        ArrayNode listed = json.putArray(EXTRACTS);
        for (Extract extract : extracts) {
            ObjectNode entry = listed.addObject();
            entry.put(EXTRACTION_ID, extract.extractionId());
            entry.put(CHECKSUM, extract.checksum());
            entry.put(QUERY_CHECKSUM, extract.queryChecksum());
            entry.put(ADAPTER_VERSION, extract.adapterVersion());
        }
        return MAPPER.writer().with(printer()).writeValueAsString(json) + "\n";
    }

    private static DefaultPrettyPrinter printer() {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(Separators.createDefaultInstance()
                .withObjectNameValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator(""));
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

    /** Reads {@link #toJson()} back; a document with other keys, other types or another schema is refused. */
    static PartitionSidecar fromJson(String json) {
        JsonNode root = object(parse(json), ROOT_KEYS, "sidecar");
        if (!SCHEMA_VERSION.equals(text(root, SCHEMA_VERSION_KEY))) {
            throw new IllegalArgumentException("unknown sidecar schema_version");
        }
        JsonNode source = object(root.path(IDENTITY), IDENTITY_KEYS, IDENTITY);
        List<Extract> extracts = new ArrayList<>();
        JsonNode listed = root.path(EXTRACTS);
        if (!listed.isArray()) {
            throw new IllegalArgumentException(EXTRACTS + " must be an array");
        }
        for (JsonNode entry : listed.values()) {
            JsonNode extract = object(entry, EXTRACT_KEYS, "extract");
            extracts.add(new Extract(
                    text(extract, EXTRACTION_ID),
                    text(extract, CHECKSUM),
                    text(extract, QUERY_CHECKSUM),
                    text(extract, ADAPTER_VERSION)));
        }
        return new PartitionSidecar(
                text(root, MUNICIPALITY),
                text(root, QUADRIMESTRE),
                text(root, REFERENCE_SHA),
                text(root, PACK),
                text(root, RULE_VERSION),
                text(root, MONTH),
                new SourceIdentity(
                        text(source, PEC_SOURCE_ID),
                        text(source, PEC_VERSION),
                        text(source, POSTGRES_VERSION),
                        text(source, MUNICIPALITY),
                        text(source, READ_MODEL)),
                instant(text(root, ACQUIRED_AT)),
                extracts);
    }

    private static JsonNode parse(String json) {
        try {
            return READER.readTree(json.getBytes(StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("the sidecar is not valid JSON", e);
        }
    }

    private static JsonNode object(JsonNode node, Set<String> keys, String what) {
        if (!node.isObject()) {
            throw new IllegalArgumentException(what + " is not a JSON object");
        }
        Set<String> actual = new TreeSet<>(node.propertyNames());
        if (!actual.equals(keys)) {
            throw new IllegalArgumentException(what + " must have exactly its keys " + new TreeSet<>(keys));
        }
        return node;
    }

    private static String text(JsonNode object, String key) {
        JsonNode value = object.path(key);
        if (!value.isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return value.stringValue();
    }

    private static Instant instant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(ACQUIRED_AT + " is not an ISO instant", e);
        }
    }
}
