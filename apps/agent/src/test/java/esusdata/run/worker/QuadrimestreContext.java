package esusdata.run.worker;

import esusdata.indicator.model.Quadrimestre;
import java.time.YearMonth;

/**
 * A {@link ReferenceExecutionContext} for the four months of its quadrimestre: what a pack run over
 * one reference revision expects of every extract it reads.
 *
 * @param municipalityIbge the 7-digit IBGE code of the municipality
 * @param quadrimestre the quadrimestre
 * @param referenceManifestSha256 SHA-256 (lowercase hex) of the manifest of the reference revision
 * @param packId the pack id
 * @param ruleVersion the rule version, {@code <pack>@<version>}
 * @param sourceIdentity the PEC the extracts are read from
 */
public record QuadrimestreContext(
        String municipalityIbge,
        Quadrimestre quadrimestre,
        String referenceManifestSha256,
        String packId,
        String ruleVersion,
        SourceIdentity sourceIdentity) {

    /** The context of one of the months of the quadrimestre. */
    public ReferenceExecutionContext at(YearMonth month) {
        return new ReferenceExecutionContext(
                municipalityIbge, quadrimestre, referenceManifestSha256, packId, ruleVersion, sourceIdentity, month);
    }
}
