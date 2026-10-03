package esusdata.source.pec;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Immutable view of the packaged PEC adapter compatibility contract. */
public final class PecCompatibilityMatrix {

    public static final String RESOURCE = "/compatibility/pec-adapters.json";
    private static final String VALIDATED = "VALIDATED";
    private static final String CAPABILITY = "capability";

    private final JsonNode root;

    private PecCompatibilityMatrix(JsonNode root) {
        this.root = root;
    }

    public static PecCompatibilityMatrix fromClasspathResource() {
        InputStream resource = PecCompatibilityMatrix.class.getResourceAsStream(RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Packaged compatibility matrix is missing: " + RESOURCE);
        }
        try (resource) {
            return new PecCompatibilityMatrix(new ObjectMapper().readTree(resource));
        } catch (IOException
                | RuntimeException e) { // NOPMD - any read failure of the packaged matrix is fatal, with its cause
            throw new IllegalStateException("Could not read packaged compatibility matrix", e);
        }
    }

    public static PecCompatibilityMatrix fromJson(String json) {
        try {
            return new PecCompatibilityMatrix(new ObjectMapper().readTree(json));
        } catch (RuntimeException e) { // NOPMD - any parse or shape failure, converted with its cause
            throw new IllegalArgumentException("Invalid compatibility matrix JSON", e);
        }
    }

    public Entry findExact(
            String capability, String adapterVersion, PecSourceIdentity identity, String postgresVersion) {
        requireNonBlank(capability, "capability");
        requireNonBlank(adapterVersion, "adapterVersion");
        requireNonBlank(postgresVersion, "postgresVersion");
        if (identity == null || !identity.isComplete()) {
            throw new IllegalStateException("PecSourceIdentity is required and must include an installation role");
        }
        if (!root.isObject()
                || !"2".equals(text(root, "schema_version"))
                || !VALIDATED.equals(text(root, "validation_status"))) {
            throw new IllegalStateException("Unsupported compatibility matrix schema");
        }
        JsonNode testedWith = root.get("tested_with");
        if (testedWith == null || !testedWith.isArray() || testedWith.isEmpty()) {
            throw new IllegalStateException("Compatibility matrix has no tested_with entries");
        }

        for (JsonNode candidate : testedWith) {
            if (matches(candidate, capability, adapterVersion, identity, postgresVersion)) {
                Entry entry = parseEntry(candidate);
                if (!VALIDATED.equals(entry.status())) {
                    throw new IllegalStateException("Exact compatibility entry is not VALIDATED: " + entry.status());
                }
                return entry;
            }
        }
        throw new IllegalStateException("No exact compatibility entry for capability=" + capability
                + ", adapterVersion=" + adapterVersion
                + ", PEC=" + identity.pecVersion()
                + ", PostgreSQL=" + postgresVersion
                + ", model=" + identity.readModel()
                + ", role=" + identity.installationRole());
    }

    /**
     * Whether some {@code VALIDATED} entry lists this PEC version for the same read model and
     * installation role — what a source's registration alone can show. Looser than {@link
     * #findExact}, which acquisition still enforces: it ignores the capability and the PostgreSQL
     * version, which is only known once connected.
     */
    public boolean lists(PecSourceIdentity identity) {
        return identity != null
                && identity.isComplete()
                && isValidatedDocument()
                && testedWith().stream().anyMatch(candidate -> validatedFor(candidate, identity));
    }

    /**
     * The capabilities with a {@code VALIDATED} entry that lists this PEC version for the same read
     * model and installation role (ADR 0030) — what decides, before any connection, whether a pack
     * can run against a source. Like {@link #lists}, it cannot see the PostgreSQL version; {@link
     * #findExact} still checks the whole entry at acquisition. An incomplete identity or a matrix
     * that is not a validated schema-v2 document validates nothing.
     */
    public Set<String> validatedCapabilities(PecSourceIdentity identity) {
        if (identity == null || !identity.isComplete() || !isValidatedDocument()) {
            return Set.of();
        }
        Set<String> validated = new TreeSet<>();
        for (JsonNode candidate : testedWith()) {
            String capability = text(candidate, CAPABILITY);
            if (capability != null && validatedFor(candidate, identity)) {
                validated.add(capability);
            }
        }
        return Collections.unmodifiableSet(validated);
    }

    /**
     * Every entry of the matrix in document order, whatever its status — {@code NOT_TESTED} and
     * {@code BLOCKED} entries included, so a screen can say why a capability is not available.
     *
     * @throws IllegalStateException if the document is not a schema-v2 matrix or an entry is
     *     incomplete
     */
    public List<Entry> entries() {
        if (!root.isObject() || !"2".equals(text(root, "schema_version"))) {
            throw new IllegalStateException("Unsupported compatibility matrix schema");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonNode candidate : testedWith()) {
            entries.add(parseEntry(candidate));
        }
        return List.copyOf(entries);
    }

    private boolean isValidatedDocument() {
        return root.isObject()
                && "2".equals(text(root, "schema_version"))
                && VALIDATED.equals(text(root, "validation_status"));
    }

    private List<JsonNode> testedWith() {
        JsonNode testedWith = root.get("tested_with");
        if (testedWith == null || !testedWith.isArray()) {
            return List.of();
        }
        List<JsonNode> candidates = new ArrayList<>();
        testedWith.forEach(candidates::add);
        return candidates;
    }

    /** A {@code VALIDATED} entry listing the identity's PEC version, read model and role. */
    private static boolean validatedFor(JsonNode candidate, PecSourceIdentity identity) {
        return VALIDATED.equals(text(candidate, "status"))
                && pecVersions(candidate).contains(identity.pecVersion())
                && sameInstallation(candidate, identity);
    }

    private static boolean sameInstallation(JsonNode candidate, PecSourceIdentity identity) {
        return identity.readModel().equals(text(candidate, "read_model"))
                && identity.installationRole().equals(text(candidate, "installation_role"));
    }

    private static boolean matches(
            JsonNode candidate,
            String capability,
            String adapterVersion,
            PecSourceIdentity identity,
            String postgresVersion) {
        return capability.equals(text(candidate, CAPABILITY))
                && adapterVersion.equals(text(candidate, "adapter_version"))
                && pecVersions(candidate).contains(identity.pecVersion())
                && postgresVersion.equals(text(candidate, "postgresql_version"))
                && sameInstallation(candidate, identity);
    }

    private static Entry parseEntry(JsonNode node) {
        JsonNode objects = node.get("objects_used");
        if (objects == null || !objects.isArray() || objects.isEmpty()) {
            throw new IllegalStateException("Compatibility entry has no objects_used fingerprints");
        }
        Map<String, String> fingerprints = new LinkedHashMap<>();
        Map<String, List<String>> columns = new LinkedHashMap<>();
        for (JsonNode object : objects) {
            String name = text(object, "object");
            String fingerprint = text(object, "signature_fingerprint");
            JsonNode columnsNode = object.get("columns_used");
            if (name == null || fingerprint == null || columnsNode == null || !columnsNode.isArray()) {
                throw new IllegalStateException("Compatibility object fingerprint entry is incomplete");
            }
            List<String> requestedColumns = new ArrayList<>();
            for (JsonNode column : columnsNode) {
                requestedColumns.add(column.asString());
            }
            if (fingerprints.put(name, fingerprint) != null) {
                throw new IllegalStateException("Compatibility matrix contains duplicate object: " + name);
            }
            columns.put(name, List.copyOf(requestedColumns));
        }
        return new Entry(
                pecVersions(node), text(node, "postgresql_version"),
                text(node, "adapter_version"), text(node, "read_model"),
                text(node, "installation_role"), text(node, CAPABILITY),
                text(node, "status"), text(node, "query_checksum"),
                Map.copyOf(fingerprints), Map.copyOf(columns));
    }

    /** The entry's explicit list of validated PEC versions — never a range (schema v2). */
    private static List<String> pecVersions(JsonNode node) {
        JsonNode versions = node.get("pec_versions");
        if (versions == null || !versions.isArray()) {
            return List.of();
        }
        List<String> listed = new ArrayList<>();
        for (JsonNode version : versions) {
            listed.add(version.asString());
        }
        return List.copyOf(listed);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Compatibility " + field + " is required");
        }
    }

    public record Entry(
            List<String> pecVersions,
            String postgresVersion,
            String adapterVersion,
            String readModel,
            String installationRole,
            String capability,
            String status,
            String queryChecksum,
            Map<String, String> objectFingerprints,
            Map<String, List<String>> objectColumns) {}
}
