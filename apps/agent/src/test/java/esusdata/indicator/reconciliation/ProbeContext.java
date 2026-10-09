package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * Everything a {@link MethodologyProbe} may look at for one reference (spec §9.4): the accepted
 * canonical dataset of one quadrimestre and the reference it is reconciled with, already checked
 * to agree with each other. Two shapes, because the Nota Final is not a rule over source records
 * (ADR 0030): {@link PackProbeContext} for C1 to C7, {@link NotaFinalProbeContext} for the
 * Componente III.
 *
 * <p>A context that does not hold together (a month missing or out of order, a reference of another
 * pack, quadrimestre or municipality, a malformed fingerprint) is refused when it is built, before
 * a probe can read anything from it.
 *
 * <p>A probe reads the data and the baseline. It does not read the official side: whether a
 * reference is compatible never rests on the local output agreeing with the official one (spec
 * §6.3, §9.5). {@link #reference()} and {@link #manifest()} are for the evaluator, which lives in
 * this package; their types are package-private, so a probe in another package can hold them but
 * not read them.
 */
public sealed interface ProbeContext {

    /** The pack the context is about: the id of the rule, or of the Nota Final. */
    String packId();

    /** The compiled rule version of {@link #packId()}. */
    String ruleVersion();

    /** The quadrimestre of the data and of the reference. */
    Quadrimestre quadrimestre();

    /** The official side, as validated for this pack. */
    ValidatedReference reference();

    /** The identity card of the reference revision. */
    SiapsReferenceManifest manifest();

    /** The {@code sha256:<64 hex>} fingerprint of the local source the data was read from. */
    String localSourceFingerprint();

    /**
     * The context of C1 to C7: the four monthly inputs of the quadrimestre in order, each with the
     * outcome the rule gave it as it is today (the baseline the other reading is compared with).
     *
     * @param quadrimestre the quadrimestre
     * @param inputs the rule, the accepted dataset and the evaluation context of each of its months
     * @param baseline the outcome of the rule for each input, in the same order
     * @param reference the official side for this pack
     * @param manifest the identity card of the reference revision
     * @param localSourceFingerprint what the inputs were read from
     */
    record PackProbeContext(
            Quadrimestre quadrimestre,
            List<PackInput> inputs,
            List<RuleOutcome> baseline,
            ValidatedReference reference,
            SiapsReferenceManifest manifest,
            String localSourceFingerprint)
            implements ProbeContext {

        public PackProbeContext {
            inputs = List.copyOf(inputs);
            baseline = List.copyOf(baseline);
            ProbeContexts.requirePack(quadrimestre, inputs, baseline, reference, manifest, localSourceFingerprint);
        }

        @Override
        public String packId() {
            return inputs.getFirst().rule().descriptor().id();
        }

        @Override
        public String ruleVersion() {
            return inputs.getFirst().rule().descriptor().ruleVersion();
        }
    }

    /**
     * The context of the Nota Final do Componente III: the monthly results by team of the seven
     * packs, the whole quadrimestre of each (a pack or month with nothing at all is refused; the
     * caller records that as a gap of local coverage instead).
     *
     * @param quadrimestre the quadrimestre
     * @param monthlyResults the results by team of every month, by pack id of C1 to C7
     * @param reference the official side for the Nota Final
     * @param manifest the identity card of the reference revision
     * @param localSourceFingerprint what the results were computed from
     */
    record NotaFinalProbeContext(
            Quadrimestre quadrimestre,
            Map<String, Map<YearMonth, List<TeamResult>>> monthlyResults,
            ValidatedReference reference,
            SiapsReferenceManifest manifest,
            String localSourceFingerprint)
            implements ProbeContext {

        public NotaFinalProbeContext {
            monthlyResults = ProbeContexts.immutable(monthlyResults);
            ProbeContexts.requireNotaFinal(quadrimestre, monthlyResults, reference, manifest, localSourceFingerprint);
        }

        @Override
        public String packId() {
            return ComponentIII.ID;
        }

        @Override
        public String ruleVersion() {
            return ComponentIII.RULE_VERSION;
        }
    }
}
