package esusdata.indicator.pack.c1;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.ReleaseGates;
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
 * {@code VALIDATED_AGAINST_PEC} for PEC 5.4.37/PostgreSQL 9.6.13. The ficha (Q01,
 * {@code docs/metodologia/c1-mais-acesso.md}) is now applied for the CBO filter: item 24-c lists
 * seven six-digit occupations, valid for numerator and denominator alike; encounters with a
 * missing CBO or one outside the list are excluded from both and counted
 * ({@code cbo_policy=FICHA_24C}). The ficha has a single list with no transition rule, so 225125
 * and 225250 (footnote additions) are valid in every competência (decision C1-D1). Team type, professional CNS and the other ficha fields are still not checked.
 * Portão D (reconciliation against Siaps/SISAB) and Portão E are gate states kept by the release
 * workflow, not limitations of the rule (decision C1-D4).
 */
public final class C1Rule {

    /** Indicator pack identity — distinct from {@link #RULE_VERSION}, which versions the rule. */
    public static final String INDICATOR_PACK = "c1-mais-acesso";

    public static final String RULE_VERSION = "c1-mais-acesso@0.3.0";
    public static final String CALCULATION_POLICY_VERSION = "c1-exact-ratio@1";
    public static final String DENOMINATOR_KIND = "PROGRAMADOS_MAIS_ESPONTANEOS";

    /** Reason code of the evidence row of an encounter left out by the CBO filter. */
    public static final String REASON_CBO_OUTSIDE_FICHA = "EXCLUIDO_CBO_FORA_DA_FICHA";

    /**
     * Item 24-c / Quadro 01 of the ficha: exact six-digit occupations (2251-42, 2251-70, 2251-30,
     * 2251-25, 2252-50, 2235-65, 2235-05), not the four-digit families C7 uses.
     */
    private static final CboGroups FICHA_CBO =
            CboGroups.of("225142", "225170", "225130", "225125", "225250", "223565", "223505");

    private static final List<String> STANDING_LIMITATIONS = List.of(
            "C1-LIM-01: Entram só os atendimentos dos sete CBO do item 24-c da ficha (225142, 225170, 225130, "
                    + "225125, 225250, 223565, 223505), em toda competência; CBO ausente ou fora da lista é "
                    + "excluído e contado.",
            "C1-LIM-03: Sem tipo de equipe comprovado, a validação eSF 70 / eAP 76 (item 24-b) não é feita.",
            "C1-LIM-06: O SIAPS extrai no 20º dia útil e só conta o enviado até o 10º dia do mês seguinte; "
                    + "o valor local pode incluir registros enviados depois.",
            "C1-LIM-07: A conformidade da identificação da pessoa com o CadSUS não é conferida.",
            "C1-LIM-08: O CNS profissional é presumido presente em registro do PEC; não é conferido.",
            "C1-LIM-09: Atendimento com dois participantes conta uma vez, pelo participante 1; o CNES não é "
                    + "filtrado porque a ficha não lista CNES.",
            "C1-LIM-11: A lotação do profissional na equipe (SCNES) não é conferida; vale o INE registrado no "
                    + "atendimento.");

    private C1Rule() {}

    /**
     * Limitations every C1 result carries until Q01 is applied and reconciled. Release gates
     * are supplied by the release workflow ({@link ReleaseGates}); a result also stays blocked
     * while any of these stands, even with every gate complete.
     */
    public static List<String> standingLimitations() {
        return STANDING_LIMITATIONS;
    }

    /**
     * Whether the encounter's CBO is one of the seven occupations of the ficha (item 24-c). A
     * missing or blank CBO is outside the list.
     */
    public static boolean isFichaCbo(String cbo) {
        return FICHA_CBO.matches(cbo);
    }

    /**
     * Computes C1 for one competency from already-canonical encounters. Encounters whose CBO is
     * missing or outside the ficha list, and those whose modality is
     * {@link CanonicalModality#UNMAPPED}, are excluded from both numerator and denominator and
     * counted, never silently folded into an arm (§2.4: "Filtros de modalidade devem ser mutuamente
     * exclusivos após a normalização").
     */
    public static IndicatorResult compute(
            List<CanonicalEncounter> encounters, String municipalityIbge, String referencePeriod, String dataCutoff) {
        return compute(encounters, municipalityIbge, referencePeriod, dataCutoff, ReleaseGates.adapterOnly());
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
        if (!releaseGates.isComplete() || !STANDING_LIMITATIONS.isEmpty()) {
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
        BigInteger outsideCbo = BigInteger.ZERO;

        for (CanonicalEncounter e : encounters) {
            if (!isFichaCbo(e.cbo())) {
                outsideCbo = outsideCbo.add(BigInteger.ONE);
                continue;
            }
            switch (e.modality()) {
                case PROGRAMADO -> programados = programados.add(BigInteger.ONE);
                case ESPONTANEO -> espontaneos = espontaneos.add(BigInteger.ONE);
                case UNMAPPED -> unmapped = unmapped.add(BigInteger.ONE);
            }
        }

        BigInteger denominator = programados.add(espontaneos);

        List<String> limitations = new ArrayList<>(STANDING_LIMITATIONS);
        if (outsideCbo.signum() > 0) {
            limitations.add(
                    "C1-LIM-04: " + outsideCbo + " atendimento(s) com CBO ausente ou fora dos sete CBO da ficha "
                            + "foram excluídos do numerador e do denominador.");
        }
        if (unmapped.signum() > 0) {
            limitations.add("C1-LIM-05: " + unmapped + " atendimento(s) com tipo de atendimento fora dos seis tipos da "
                    + "ficha foram excluídos do numerador e do denominador.");
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
