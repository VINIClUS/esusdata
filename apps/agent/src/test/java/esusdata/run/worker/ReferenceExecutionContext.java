package esusdata.run.worker;

import esusdata.indicator.model.Quadrimestre;
import java.time.YearMonth;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What one partition of {@link ReferenceScopedExtracts} is expected to be, known from outside the
 * extract: the municipality, the quadrimestre and the reference revision the run is for (the
 * SHA-256 of its manifest), the pack and rule version, the source it is read from and the month.
 * An extract is accepted against this and never against its own manifest, whose fields can only
 * agree with themselves.
 *
 * @param municipalityIbge the 7-digit IBGE code of the municipality
 * @param quadrimestre the quadrimestre the month belongs to
 * @param referenceManifestSha256 SHA-256 (lowercase hex) of the manifest of the reference revision
 * @param packId the pack id, e.g. {@code c1-mais-acesso}
 * @param ruleVersion the rule version, {@code <pack>@<version>}
 * @param sourceIdentity the PEC the extracts are read from
 * @param month the competência
 */
public record ReferenceExecutionContext(
        String municipalityIbge,
        Quadrimestre quadrimestre,
        String referenceManifestSha256,
        String packId,
        String ruleVersion,
        SourceIdentity sourceIdentity,
        YearMonth month) {

    private static final Pattern IBGE = Pattern.compile("\\d{7}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public ReferenceExecutionContext {
        if (municipalityIbge == null || !IBGE.matcher(municipalityIbge).matches()) {
            throw new IllegalArgumentException("municipalityIbge must be a 7-digit IBGE code");
        }
        Objects.requireNonNull(quadrimestre, "quadrimestre");
        if (referenceManifestSha256 == null
                || !SHA256.matcher(referenceManifestSha256).matches()) {
            throw new IllegalArgumentException("referenceManifestSha256 must be a lowercase hex SHA-256");
        }
        Objects.requireNonNull(packId, "packId");
        if (ruleVersion == null || !ruleVersion.startsWith(packId + "@")) {
            throw new IllegalArgumentException("ruleVersion must be <packId>@<version>");
        }
        Objects.requireNonNull(sourceIdentity, "sourceIdentity");
        if (!municipalityIbge.equals(sourceIdentity.municipalityIbge())) {
            throw new IllegalArgumentException("the source identity is that of another municipality");
        }
        Objects.requireNonNull(month, "month");
        if (!Quadrimestre.of(month).equals(quadrimestre)) {
            throw new IllegalArgumentException("month " + month + " is not in " + quadrimestre);
        }
    }

    /** The SIAPS spelling of the quadrimestre, {@code 2026Q1}: the name of its directory. */
    public String quadrimestreName() {
        return quadrimestre.year() + "Q" + quadrimestre.index();
    }
}
