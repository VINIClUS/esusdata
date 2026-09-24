package esusdata.indicator.pack.c1;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * C1 — Mais acesso (Tech Spec §2.4, verbatim formula): {@code 100 × programados /
 * (programados + espontâneos)}. Counts encounters, not people. Monthly apportionment.
 *
 * <p>Gate status (§4.4 Portões A–E), recorded honestly rather than collapsed into "works":
 * Portão C (adaptador) is what this class and its adapter query actually prove —
 * {@code VALIDATED_AGAINST_PEC} for PEC 5.4.37/PostgreSQL 9.6.13. Portões A/B (Q01 ficha — the
 * exact CBO list and ficha fields) were not retrieved in this session and remain
 * {@code BLOCKED}; this rule pack therefore ships with an explicit {@code cbo_policy=ALL_CBO}
 * limitation rather than silently filtering by an assumed CBO set. Portão D (reconciliation
 * against Siaps/SISAB) has no reference data available in this environment and is
 * {@code NOT_IMPLEMENTED}. Portão E requires a human review and is out of scope for code.
 */
public final class C1Rule {

    /** Indicator pack identity — distinct from {@link #RULE_VERSION}, which versions the rule. */
    public static final String INDICATOR_PACK = "c1-mais-acesso";

    public static final String RULE_VERSION = "c1-mais-acesso@0.1.0";
    public static final String CALCULATION_POLICY_VERSION = "c1-exact-ratio@1";
    public static final String DENOMINATOR_KIND = "PROGRAMADOS_MAIS_ESPONTANEOS";

    private static final List<String> STANDING_LIMITATIONS = List.of(
            "cbo_policy=ALL_CBO — Q01 (ficha metodológica oficial C1) não foi recuperada nesta sessão; "
                    + "nenhum filtro de CBO foi aplicado (Portão A/B BLOCKED).",
            "Nenhuma reconciliação com Siaps/SISAB foi realizada (Portão D NOT_IMPLEMENTED).");

    private C1Rule() {}

    /**
     * Release gates are deliberately supplied by the release workflow rather than inferred from
     * the presence of this class or its compatibility entry. A result must not look published
     * while any one of the five gates is incomplete.
     */
    public record ReleaseGates(
            boolean sourceAndValidity,
            boolean calculationModel,
            boolean adapter,
            boolean reconciliation,
            boolean pilotAndOperations) {
        public static ReleaseGates allComplete() {
            return new ReleaseGates(true, true, true, true, true);
        }

        public static ReleaseGates knownIncomplete() {
            return new ReleaseGates(false, false, true, false, false);
        }

        public boolean isComplete() {
            return sourceAndValidity
                    && calculationModel
                    && adapter
                    && reconciliation
                    && pilotAndOperations
                    && STANDING_LIMITATIONS.isEmpty();
        }

        public List<String> incompleteReasons() {
            List<String> reasons = new ArrayList<>();
            if (!sourceAndValidity) {
                reasons.add("Portão A (fonte e vigência) incompleto");
            }
            if (!calculationModel) {
                reasons.add("Portão B (modelo de cálculo) incompleto");
            }
            if (!adapter) {
                reasons.add("Portão C (adaptador) incompleto");
            }
            if (!reconciliation) {
                reasons.add("Portão D (reconciliação) incompleto");
            }
            if (!pilotAndOperations) {
                reasons.add("Portão E (piloto e operação) incompleto");
            }
            return List.copyOf(reasons);
        }
    }

    /**
     * Computes C1 for one competency from already-canonical encounters. Encounters whose modality
     * is {@link CanonicalModality#UNMAPPED} are excluded from both numerator and denominator and
     * counted, never silently folded into an arm (§2.4: "Filtros de modalidade devem ser mutuamente
     * exclusivos após a normalização").
     */
    public static IndicatorResult compute(
            List<CanonicalEncounter> encounters, String municipalityIbge, String referencePeriod, String dataCutoff) {
        return compute(encounters, municipalityIbge, referencePeriod, dataCutoff, ReleaseGates.knownIncomplete());
    }

    /**
     * Computes a publishable C1 result only when the release workflow has completed every gate.
     * Counts are still returned exactly as diagnostic evidence, but the value and classification
     * stay unavailable while a gate is incomplete.
     */
    public static IndicatorResult compute(
            List<CanonicalEncounter> encounters,
            String municipalityIbge,
            String referencePeriod,
            String dataCutoff,
            ReleaseGates releaseGates) {
        validateRequestedScope(encounters, municipalityIbge, referencePeriod);
        Computation computation = count(encounters);
        if (!releaseGates.isComplete()) {
            List<String> limitations = new ArrayList<>(computation.limitations());
            limitations.addAll(releaseGates.incompleteReasons());
            return new IndicatorResult(
                    IndicatorResult.IndicatorStatus.BLOCKED,
                    null,
                    computation.numerator(),
                    computation.denominator(),
                    DENOMINATOR_KIND,
                    null,
                    referencePeriod,
                    RULE_VERSION,
                    dataCutoff,
                    municipalityIbge,
                    limitations,
                    CALCULATION_POLICY_VERSION);
        }
        return toResult(computation, municipalityIbge, referencePeriod, dataCutoff);
    }

    /**
     * Exact calculation over evidence that has already been validated by the acquisition layer.
     * This method is intentionally named so callers cannot mistake a reproducibility calculation
     * for a release-approved indicator.
     */
    public static IndicatorResult computeEvidenceOnly(
            List<CanonicalEncounter> encounters, String municipalityIbge, String referencePeriod, String dataCutoff) {
        validateRequestedScope(encounters, municipalityIbge, referencePeriod);
        return toResult(count(encounters), municipalityIbge, referencePeriod, dataCutoff);
    }

    private static void validateRequestedScope(
            List<CanonicalEncounter> encounters, String municipalityIbge, String referencePeriod) {
        if (encounters == null) {
            throw new IllegalArgumentException("encounters are required");
        }
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalArgumentException("municipality must be a 7-digit IBGE code: " + municipalityIbge);
        }
        if (referencePeriod == null || !referencePeriod.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("referencePeriod must use YYYY-MM: " + referencePeriod);
        }
        YearMonth requestedMonth;
        try {
            requestedMonth = YearMonth.parse(referencePeriod);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "referencePeriod must be a valid YYYY-MM competency: " + referencePeriod, e);
        }

        for (CanonicalEncounter encounter : encounters) {
            validateEncounterScope(encounter, municipalityIbge, requestedMonth, referencePeriod);
        }
    }

    private static void validateEncounterScope(
            CanonicalEncounter encounter, String municipalityIbge, YearMonth requestedMonth, String referencePeriod) {
        if (encounter == null) {
            throw new IllegalArgumentException("encounters cannot contain null records");
        }
        if (!municipalityIbge.equals(encounter.municipalityIbge())) {
            throw new IllegalArgumentException(
                    "encounter municipality does not match requested municipality: " + encounter.municipalityIbge());
        }
        LocalDate careDate;
        try {
            careDate = LocalDate.parse(encounter.careDate());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("encounter careDate is invalid: " + encounter.careDate(), e);
        }
        if (!requestedMonth.equals(YearMonth.from(careDate))) {
            throw new IllegalArgumentException("encounter careDate does not match referencePeriod " + referencePeriod
                    + ": " + encounter.careDate());
        }
    }

    private static Computation count(List<CanonicalEncounter> encounters) {
        BigInteger programados = BigInteger.ZERO;
        BigInteger espontaneos = BigInteger.ZERO;
        BigInteger unmapped = BigInteger.ZERO;

        for (CanonicalEncounter e : encounters) {
            switch (e.modality()) {
                case PROGRAMADO -> programados = programados.add(BigInteger.ONE);
                case ESPONTANEO -> espontaneos = espontaneos.add(BigInteger.ONE);
                case UNMAPPED -> unmapped = unmapped.add(BigInteger.ONE);
            }
        }

        BigInteger denominator = programados.add(espontaneos);

        List<String> limitations = new ArrayList<>(STANDING_LIMITATIONS);
        if (unmapped.signum() > 0) {
            limitations.add(unmapped + " encontro(s) com tipo de atendimento fora do mapeamento "
                    + "congelado (ids 8/9/10/11 de tb_dim_tipo_atendimento) foram excluídos do cálculo.");
        }

        return new Computation(programados, denominator, limitations);
    }

    private static IndicatorResult toResult(
            Computation computation, String municipalityIbge, String referencePeriod, String dataCutoff) {
        BigInteger numerator = computation.numerator();
        BigInteger denominator = computation.denominator();

        if (denominator.signum() == 0) {
            return new IndicatorResult(
                    IndicatorResult.IndicatorStatus.NO_DENOMINATOR,
                    null,
                    numerator,
                    denominator,
                    DENOMINATOR_KIND,
                    null,
                    referencePeriod,
                    RULE_VERSION,
                    dataCutoff,
                    municipalityIbge,
                    computation.limitations(),
                    CALCULATION_POLICY_VERSION);
        }

        ExactRatio ratio = new ExactRatio(numerator, denominator).asPercentage();
        Classification classification = classify(ratio);
        String valueText = ratio.toScaledBigDecimal(4).toPlainString();

        return new IndicatorResult(
                IndicatorResult.IndicatorStatus.COMPUTED,
                valueText,
                numerator,
                denominator,
                DENOMINATOR_KIND,
                classification,
                referencePeriod,
                RULE_VERSION,
                dataCutoff,
                municipalityIbge,
                computation.limitations(),
                CALCULATION_POLICY_VERSION);
    }

    private record Computation(BigInteger numerator, BigInteger denominator, List<String> limitations) {}

    /**
     * Classification bands, verbatim from §2.4:
     * {@code 50 < x ≤ 70 Ótimo · 30 < x ≤ 50 Bom · 10 < x ≤ 30 Suficiente · x ≤ 10 or x > 70 Regular}.
     * Deliberately non-monotonic (MET-18) — decided entirely by integer cross-multiplication,
     * never by converting {@code ratio} to a decimal first.
     */
    public static Classification classify(ExactRatio percentageRatio) {
        if (percentageRatio.compareToFraction(70, 1) > 0) {
            return Classification.REGULAR;
        }
        if (percentageRatio.compareToFraction(50, 1) > 0) {
            return Classification.OTIMO;
        }
        if (percentageRatio.compareToFraction(30, 1) > 0) {
            return Classification.BOM;
        }
        if (percentageRatio.compareToFraction(10, 1) > 0) {
            return Classification.SUFICIENTE;
        }
        return Classification.REGULAR;
    }

    /**
     * Quadrimestral consolidation (§2.4 @800-802, MET-33): mean of the monitored monthly results,
     * band applied only after averaging — never round intermediate monthly values.
     */
    public static Classification classifyQuadrimestral(ExactRatio... monthlyRatios) {
        ExactRatio mean = ExactRatio.meanOfExactRatios(monthlyRatios);
        return classify(mean);
    }
}
