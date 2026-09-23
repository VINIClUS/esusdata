package esusdata.source.pec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable view of the packaged PEC adapter compatibility contract. */
public final class PecCompatibilityMatrix {

    public static final String RESOURCE = "/compatibility/pec-adapters.json";

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
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not read packaged compatibility matrix", e);
        }
    }

    public static PecCompatibilityMatrix fromJson(String json) {
        try {
            return new PecCompatibilityMatrix(new ObjectMapper().readTree(json));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid compatibility matrix JSON", e);
        }
    }

    public Entry findExact(
            String capability,
            String adapterVersion,
            PecSourceIdentity identity,
            String postgresVersion
    ) {
        requireNonBlank(capability, "capability");
        requireNonBlank(adapterVersion, "adapterVersion");
        requireNonBlank(postgresVersion, "postgresVersion");
        if (identity == null || !identity.isComplete()) {
            throw new IllegalStateException(
                    "PecSourceIdentity is required and must include an installation role");
        }
        if (!root.isObject()
                || !"1".equals(text(root, "schema_version"))
                || !"VALIDATED".equals(text(root, "validation_status"))) {
            throw new IllegalStateException("Unsupported compatibility matrix schema");
        }
        JsonNode testedWith = root.get("tested_with");
        if (testedWith == null || !testedWith.isArray() || testedWith.isEmpty()) {
            throw new IllegalStateException("Compatibility matrix has no tested_with entries");
        }

        for (JsonNode candidate : testedWith) {
            if (matches(candidate, capability, adapterVersion, identity, postgresVersion)) {
                Entry entry = parseEntry(candidate);
                if (!"VALIDATED".equals(entry.status())) {
                    throw new IllegalStateException(
                            "Exact compatibility entry is not VALIDATED: " + entry.status());
                }
                return entry;
            }
        }
        throw new IllegalStateException(
                "No exact compatibility entry for capability=" + capability
                        + ", adapterVersion=" + adapterVersion
                        + ", PEC=" + identity.pecVersion()
                        + ", PostgreSQL=" + postgresVersion
                        + ", model=" + identity.readModel()
                        + ", role=" + identity.installationRole());
    }

    private static boolean matches(
            JsonNode candidate,
            String capability,
            String adapterVersion,
            PecSourceIdentity identity,
            String postgresVersion
    ) {
        return capability.equals(text(candidate, "capability"))
                && adapterVersion.equals(text(candidate, "adapter_version"))
                && identity.pecVersion().equals(text(candidate, "pec_version"))
                && postgresVersion.equals(text(candidate, "postgresql_version"))
                && identity.readModel().equals(text(candidate, "read_model"))
                && identity.installationRole().equals(text(candidate, "installation_role"));
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
            for (JsonNode column : columnsNode) requestedColumns.add(column.asString());
            if (fingerprints.put(name, fingerprint) != null) {
                throw new IllegalStateException("Compatibility matrix contains duplicate object: " + name);
            }
            columns.put(name, List.copyOf(requestedColumns));
        }
        return new Entry(
                text(node, "pec_version"), text(node, "postgresql_version"),
                text(node, "adapter_version"), text(node, "read_model"),
                text(node, "installation_role"), text(node, "capability"),
                text(node, "status"), text(node, "query_checksum"),
                Map.copyOf(fingerprints), Map.copyOf(columns));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Compatibility " + field + " is required");
        }
    }

    public record Entry(
            String pecVersion,
            String postgresVersion,
            String adapterVersion,
            String readModel,
            String installationRole,
            String capability,
            String status,
            String queryChecksum,
            Map<String, String> objectFingerprints,
            Map<String, List<String>> objectColumns
    ) {
    }
}
