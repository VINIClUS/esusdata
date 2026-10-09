package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredConvention;
import esusdata.indicator.reconciliation.MethodologyProfile.DeclaredLimitation;
import esusdata.indicator.reconciliation.MethodologyProfile.Source;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The compatibility dossier of one {@code pack x reference_id} (spec section 8.4): what the
 * evaluator read, how it decided and why. {@link CompatibilityDossierWriter} writes it; the JSON
 * holds masked counts only, and the local detail of the probes and of the field comparison stays in
 * their records.
 *
 * <p>The dossier never ends {@link ReferenceCompatibility#UNKNOWN}: that is the state of a reference
 * nobody judged.
 *
 * @param referenceId the reference revision
 * @param pack the pack the dossier is about
 * @param ruleVersion the compiled rule version
 * @param quadrimestre the SIAPS quadrimestre of the reference, {@code 2026Q1}
 * @param referenceManifestSha256 the manifest of the revision the verdict is scoped to
 * @param localSourceFingerprint the local source the probes read, {@code sha256:<hex>}
 * @param officialMethodologySources the sources of the official edition of the quadrimestre; the
 *     ones of every edition of the profile when the profile has none for it. Never empty
 * @param normativeDeltas the dimensions the edition does not read as the local rule, in profile order
 * @param probeResults the results the evaluator was given, dimensions first, then conventions
 * @param officialFieldComparison local against official figures: a diagnostic, never read by the verdict
 * @param coverage how the type of the revision's teams was read
 * @param verdict the decision; never {@code UNKNOWN}
 * @param reason one sentence naming the step of the decision table and the ids that decided
 * @param declaredLimitations the limitations of the profile, which nothing local observes
 * @param declaredConventions the conventions of the profile, each with its probe result if there is one
 * @param siblingVerdicts for the Nota Final, the verdict of each sibling pack of the quadrimestre by
 *     pack id; empty for a pack
 */
public record CompatibilityDossier(
        String referenceId,
        String pack,
        String ruleVersion,
        String quadrimestre,
        String referenceManifestSha256,
        String localSourceFingerprint,
        List<Source> officialMethodologySources,
        List<NormativeDelta> normativeDeltas,
        List<ProbeEntry> probeResults,
        OfficialFieldComparison officialFieldComparison,
        TeamTypeCoverage coverage,
        ReferenceCompatibility verdict,
        String reason,
        List<DeclaredLimitation> declaredLimitations,
        List<ConventionEntry> declaredConventions,
        Map<String, ReferenceCompatibility> siblingVerdicts) {

    /** Whether a result belongs to a dimension, which the verdict reads, or to a convention, which it does not. */
    public enum Role {
        DIMENSION,
        CONVENTION
    }

    /**
     * A dimension the official edition does not read as the local rule.
     *
     * @param dimensionId the dimension
     * @param localReading how the local rule reads it
     * @param officialReading {@code DIFFERENT} or {@code UNKNOWN}
     * @param officialReadingText the official text, for {@code DIFFERENT} only
     * @param sourceId the source of the reading
     * @param probeId the probe that proves whether the other reading matters
     * @param decisionRefs the decision records behind the local reading
     */
    public record NormativeDelta(
            String dimensionId,
            String localReading,
            OfficialReading officialReading,
            Optional<String> officialReadingText,
            String sourceId,
            Optional<String> probeId,
            List<String> decisionRefs) {

        public NormativeDelta {
            Objects.requireNonNull(dimensionId, "dimensionId");
            Objects.requireNonNull(officialReading, "officialReading");
            if (officialReading == OfficialReading.SAME) {
                throw new IllegalArgumentException(dimensionId + " reads SAME: it is not a normative delta");
            }
            decisionRefs = List.copyOf(decisionRefs);
        }
    }

    /**
     * A probe result with the role it played and the dimension or convention it belongs to.
     *
     * @param role dimension or convention
     * @param subjectId the dimension or convention id
     * @param result what the probe found
     */
    public record ProbeEntry(Role role, String subjectId, ProbeResult result) {

        public ProbeEntry {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(subjectId, "subjectId");
            Objects.requireNonNull(result, "result");
        }
    }

    /**
     * A declared convention with the probe result recorded for it, if the probe ran. The verdict
     * never reads it: it shows how large the convention is.
     *
     * @param convention the convention of the profile
     * @param result the probe result, absent when the convention has no probe or it was not given
     */
    public record ConventionEntry(DeclaredConvention convention, Optional<ProbeResult> result) {

        public ConventionEntry {
            Objects.requireNonNull(convention, "convention");
            Objects.requireNonNull(result, "result");
        }
    }

    public CompatibilityDossier {
        Objects.requireNonNull(referenceId, "referenceId");
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(ruleVersion, "ruleVersion");
        Objects.requireNonNull(quadrimestre, "quadrimestre");
        Objects.requireNonNull(referenceManifestSha256, "referenceManifestSha256");
        Objects.requireNonNull(localSourceFingerprint, "localSourceFingerprint");
        Objects.requireNonNull(officialFieldComparison, "officialFieldComparison");
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reason, "reason");
        officialMethodologySources = List.copyOf(officialMethodologySources);
        normativeDeltas = List.copyOf(normativeDeltas);
        probeResults = List.copyOf(probeResults);
        declaredLimitations = List.copyOf(declaredLimitations);
        declaredConventions = List.copyOf(declaredConventions);
        siblingVerdicts = Map.copyOf(new TreeMap<>(siblingVerdicts));
        if (officialMethodologySources.isEmpty()) {
            throw new IllegalArgumentException("a dossier names the official sources it read; it is never empty");
        }
        if (!verdict.isDecided()) {
            throw new IllegalArgumentException("a dossier decides: " + verdict + " is the state of no dossier");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("a dossier says why");
        }
    }
}
