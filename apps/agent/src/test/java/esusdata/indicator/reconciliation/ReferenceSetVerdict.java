package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.Comparison.RowResult;
import esusdata.indicator.reconciliation.PackVerdict.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/**
 * The Portão D verdict of one pre-registered reference set (spec §14, ADR 0034): the verdicts of
 * its {@code GATE} references under {@code ALL_REQUIRED}. The set passes only when it has at least
 * one {@code GATE} reference and every one of them passes; one that fails fails the set; otherwise
 * the set is pending. {@code DIAGNOSTIC} declarations, and verdicts keyed to them, are ignored.
 *
 * <p>A reference counts only if it is fit for the gate, and that is decided <em>before</em> its
 * figures are read: a local verdict that is missing, is not a gate one or is of another pack, rule
 * version or quadrimestre; a dossier that is missing or does not stand for the declaration (see
 * {@link DossierEvidence#problems}); a local source other than the one the dossier was decided on.
 * Any of these makes the reference {@code PENDING}, whatever the figures say, so a {@code FAILED}
 * computed from a source the dossier never covered does not fail the set. Once the reference is
 * fit, nothing softens its verdict: a local calculation error on an {@code EXACT} or {@code
 * EQUIVALENT_FOR_REFERENCE} reference is {@code FAILED}.
 *
 * <p>A {@code GATE} declaration that is superseded, retracted or not {@code ACTIVE} never reaches
 * here: {@link ReferenceDeclaration#violations()} refuses it when the policy is loaded.
 *
 * @param pack the pack id
 * @param ruleVersion the compiled rule version
 * @param check the set's check, {@code ...@2}
 * @param gateSetSha256 the hash of the set as pre-registered, the one D cites
 * @param status the verdict of the set
 * @param reason why, in one sentence; empty for a set that passes
 * @param references one outcome per {@code GATE} reference, in declaration order
 */
public record ReferenceSetVerdict(
        String pack,
        String ruleVersion,
        String check,
        String gateSetSha256,
        Status status,
        String reason,
        List<ReferenceOutcome> references) {

    /**
     * One {@code GATE} reference of the set.
     *
     * @param referenceId the reference
     * @param referenceManifestSha256 the manifest the declaration pins
     * @param dossierRef the dossier the declaration cites
     * @param dossierSha256 its pinned SHA-256
     * @param compatibility the compatibility the declaration states
     * @param localSourceFingerprint the local source the dossier says it was decided on, empty when
     *     there is no dossier
     * @param status this reference's verdict
     * @param reason why it is not {@code PASSED}, or the reason of its figures
     * @param rows the figures of the local verdict (N_S, N_L, D and T per team type); empty when the
     *     reference is pending for a reason that is not about the figures
     * @param officialFieldComparison the {@code official_field_comparison} block of the dossier, or
     *     a missing node when there is no dossier
     */
    public record ReferenceOutcome(
            String referenceId,
            String referenceManifestSha256,
            String dossierRef,
            String dossierSha256,
            ReferenceCompatibility compatibility,
            String localSourceFingerprint,
            Status status,
            String reason,
            List<RowResult> rows,
            JsonNode officialFieldComparison) {

        public ReferenceOutcome {
            Objects.requireNonNull(referenceId, "referenceId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(officialFieldComparison, "officialFieldComparison");
            rows = List.copyOf(rows);
        }
    }

    public ReferenceSetVerdict {
        Objects.requireNonNull(status, "status");
        references = List.copyOf(references);
    }

    /**
     * Decides the set.
     *
     * @param verdictsByReferenceId the local verdict computed against each {@code GATE} reference
     * @param dossiersByReferenceId the committed dossier of each one, as {@link DossierEvidence} reads it
     */
    public static ReferenceSetVerdict aggregate(
            ReferenceSet set,
            Map<String, PackVerdict> verdictsByReferenceId,
            Map<String, DossierEvidence> dossiersByReferenceId) {
        List<ReferenceOutcome> outcomes = new ArrayList<>();
        for (ReferenceDeclaration declaration : set.references()) {
            if (declaration.purpose() == ReferencePurpose.GATE) {
                outcomes.add(outcomeOf(
                        set,
                        declaration,
                        verdictsByReferenceId.get(declaration.referenceId()),
                        dossiersByReferenceId.get(declaration.referenceId())));
            }
        }
        return new ReferenceSetVerdict(
                set.pack(),
                set.ruleVersion(),
                set.check(),
                set.gateSetSha256(),
                statusOf(outcomes),
                reasonOf(outcomes),
                outcomes);
    }

    /** {@code ALL_REQUIRED}: no reference is pending, nor an empty set, for a pass; one failure fails. */
    static Status statusOf(List<ReferenceOutcome> outcomes) {
        if (outcomes.isEmpty()) {
            return Status.PENDING;
        }
        if (outcomes.stream().anyMatch(outcome -> outcome.status() == Status.FAILED)) {
            return Status.FAILED;
        }
        return outcomes.stream().anyMatch(outcome -> outcome.status() == Status.PENDING)
                ? Status.PENDING
                : Status.PASSED;
    }

    private static String reasonOf(List<ReferenceOutcome> outcomes) {
        Status status = statusOf(outcomes);
        if (outcomes.isEmpty()) {
            return "sem referência GATE ativa e obrigatória";
        }
        if (status == Status.PASSED) {
            return "";
        }
        String ids = outcomes.stream()
                .filter(outcome -> outcome.status() == status)
                .map(ReferenceOutcome::referenceId)
                .collect(Collectors.joining(", "));
        return (status == Status.FAILED ? "reprovada: " : "pendente: ") + ids;
    }

    private static ReferenceOutcome outcomeOf(
            ReferenceSet set, ReferenceDeclaration declaration, PackVerdict verdict, DossierEvidence dossier) {
        List<String> invalid = invalidators(set, declaration, verdict, dossier);
        boolean fit = invalid.isEmpty();
        return new ReferenceOutcome(
                declaration.referenceId(),
                declaration.referenceManifestSha256(),
                declaration.compatibilityEvidenceRef(),
                declaration.compatibilityEvidenceSha256(),
                declaration.compatibility(),
                dossier == null ? "" : dossier.localSourceFingerprint(),
                fit ? verdict.status() : Status.PENDING,
                fit ? verdict.reason() : String.join("; ", invalid),
                fit ? verdict.rows() : List.of(),
                dossier == null ? MissingNode.getInstance() : dossier.officialFieldComparison());
    }

    /** Everything that keeps the reference out of the gate, before its figures are read. */
    private static List<String> invalidators(
            ReferenceSet set, ReferenceDeclaration declaration, PackVerdict verdict, DossierEvidence dossier) {
        List<String> problems = new ArrayList<>();
        problems.addAll(verdictProblems(set, declaration, verdict));
        problems.addAll(dossier == null ? List.of("there is no dossier") : dossier.problems(set, declaration));
        if (verdict != null
                && dossier != null
                && !verdict.localSourceFingerprint().equals(dossier.localSourceFingerprint())) {
            problems.add("the local source of the verdict is not the one the dossier was decided on");
        }
        return problems;
    }

    private static List<String> verdictProblems(
            ReferenceSet set, ReferenceDeclaration declaration, PackVerdict verdict) {
        if (verdict == null) {
            return List.of("there is no local verdict");
        }
        List<String> problems = new ArrayList<>();
        if (verdict.purpose() != ReferencePurpose.GATE) {
            problems.add("the local verdict is not a gate one");
        }
        if (!verdict.pack().packId().equals(set.pack())) {
            problems.add("the local verdict is of another pack");
        }
        if (!verdict.ruleVersion().equals(set.ruleVersion())) {
            problems.add("the local verdict is of another rule version");
        }
        // A verdict that compared nothing has no quadrimestre; its own reason says why.
        if ((verdict.quadrimestre() != null || verdict.status() != Status.PENDING)
                && !declaration.quadrimestre().equals(verdict.quadrimestre())) {
            problems.add("the local verdict is of another quadrimestre");
        }
        return problems;
    }
}
