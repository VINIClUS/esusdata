package esusdata.indicator.pack.c1;

import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ReleaseGates;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * C1 behind the rule SPI (ADR 0030) — an adapter over {@link C1Rule}, which keeps computing
 * exactly as before: the same frozen capability, the same canonical v1 extract, the same evidence
 * rows. What it adds is the catalog description (the family is now the pack's real one,
 * {@code QUALIDADE_ESF_EAP}, §2.2) and the result per team (INE), the granularity of the ficha.
 */
public final class C1Pack implements IndicatorRule {

    /** The frozen capability C1 has always read (contracts/compatibility, VALIDATED). */
    public static final String CAPABILITY = "individual_encounter_modality";

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            C1Rule.INDICATOR_PACK,
            C1Rule.RULE_VERSION,
            "qualidade-esf-eap-2026-06",
            "QUALIDADE_ESF_EAP",
            "C1",
            "Mais acesso",
            ValueKind.PERCENTAGE,
            "percentual",
            C1Rule.DENOMINATOR_KIND,
            C1Rule.CALCULATION_POLICY_VERSION,
            List.of(CAPABILITY),
            List.of(),
            ReleaseGates.adapterOnly(),
            C1Rule.standingLimitations(),
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.engineeringDefault(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/"
                            + "equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c1-mais-acesso",
                    "docs/metodologia/c1-mais-acesso.md"),
            List.of());

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataRequirements requirements(YearMonth competencia) {
        return new DataRequirements(
                DataRequirements.V1,
                List.of(PartRequirement.of(
                        CAPABILITY,
                        competencia.atDay(1),
                        competencia.plusMonths(1).atDay(1))));
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        List<CanonicalEncounter> encounters = data.encounters();
        String period = context.referencePeriod();
        String cutoff = context.dataCutoff().toString();
        IndicatorResult municipal = C1Rule.compute(encounters, context.municipalityIbge(), period, cutoff);

        Map<String, List<CanonicalEncounter>> byTeam = new LinkedHashMap<>();
        for (CanonicalEncounter e : encounters) {
            byTeam.computeIfAbsent(e.ine() == null ? "" : e.ine(), k -> new ArrayList<>())
                    .add(e);
        }
        List<TeamResult> teams = new ArrayList<>(byTeam.size());
        for (Map.Entry<String, List<CanonicalEncounter>> team : byTeam.entrySet()) {
            List<CanonicalEncounter> members = team.getValue();
            String ine = team.getKey().isEmpty() ? null : team.getKey();
            teams.add(new TeamResult(
                    ine, firstCnes(members), C1Rule.compute(members, context.municipalityIbge(), period, cutoff)));
        }
        return new RuleOutcome(municipal, teams, evidence(encounters));
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Optional.of(C1Rule.classify(value));
    }

    /**
     * One row per encounter, as the run pipeline has written C1 evidence since V2; an encounter
     * outside the ficha's CBO list is {@code EXCLUDED} with {@link C1Rule#REASON_CBO_OUTSIDE_FICHA}.
     */
    static List<EvidenceItem> evidence(List<CanonicalEncounter> encounters) {
        List<EvidenceItem> items = new ArrayList<>(encounters.size());
        for (CanonicalEncounter e : encounters) {
            boolean inFicha = C1Rule.isFichaCbo(e.cbo());
            EvidenceDecision decision = inFicha ? byModality(e.modality()) : EvidenceDecision.EXCLUDED;
            items.add(new EvidenceItem(
                    EvidenceSubjectKind.EVENT,
                    null,
                    e.sourceRef(),
                    e.careDate(),
                    null,
                    decision,
                    inFicha ? null : C1Rule.REASON_CBO_OUTSIDE_FICHA,
                    null,
                    e.cnes(),
                    e.ine(),
                    e.cbo(),
                    e.modality().name()));
        }
        return items;
    }

    private static EvidenceDecision byModality(CanonicalModality modality) {
        return switch (modality) {
            case PROGRAMADO -> EvidenceDecision.IN_NUMERATOR;
            case ESPONTANEO -> EvidenceDecision.DENOMINATOR_ONLY;
            case UNMAPPED -> EvidenceDecision.EXCLUDED_UNMAPPED;
        };
    }

    private static String firstCnes(List<CanonicalEncounter> members) {
        for (CanonicalEncounter e : members) {
            if (e.cnes() != null) {
                return e.cnes();
            }
        }
        return null;
    }
}
