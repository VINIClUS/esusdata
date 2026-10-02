package esusdata.source.pec;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The canonical v2 capabilities packaged with this release (ADR 0030), read from {@code
 * contracts/compatibility/capabilities/index.json} and the descriptor and SQL files it lists. The
 * checksum of each query is computed here exactly as for C1's frozen query ({@link FrozenQuery}),
 * so the matrix entry, the Rust registry and the Java side agree on one value.
 */
public final class CapabilityCatalog {

    public static final String INDEX = "/compatibility/capabilities/index.json";
    private static final String DESCRIPTOR_ROOT = "/compatibility/capabilities/";
    private static final String QUERY_ROOT = "/compatibility/";

    private static final CapabilityCatalog PACKAGED = load();

    private final Map<String, CapabilityContract> byCapability;

    private CapabilityCatalog(Map<String, CapabilityContract> byCapability) {
        this.byCapability = Collections.unmodifiableMap(new LinkedHashMap<>(byCapability));
    }

    public static CapabilityCatalog packaged() {
        return PACKAGED;
    }

    public Optional<CapabilityContract> find(String capability) {
        return Optional.ofNullable(byCapability.get(capability));
    }

    /** The packaged contract of {@code capability}, or {@link IllegalArgumentException}. */
    public CapabilityContract require(String capability) {
        return find(capability).orElseThrow(() -> new IllegalArgumentException("no packaged capability " + capability));
    }

    public List<CapabilityContract> all() {
        return List.copyOf(byCapability.values());
    }

    static CapabilityCatalog load() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode index = read(mapper, INDEX);
        Map<String, CapabilityContract> contracts = new LinkedHashMap<>();
        for (JsonNode file : index.get("descriptors")) {
            CapabilityContract contract = contract(read(mapper, DESCRIPTOR_ROOT + file.asString()));
            if (contracts.put(contract.capability(), contract) != null) {
                throw new IllegalStateException("capability " + contract.capability() + " is packaged twice");
            }
        }
        return new CapabilityCatalog(contracts);
    }

    private static CapabilityContract contract(JsonNode d) {
        List<CapabilityContract.Bind> binds = new ArrayList<>();
        for (JsonNode b : d.get("binds")) {
            binds.add(new CapabilityContract.Bind(
                    b.get("name").asString(), b.get("type").asString()));
        }
        List<CapabilityContract.Column> columns = new ArrayList<>();
        for (JsonNode c : d.get("columns")) {
            columns.add(new CapabilityContract.Column(
                    c.get("name").asString(),
                    c.get("type").asString(),
                    c.get("required").asBoolean()));
        }
        String queryPath = d.get("query").asString();
        String query = FrozenQuery.load(QUERY_ROOT + queryPath);
        JsonNode scope = d.get("scope_date_column");
        return new CapabilityContract(
                d.get("capability").asString(),
                d.get("adapter_version").asString(),
                d.get("record_kind").asString(),
                d.get("entity_type_column").asString(),
                d.get("record_id_column").asString(),
                d.get("municipality_column").asString(),
                scope == null || scope.isNull() ? null : scope.asString(),
                queryPath,
                binds,
                columns,
                query,
                FrozenQuery.checksum(query));
    }

    private static JsonNode read(ObjectMapper mapper, String resourcePath) {
        try (InputStream resource = CapabilityCatalog.class.getResourceAsStream(resourcePath)) {
            if (resource == null) {
                throw new IllegalStateException("Packaged capability resource is missing: " + resourcePath);
            }
            return mapper.readTree(resource);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read packaged capability resource: " + resourcePath, e);
        }
    }
}
