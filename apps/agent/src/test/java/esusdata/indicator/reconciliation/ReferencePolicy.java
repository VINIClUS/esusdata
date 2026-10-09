package esusdata.indicator.reconciliation;

import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.componente3.ComponentIII;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The SIAPS reference policy of the Portão D (spec 2026-10-08 §8.1): {@code
 * contracts/indicators/siaps-reference-policy.json}, which pre-registers, for every compiled rule,
 * the official references D may be decided against. It is dev tooling of the Portão D and is not
 * packaged in the jar.
 *
 * <p>Like the release-gate registry, the loader does not run the JSON Schema; it checks the same
 * things itself, so the two cannot drift apart ({@code ReferencePolicyTest} feeds both the same bad
 * documents). It is strict by design: a duplicated key, a field the contract does not name (a date,
 * "latest", a floor), a value of the wrong JSON type or a second set for a rule is refused, and so
 * is a compiled rule without a set. The rules, in the order they are checked:
 *
 * <ol>
 *   <li>the shape of every object and value;
 *   <li>one set per compiled {@code pack + rule_version}, with the right {@code @2} check for the
 *       pack ({@link #CHECK_NOTA_FINAL} for the Nota Final, {@link #CHECK_DISTRIBUTION} for C1 to
 *       C7);
 *   <li>{@code reference_id} unique across all sets;
 *   <li>the declaration rules of {@link ReferenceDeclaration#violations()};
 *   <li>the id naming the pack of its set, and the dossier path being {@link #dossierPath}.
 * </ol>
 *
 * Files that the declarations point at (manifests and dossiers) are not read here; {@code
 * ReferencePolicyConsistencyTest} checks them against the repository.
 */
public final class ReferencePolicy {

    /** The check that decides D for C1 to C7 under this policy. */
    public static final String CHECK_DISTRIBUTION = "siaps-distribuicao-por-classe@2";

    /** The check that decides D for the Nota Final do Componente III. */
    public static final String CHECK_NOTA_FINAL = "siaps-nota-final-por-classe@2";

    /** Where the manifest of each reference revision lives, by {@code reference_id} (spec §8.2). */
    public static final String MANIFEST_DIR = "docs/indicadores/portoes/references/";

    /** Where the compatibility dossier of each {@code reference_id} and pack lives (spec §8.4). */
    public static final String DOSSIER_DIR = "docs/indicadores/portoes/compatibilidade/";

    /** The {@code schema_version} of the compatibility dossiers the policy reads (Task 7 writes them). */
    public static final String DOSSIER_SCHEMA_VERSION = "siaps-compatibility-dossier@1";

    private static final String PREFIX = "SIAPS reference policy: ";
    private static final String SCHEMA_VERSION = "1";
    private static final String CIII_CODE = "ciii";
    private static final Pattern QUADRIMESTRE = Pattern.compile("[0-9]{4}Q[1-3]");
    private static final Pattern IBGE = Pattern.compile("[0-9]{7}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern DOSSIER_REF = Pattern.compile(Pattern.quote(DOSSIER_DIR) + "[a-z0-9-]+\\.json");
    private static final Pattern DATE_LIKE = Pattern.compile(
            "(?i)date|_at|since|until|after|before|latest|newest|recent|floor|signed|signature|valid|expire|deadline|cutoff");

    /** Strict reading: a repeated key or trailing content is a broken contract, not a last-one-wins. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final List<ReferenceSet> sets;

    private ReferencePolicy(List<ReferenceSet> sets) {
        this.sets = sets;
    }

    /**
     * Reads and checks the policy at {@code file}.
     *
     * @param compiled the rules of this release: each needs a set, no other pack may have one
     * @throws IOException if the file cannot be read
     */
    public static ReferencePolicy load(Path file, Collection<PackDescriptor> compiled) throws IOException {
        return fromJson(Files.readString(file, StandardCharsets.UTF_8), compiled);
    }

    /** Parses and checks {@code json}; an invalid policy is an {@link IllegalStateException}. */
    public static ReferencePolicy fromJson(String json, Collection<PackDescriptor> compiled) {
        Fields root = new Fields(parse(json), "policy");
        if (!SCHEMA_VERSION.equals(root.text("schema_version"))) {
            throw invalid("unsupported schema_version");
        }
        JsonNode nodes = root.require("reference_sets");
        root.done();
        if (!nodes.isArray() || nodes.isEmpty()) {
            throw invalid("reference_sets must be a non-empty array");
        }
        Map<String, PackDescriptor> byPack = compiled.stream().collect(Collectors.toMap(PackDescriptor::id, d -> d));
        Set<String> ids = new HashSet<>();
        Map<String, ReferenceSet> byRule = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            ReferenceSet set = parseSet(node, byPack, ids);
            if (byRule.putIfAbsent(set.ruleVersion(), set) != null) {
                throw invalid("duplicate reference set for " + set.ruleVersion());
            }
        }
        for (PackDescriptor descriptor : compiled) {
            if (!byRule.containsKey(descriptor.ruleVersion())) {
                throw invalid("has no reference set for compiled rule " + descriptor.ruleVersion());
            }
        }
        return new ReferencePolicy(List.copyOf(byRule.values()));
    }

    /** Every set, in the order of the file. */
    public List<ReferenceSet> referenceSets() {
        return sets;
    }

    /**
     * The set of a compiled rule.
     *
     * @throws IllegalArgumentException if the policy has no set for exactly this pack and version
     */
    public ReferenceSet referenceSet(String packId, String ruleVersion) {
        return sets.stream()
                .filter(set -> set.pack().equals(packId) && set.ruleVersion().equals(ruleVersion))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no reference set for " + packId + " " + ruleVersion));
    }

    /** The check that decides D for a pack: the Nota Final has its own, C1 to C7 share one. */
    public static String checkFor(String packId) {
        return ComponentIII.ID.equals(packId) ? CHECK_NOTA_FINAL : CHECK_DISTRIBUTION;
    }

    /** The pack code a {@code reference_id} carries for this pack: {@code c1} to {@code c7}, {@code ciii}. */
    public static String packCode(PackDescriptor descriptor) {
        return ComponentIII.ID.equals(descriptor.id())
                ? CIII_CODE
                : descriptor.code().toLowerCase(Locale.ROOT);
    }

    /** The repo-relative path of the manifest of a reference revision. */
    public static String manifestPath(String referenceId) {
        return MANIFEST_DIR + referenceId + ".json";
    }

    /** The repo-relative path of the dossier of a reference for a pack. */
    public static String dossierPath(String referenceId, String packId) {
        return DOSSIER_DIR + referenceId + "-" + packId + ".json";
    }

    private static ReferenceSet parseSet(JsonNode node, Map<String, PackDescriptor> compiled, Set<String> ids) {
        Fields fields = new Fields(node, "reference set");
        String pack = fields.text("pack");
        String ruleVersion = fields.text("rule_version");
        String check = fields.text("check");
        SelectionPolicy selection = fields.choice("selection_policy", SelectionPolicy.class);
        JsonNode references = fields.require("references");
        fields.done();
        PackDescriptor descriptor = compiled.get(pack);
        if (descriptor == null) {
            throw invalid("names unregistered pack " + pack);
        }
        if (!descriptor.ruleVersion().equals(ruleVersion)) {
            throw invalid("rule_version " + ruleVersion + " is not the compiled " + descriptor.ruleVersion());
        }
        if (!checkFor(pack).equals(check)) {
            throw invalid(ruleVersion + " check must be " + checkFor(pack) + ", not " + check);
        }
        if (!references.isArray()) {
            throw invalid(ruleVersion + " references must be an array");
        }
        List<ReferenceDeclaration> declarations = new ArrayList<>();
        for (JsonNode item : references) {
            ReferenceDeclaration declaration =
                    parseDeclaration(item, ruleVersion + " reference #" + (declarations.size() + 1));
            if (!ids.add(declaration.referenceId())) {
                throw invalid("duplicate reference_id " + declaration.referenceId());
            }
            requireSound(descriptor, declaration);
            declarations.add(declaration);
        }
        return new ReferenceSet(pack, ruleVersion, check, selection, declarations);
    }

    private static ReferenceDeclaration parseDeclaration(JsonNode node, String where) {
        Fields fields = new Fields(node, where);
        ReferenceDeclaration declaration = new ReferenceDeclaration(
                fields.pattern("reference_id", ReferenceDeclaration.ID),
                fields.pattern("quadrimestre", QUADRIMESTRE),
                fields.pattern("municipality_ibge", IBGE),
                fields.choice("source_kind", SourceKind.class),
                fields.choice("purpose", ReferencePurpose.class),
                fields.flag("required"),
                fields.choice("status", ReferenceStatus.class),
                fields.choice("compatibility", ReferenceCompatibility.class),
                fields.pattern("reference_manifest_sha256", SHA256),
                fields.patternOrNull("compatibility_evidence_ref", DOSSIER_REF),
                fields.patternOrNull("compatibility_evidence_sha256", SHA256),
                fields.optionalText("note"));
        fields.done();
        return declaration;
    }

    /** The rules of the declaration itself, then those that need the set around it. */
    private static void requireSound(PackDescriptor descriptor, ReferenceDeclaration declaration) {
        List<String> problems = new ArrayList<>(declaration.violations());
        String code = packCode(descriptor);
        if (!code.equals(declaration.packCode())) {
            problems.add("reference_id names pack " + declaration.packCode() + " but the set is " + descriptor.id()
                    + " (" + code + ")");
        }
        String ref = declaration.compatibilityEvidenceRef();
        String dossier = dossierPath(declaration.referenceId(), descriptor.id());
        if (ref != null && !ref.equals(dossier)) {
            problems.add("compatibility_evidence_ref must be " + dossier + ", not " + ref);
        }
        if (!problems.isEmpty()) {
            throw invalid("reference " + declaration.referenceId() + ": " + String.join("; ", problems));
        }
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw invalid("is not valid JSON", e);
        }
    }

    private static IllegalStateException invalid(String detail) {
        return new IllegalStateException(PREFIX + detail);
    }

    private static IllegalStateException invalid(String detail, Throwable cause) {
        return new IllegalStateException(PREFIX + detail, cause);
    }

    /**
     * The fields of one JSON object as the loader reads them. Each field read is marked, with its
     * JSON type enforced (a string is never coerced from a number, a flag never from a string);
     * {@link #done()} then refuses whatever was not read. The list of allowed fields is therefore
     * the code that reads them, and cannot disagree with it.
     */
    private static final class Fields {

        private final JsonNode node;
        private final String where;
        private final Set<String> read = new HashSet<>();

        Fields(JsonNode node, String where) {
            if (!node.isObject()) {
                throw invalid(where + " must be an object");
            }
            this.node = node;
            this.where = where;
        }

        /** The value of a field that must be present, JSON null included. */
        JsonNode require(String name) {
            read.add(name);
            JsonNode value = node.get(name);
            if (value == null) {
                throw invalid(where + " needs " + name);
            }
            return value;
        }

        String text(String name) {
            JsonNode value = require(name);
            if (!value.isString()) {
                throw invalid(where + " " + name + " must be a string");
            }
            return value.stringValue();
        }

        String pattern(String name, Pattern shape) {
            String text = text(name);
            if (!shape.matcher(text).matches()) {
                throw invalid(where + " " + name + " " + text + " does not match " + shape.pattern());
            }
            return text;
        }

        /** A string of this shape, or JSON null. */
        String patternOrNull(String name, Pattern shape) {
            return require(name).isNull() ? null : pattern(name, shape);
        }

        boolean flag(String name) {
            JsonNode value = require(name);
            if (!value.isBoolean()) {
                throw invalid(where + " " + name + " must be true or false");
            }
            return value.booleanValue();
        }

        /** A string when the field is there; {@code null} when it is absent. */
        String optionalText(String name) {
            return node.has(name) ? text(name) : null;
        }

        <E extends Enum<E>> E choice(String name, Class<E> type) {
            String text = text(name);
            try {
                return Enum.valueOf(type, text);
            } catch (IllegalArgumentException e) {
                throw invalid(
                        where + " " + name + " " + text + " is not one of " + List.of(type.getEnumConstants()), e);
            }
        }

        /** Refuses the fields nobody read: the contract names them all, and none of them is a date. */
        void done() {
            for (String name : node.propertyNames()) {
                if (!read.contains(name)) {
                    String hint = DATE_LIKE.matcher(name).find()
                            ? " (a reference is chosen by its id, never by a date or by recency)"
                            : "";
                    throw invalid(where + " has unknown field " + name + hint);
                }
            }
        }
    }
}
