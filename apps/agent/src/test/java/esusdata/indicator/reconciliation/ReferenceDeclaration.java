package esusdata.indicator.reconciliation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One reference a {@link ReferenceSet} pre-registers (spec §8.1): the official SIAPS result of one
 * municipality and quadrimestre from one kind of source, named by its {@code reference_id}. The id
 * is the only way to pick a reference. The {@code quadrimestre} identifies the period; nothing
 * orders or selects by it.
 *
 * <p>The constructor does not enforce the rules. {@link #violations()} reports them, so the loader
 * and the selector refuse the same declarations, and a test can still build a broken one.
 *
 * @param referenceId {@code <uf>-<ibge>-<year>q<n>-<c1..c7|ciii>-<team|agg|aggu>-r<revision>},
 *     unique across the whole policy
 * @param quadrimestre the period in the SIAPS spelling, e.g. {@code 2026Q1}
 * @param municipalityIbge the 7-digit IBGE code of the municipality
 * @param sourceKind where the reference comes from; {@code team}, {@code agg} and {@code aggu} in
 *     the id
 * @param purpose whether it only compares ({@code DIAGNOSTIC}) or decides D ({@code GATE})
 * @param required whether D needs it to pass; only a {@code GATE} reference is required
 * @param status the state of the captured revision
 * @param compatibility the verdict of the cited dossier, or {@code UNKNOWN} before there is one
 * @param referenceManifestSha256 SHA-256 of the manifest of the captured revision
 * @param compatibilityEvidenceRef repo-relative path of the dossier, or {@code null} before there
 *     is one
 * @param compatibilityEvidenceSha256 SHA-256 of the dossier, {@code null} exactly when the path is
 * @param note free text for people; never read by a check
 */
public record ReferenceDeclaration(
        String referenceId,
        String quadrimestre,
        String municipalityIbge,
        SourceKind sourceKind,
        ReferencePurpose purpose,
        boolean required,
        ReferenceStatus status,
        ReferenceCompatibility compatibility,
        String referenceManifestSha256,
        String compatibilityEvidenceRef,
        String compatibilityEvidenceSha256,
        String note) {

    private static final String PERIOD = "(?<year>[0-9]{4})q(?<index>[1-3])";
    private static final String PACK = "(?<pack>c[1-7]|ciii)";
    private static final String SOURCE = "(?<source>team|agg|aggu)";

    /**
     * The shape of a {@code reference_id}: {@code <uf>-<ibge>-<year>q<n>-<pack>-<source>-r<revision>}.
     * The groups are the facts the other fields must repeat.
     */
    static final Pattern ID =
            Pattern.compile("[a-z]{2}-(?<ibge>[0-9]{7})-" + PERIOD + "-" + PACK + "-" + SOURCE + "-r[0-9]+");

    private static final Map<String, SourceKind> SOURCE_OF_ID_CODE = Map.of(
            "team", SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
            "agg", SourceKind.PUBLIC_AGGREGATE,
            "aggu", SourceKind.PUBLIC_AGGREGATE_WITH_PERIOD_UNIVERSE);

    public ReferenceDeclaration {
        Objects.requireNonNull(referenceId, "referenceId");
        Objects.requireNonNull(quadrimestre, "quadrimestre");
        Objects.requireNonNull(municipalityIbge, "municipalityIbge");
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(compatibility, "compatibility");
        Objects.requireNonNull(referenceManifestSha256, "referenceManifestSha256");
    }

    /**
     * Everything wrong with this declaration by itself, in plain words; empty when it is sound. One
     * sentence per rule, so a caller or a test can tell the rules apart:
     *
     * <ul>
     *   <li>{@code GATE}: required, {@code ACTIVE}, {@code EXACT} or {@code EQUIVALENT_FOR_REFERENCE},
     *       with its dossier path and hash (spec §8.1 rule 3);</li>
     *   <li>{@code DIAGNOSTIC}: never required (rule 4), and never an {@code ACTIVE} reference whose
     *       dossier makes it eligible (rule 7);</li>
     *   <li>the dossier path and hash come together, an {@code UNKNOWN} compatibility cites none and
     *       every other one cites the dossier that decided it;</li>
     *   <li>the id agrees with the municipality, the quadrimestre and the source kind.</li>
     * </ul>
     */
    public List<String> violations() {
        List<String> found = new ArrayList<>();
        purposeViolations(found);
        citationViolations(found);
        idViolations(found);
        return List.copyOf(found);
    }

    private void purposeViolations(List<String> found) {
        if (purpose == ReferencePurpose.GATE) {
            gateViolations(found);
            return;
        }
        if (required) {
            found.add("a DIAGNOSTIC reference is never required");
        }
        if (status == ReferenceStatus.ACTIVE && compatibility.authorizesGate()) {
            found.add("an ACTIVE reference whose dossier is " + compatibility
                    + " belongs to the gate set (spec 8.1 rule 7) and cannot stay DIAGNOSTIC");
        }
    }

    private void gateViolations(List<String> found) {
        if (!required) {
            found.add("a GATE reference must be required");
        }
        if (status != ReferenceStatus.ACTIVE) {
            found.add("a GATE reference must be ACTIVE, not " + status);
        }
        if (!compatibility.authorizesGate()) {
            found.add("a GATE reference needs a dossier verdict of EXACT or EQUIVALENT_FOR_REFERENCE, not "
                    + compatibility);
        }
        if (compatibilityEvidenceRef == null) {
            found.add("a GATE reference needs compatibility_evidence_ref");
        }
        if (compatibilityEvidenceSha256 == null) {
            found.add("a GATE reference needs compatibility_evidence_sha256");
        }
    }

    private void citationViolations(List<String> found) {
        boolean cited = compatibilityEvidenceRef != null;
        if (cited != (compatibilityEvidenceSha256 != null)) {
            found.add("compatibility_evidence_ref and compatibility_evidence_sha256 must be set together");
        }
        if (compatibility == ReferenceCompatibility.UNKNOWN && cited) {
            found.add("an UNKNOWN compatibility cites no dossier: UNKNOWN exists only before the dossier does");
        }
        if (compatibility.isDecided() && !cited) {
            found.add("compatibility " + compatibility + " must cite the dossier that decided it");
        }
    }

    private void idViolations(List<String> found) {
        Matcher id = ID.matcher(referenceId);
        if (!id.matches()) {
            found.add("reference_id does not follow <uf>-<ibge>-<year>q<n>-<pack>-<team|agg|aggu>-r<revision>");
            return;
        }
        if (!municipalityIbge.equals(id.group("ibge"))) {
            found.add("reference_id names municipality " + id.group("ibge") + " but municipality_ibge is "
                    + municipalityIbge);
        }
        String named = id.group("year") + "Q" + id.group("index");
        if (!quadrimestre.equals(named)) {
            found.add("reference_id names quadrimestre " + named + " but quadrimestre is " + quadrimestre);
        }
        if (SOURCE_OF_ID_CODE.get(id.group("source")) != sourceKind) {
            found.add("reference_id names source " + id.group("source") + " but source_kind is " + sourceKind);
        }
    }

    /** The pack code the id carries ({@code c1} to {@code c7}, {@code ciii}); empty if it is malformed. */
    String packCode() {
        Matcher id = ID.matcher(referenceId);
        return id.matches() ? id.group("pack") : "";
    }

    /**
     * The fields of this declaration that the gate set hash covers, keyed and sorted by name
     * (spec §8.1): the identity of the reference, its state, and the two hashes that pin its
     * manifest and its dossier. Period, municipality, source kind and note are left out; the id
     * and the manifest hash already bind them.
     */
    Map<String, Object> gateCanonical() {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("reference_id", referenceId);
        canonical.put("required", required);
        canonical.put("status", status.name());
        canonical.put("compatibility", compatibility.name());
        canonical.put("reference_manifest_sha256", referenceManifestSha256);
        canonical.put("compatibility_evidence_ref", compatibilityEvidenceRef);
        canonical.put("compatibility_evidence_sha256", compatibilityEvidenceSha256);
        return canonical;
    }
}
