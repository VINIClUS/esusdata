package esusdata.indicator.reconciliation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What the gate reads of a committed compatibility dossier (spec §8.4): the facts that bind it to
 * one reference, one rule version and one manifest, its verdict and the local source it was decided
 * on. It is read from the committed bytes, never from a {@link CompatibilityDossier} in memory: the
 * gate judges the file the declaration pins. This is the only reading of a dossier by the gate;
 * {@code PortaoDCompatibilityRun.GateCheck} and {@link ReferenceSetVerdict#aggregate} both go
 * through {@link #problems}.
 *
 * @param referenceId the reference the dossier says it is about
 * @param ruleVersion the compiled rule version it says it is about
 * @param referenceManifestSha256 the manifest it says it is scoped to
 * @param verdict its verdict; {@link ReferenceCompatibility#UNKNOWN} when the file names none that exists
 * @param localSourceFingerprint the local source it was decided on, {@code sha256:<hex>}
 * @param ref the repo-relative path it was read from
 * @param sha256 the SHA-256 of the bytes that were read
 * @param officialFieldComparison its {@code official_field_comparison} block, masked counts as text
 */
public record DossierEvidence(
        String referenceId,
        String ruleVersion,
        String referenceManifestSha256,
        ReferenceCompatibility verdict,
        String localSourceFingerprint,
        String ref,
        String sha256,
        JsonNode officialFieldComparison) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public DossierEvidence {
        Objects.requireNonNull(referenceId, "referenceId");
        Objects.requireNonNull(ruleVersion, "ruleVersion");
        Objects.requireNonNull(referenceManifestSha256, "referenceManifestSha256");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(localSourceFingerprint, "localSourceFingerprint");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(officialFieldComparison, "officialFieldComparison");
    }

    /**
     * Reads the dossier committed at {@code ref}.
     *
     * @param bytes the content of the file
     * @return the dossier, or empty when the bytes are not a JSON object
     */
    public static Optional<DossierEvidence> read(String ref, byte[] bytes) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(bytes);
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
        if (!tree.isObject()) {
            return Optional.empty();
        }
        return Optional.of(new DossierEvidence(
                tree.path("reference_id").asString(""),
                tree.path("rule_version").asString(""),
                tree.path("reference_manifest_sha256").asString(""),
                verdictOf(tree.path("verdict").asString("")),
                tree.path("local_source_fingerprint").asString(""),
                ref,
                SummaryWriter.sha256(bytes),
                tree.path("official_field_comparison")));
    }

    private static ReferenceCompatibility verdictOf(String name) {
        return Stream.of(ReferenceCompatibility.values())
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElse(ReferenceCompatibility.UNKNOWN);
    }

    /**
     * Everything that keeps this dossier from standing for {@code declaration} in {@code set}; empty
     * when it does. The dossier must be the one the declaration pins, about the same reference, rule
     * version and manifest, with the verdict the declaration states, and that verdict must authorize
     * the gate.
     */
    public List<String> problems(ReferenceSet set, ReferenceDeclaration declaration) {
        List<String> problems = new ArrayList<>();
        if (!sha256.equals(declaration.compatibilityEvidenceSha256())) {
            problems.add("the dossier is not the one the declaration pins");
        }
        if (!declaration.referenceId().equals(referenceId)) {
            problems.add("the dossier is of another reference");
        }
        if (!set.ruleVersion().equals(ruleVersion)) {
            problems.add("the dossier is of another rule version");
        }
        if (!declaration.referenceManifestSha256().equals(referenceManifestSha256)) {
            problems.add("the dossier is of another manifest");
        }
        if (declaration.compatibility() != verdict) {
            problems.add("the dossier verdict is not the declared compatibility");
        }
        if (!verdict.authorizesGate()) {
            problems.add("the dossier verdict does not authorize the gate");
        }
        return List.copyOf(problems);
    }
}
