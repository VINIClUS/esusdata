package esusdata.indicator;

import esusdata.indicator.model.GateCheck;
import esusdata.indicator.model.GateId;
import esusdata.indicator.model.GateStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.componente3.ComponentIII;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The versioned release-gate registry (ADR 0032): {@code contracts/indicators/release-gates.json},
 * packaged as {@value #RESOURCE}. It records, per pack and {@code rule_version}, the two gates an
 * automated check outside the run decides — Portão A (fonte e vigência) and Portão D
 * (reconciliação). Portões B and C are evaluated on every result by the executor.
 *
 * <p>Fail fast, like the compatibility matrix: an invalid file, a registered pack without an entry,
 * a duplicated or unknown entry, or a {@code PASSED} gate without its check, date and evidence stops
 * the application from starting — it never falls back to "no gates". An entry recorded for another
 * {@code rule_version} than the compiled one never counts: the pack is all-pending and flagged
 * {@code stale}.
 *
 * <p>The file is validated structurally here and against {@code release-gates.schema.json} in the
 * build (the JSON Schema library is test scope, as for the compatibility matrix). Evidence
 * references are repo-relative documents that the jar does not carry: their existence and SHA-256
 * are checked by {@code ReleaseGatesConsistencyTest}, at build time, and only their shape here.
 */
public final class ReleaseGateRegistry {

    public static final String RESOURCE = "/indicators/release-gates.json";

    private static final String SCHEMA_VERSION = "1";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Map<String, Entry>> entries;

    /** What the file records for one pack at one rule version. */
    private record Entry(GateCheck sourceAndValidity, GateCheck reconciliation) {}

    private ReleaseGateRegistry(Map<String, Map<String, Entry>> entries) {
        this.entries = entries;
    }

    /** Loaded once; a broken file makes the first caller, and so the application, fail. */
    private static final class Holder {
        private static final ReleaseGateRegistry INSTANCE = fromClasspath();
    }

    /** The registry packaged with this release. */
    public static ReleaseGateRegistry bundled() {
        return Holder.INSTANCE;
    }

    /** The packs that must have an entry: every compiled rule, and the Nota Final. */
    public static List<PackDescriptor> registeredPacks() {
        List<PackDescriptor> packs = new ArrayList<>();
        for (var rule : IndicatorRuleRegistry.all()) {
            packs.add(rule.descriptor());
        }
        packs.add(ComponentIII.DESCRIPTOR);
        return List.copyOf(packs);
    }

    static ReleaseGateRegistry fromClasspath() {
        InputStream resource = ReleaseGateRegistry.class.getResourceAsStream(RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Packaged release-gate registry is missing: " + RESOURCE);
        }
        try (resource) {
            return fromJson(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read packaged release-gate registry", e);
        }
    }

    public static ReleaseGateRegistry fromJson(String json) {
        return fromJson(json, registeredPacks());
    }

    /** Parses and checks {@code json}; {@code registered} are the packs that need an entry. */
    public static ReleaseGateRegistry fromJson(String json, Collection<PackDescriptor> registered) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (RuntimeException e) { // NOPMD - any parse failure of the registry is fatal, with its cause
            throw new IllegalStateException("Release-gate registry is not valid JSON", e);
        }
        if (!root.isObject() || !SCHEMA_VERSION.equals(text(root, "schema_version"))) {
            throw new IllegalStateException("Release-gate registry: unsupported schema_version");
        }
        JsonNode packs = root.get("packs");
        if (packs == null || !packs.isArray()) {
            throw new IllegalStateException("Release-gate registry: packs must be an array");
        }
        Set<String> known = new HashSet<>();
        registered.forEach(d -> known.add(d.id()));
        Map<String, Map<String, Entry>> entries = new HashMap<>();
        for (JsonNode node : packs) {
            addEntry(entries, node, known);
        }
        for (String id : known) {
            if (!entries.containsKey(id)) {
                throw new IllegalStateException("Release-gate registry has no entry for registered pack " + id);
            }
        }
        return new ReleaseGateRegistry(entries);
    }

    private static void addEntry(Map<String, Map<String, Entry>> entries, JsonNode node, Set<String> known) {
        String pack = requireText(node, "pack", "entry");
        String ruleVersion = requireText(node, "rule_version", pack);
        if (!known.contains(pack)) {
            throw new IllegalStateException("Release-gate registry names an unregistered pack " + pack);
        }
        if (!ruleVersion.startsWith(pack + "@")) {
            throw new IllegalStateException(
                    "Release-gate registry: rule_version " + ruleVersion + " must be " + pack + "@<version>");
        }
        JsonNode gates = node.get("gates");
        if (gates == null || !gates.isObject()) {
            throw new IllegalStateException("Release-gate registry: " + ruleVersion + " has no gates object");
        }
        for (String name : gates.propertyNames()) {
            if (!isRegisteredGate(name)) {
                throw new IllegalStateException(
                        "Release-gate registry: " + ruleVersion + " carries gate " + name + " (only A and D)");
            }
        }
        JsonNode closed = node.get("blocking_gaps_closed");
        if (closed == null || !closed.isArray()) {
            throw new IllegalStateException("Release-gate registry: " + ruleVersion + " needs blocking_gaps_closed");
        }
        Entry entry = new Entry(gate(gates, GateId.A, ruleVersion), gate(gates, GateId.D, ruleVersion));
        if (entries.computeIfAbsent(pack, k -> new HashMap<>()).putIfAbsent(ruleVersion, entry) != null) {
            throw new IllegalStateException("Release-gate registry: duplicate entry for " + ruleVersion);
        }
    }

    private static boolean isRegisteredGate(String name) {
        for (GateId id : GateId.values()) {
            if (id.isRegistered() && id.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static GateCheck gate(JsonNode gates, GateId id, String ruleVersion) {
        String where = ruleVersion + " gate " + id;
        JsonNode node = gates.get(id.name());
        if (node == null || !node.isObject()) {
            throw new IllegalStateException("Release-gate registry: " + where + " is missing");
        }
        GateCheck.State state = state(requireText(node, "status", where), where);
        String check = text(node, "check");
        String checkedAt = text(node, "checked_at");
        List<GateCheck.Evidence> evidence = evidence(node.get("evidence"), where);
        if (state == GateCheck.State.PENDING) {
            if (check != null || checkedAt != null) {
                throw new IllegalStateException(
                        "Release-gate registry: " + where + " is PENDING but names a check or a date");
            }
        } else {
            requireDate(checkedAt, where);
            if (check == null || check.isBlank()) {
                throw new IllegalStateException("Release-gate registry: " + where + " " + state + " needs a check");
            }
            if (state == GateCheck.State.PASSED && evidence.isEmpty()) {
                throw new IllegalStateException("Release-gate registry: " + where + " PASSED needs evidence");
            }
        }
        return new GateCheck(state, check, checkedAt, evidence, text(node, "note"));
    }

    private static GateCheck.State state(String status, String where) {
        try {
            return GateCheck.State.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Release-gate registry: " + where + " has unknown status " + status, e);
        }
    }

    private static void requireDate(String checkedAt, String where) {
        try {
            LocalDate.parse(checkedAt == null ? "" : checkedAt);
        } catch (DateTimeException e) {
            throw new IllegalStateException("Release-gate registry: " + where + " needs checked_at as yyyy-MM-dd", e);
        }
    }

    private static List<GateCheck.Evidence> evidence(JsonNode node, String where) {
        if (node == null || !node.isArray()) {
            throw new IllegalStateException("Release-gate registry: " + where + " needs an evidence array");
        }
        List<GateCheck.Evidence> items = new ArrayList<>();
        for (JsonNode item : node) {
            String sha = requireText(item, "sha256", where + " evidence");
            if (!SHA256.matcher(sha).matches()) {
                throw new IllegalStateException(
                        "Release-gate registry: " + where + " evidence sha256 must be 64 lowercase hex digits");
            }
            items.add(new GateCheck.Evidence(
                    requireText(item, "kind", where + " evidence"),
                    requireText(item, "ref", where + " evidence"),
                    sha));
        }
        return List.copyOf(items);
    }

    /**
     * What the registry says about this compiled pack: A and D as recorded for its exact {@code
     * rule_version}, B and C pending until the executor evaluates them. A pack the registry has no
     * entry for at this version is all-pending — and {@code stale} when it has one for another.
     */
    public GateStatus statusOf(PackDescriptor descriptor) {
        Map<String, Entry> versions = entries.get(descriptor.id());
        if (versions == null) {
            return GateStatus.pending(descriptor.id(), descriptor.ruleVersion(), false);
        }
        Entry entry = versions.get(descriptor.ruleVersion());
        if (entry == null) {
            return GateStatus.pending(descriptor.id(), descriptor.ruleVersion(), true);
        }
        Map<GateId, GateCheck> gates = new EnumMap<>(GateId.class);
        gates.put(GateId.A, entry.sourceAndValidity());
        gates.put(GateId.B, GateCheck.pending(null));
        gates.put(GateId.C, GateCheck.pending(null));
        gates.put(GateId.D, entry.reconciliation());
        return new GateStatus(descriptor.id(), descriptor.ruleVersion(), gates, false);
    }

    /**
     * The gate state a staged result carries (V12 {@code gate_snapshot_json}): pack, rule version,
     * whether the registry was stale, and per gate its status, check, date and evidence references.
     */
    public static String snapshotJson(GateStatus status) {
        Map<String, Object> gates = new LinkedHashMap<>();
        for (GateId id : GateId.values()) {
            GateCheck check = status.check(id);
            Map<String, Object> gate = new LinkedHashMap<>();
            gate.put("status", check.state().name());
            gate.put("check", check.check());
            gate.put("checked_at", check.checkedAt());
            gate.put(
                    "evidence",
                    check.evidence().stream().map(GateCheck.Evidence::ref).toList());
            gate.put("note", check.note());
            gates.put(id.name(), gate);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pack", status.pack());
        snapshot.put("rule_version", status.ruleVersion());
        snapshot.put("stale", status.stale());
        snapshot.put("gates", gates);
        return MAPPER.writeValueAsString(snapshot);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String requireText(JsonNode node, String field, String where) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Release-gate registry: " + where + " needs " + field);
        }
        return value;
    }
}
