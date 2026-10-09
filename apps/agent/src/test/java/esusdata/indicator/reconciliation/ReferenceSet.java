package esusdata.indicator.reconciliation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import tools.jackson.databind.ObjectMapper;

/**
 * The references pre-registered for one compiled rule (spec §8.1): one set per {@code pack +
 * rule_version}, decided by {@code check} under {@code selection_policy}. A new {@code
 * rule_version} needs a new set, so an approval of D never carries over to another version.
 *
 * @param pack the pack id
 * @param ruleVersion the compiled rule version, {@code <pack>@<version>}
 * @param check the automated check that decides D for this pack
 * @param selectionPolicy how the references decide D
 * @param references every declared reference, in declaration order
 */
public record ReferenceSet(
        String pack,
        String ruleVersion,
        String check,
        SelectionPolicy selectionPolicy,
        List<ReferenceDeclaration> references) {

    private static final ObjectMapper CANONICAL_JSON = new ObjectMapper();

    public ReferenceSet {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(ruleVersion, "ruleVersion");
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(selectionPolicy, "selectionPolicy");
        references = List.copyOf(references);
    }

    /**
     * The hash D cites for this set (spec §8.1 "Hash do conjunto de gate"): the SHA-256, lowercase
     * hex, of {@link #canonicalGateJson()}. It covers the gate declarations only, so declaring,
     * changing or dropping a {@code DIAGNOSTIC} reference never moves it, and any change to a
     * {@code GATE} declaration does. It does not depend on the order of the declarations or on the
     * whitespace of the policy file.
     */
    public String gateSetSha256() {
        return sha256(canonicalGateJson());
    }

    /**
     * What {@link #gateSetSha256()} hashes: a compact JSON object with sorted keys, in UTF-8, made of
     * {@code pack}, {@code rule_version}, {@code check}, {@code selection_policy} and {@code
     * gate_references}. The latter holds the {@code GATE} declarations only, each reduced to the
     * fields of {@link ReferenceDeclaration#gateCanonical()}. They are sorted by {@code
     * reference_id}, which only fixes the canonical form: nothing here ranks a reference over
     * another, and no date is involved.
     */
    public String canonicalGateJson() {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("pack", pack);
        canonical.put("rule_version", ruleVersion);
        canonical.put("check", check);
        canonical.put("selection_policy", selectionPolicy.name());
        canonical.put("gate_references", gateReferences());
        return CANONICAL_JSON.writeValueAsString(canonical);
    }

    private List<Map<String, Object>> gateReferences() {
        return references.stream()
                .filter(declaration -> declaration.purpose() == ReferencePurpose.GATE)
                .sorted(Comparator.comparing(ReferenceDeclaration::referenceId))
                .map(ReferenceDeclaration::gateCanonical)
                .toList();
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK provides SHA-256", e);
        }
    }
}
