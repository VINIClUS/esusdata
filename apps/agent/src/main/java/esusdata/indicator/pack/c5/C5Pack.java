package esusdata.indicator.pack.c5;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ReleaseGates;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.indicator.model.ValueKind;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * C5 — Cuidado da pessoa com hipertensão (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c5-cuidado-hipertensao.md}).
 *
 * <p>Esqueleto da fundação (ADR 0030): o descritor, as práticas com os pesos da ficha, as faixas e
 * as capacidades lidas já estão aqui; a regra ({@link #evaluate}) e as listas de códigos ficam com
 * a sessão do pacote. Enquanto isso o pacote devolve {@code BLOCKED} sem contagens, nunca zero.
 */
public final class C5Pack implements IndicatorRule {

    private static final String SIX_MONTHS = "6 meses";
    private static final String TWELVE_MONTHS = "12 meses";

    public static final String ID = "c5-cuidado-hipertensao";
    public static final String RULE_VERSION = ID + "@0.1.0";

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            RULE_VERSION,
            "qualidade-esf-eap-2026-06",
            "QUALIDADE_ESF_EAP",
            "C5",
            "Cuidado da pessoa com hipertensão",
            ValueKind.SCORE,
            "percentual",
            "PESSOAS_COM_HIPERTENSAO_VINCULADAS",
            "c5-exact-score@1",
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.HOME_VISIT,
                    Capabilities.MEASUREMENT_RECORD,
                    Capabilities.CONDITION_LIST),
            List.of(
                    ComponentSpec.practice(
                            "A",
                            "Ter pelo menos 01 (uma) consulta presencial ou remota realizadas por médica(o) ou enfermeira(o), nos últimos 06 (seis) meses.",
                            25,
                            SIX_MONTHS),
                    ComponentSpec.practice(
                            "B",
                            "Ter pelo menos 01 (um) registro de aferição de pressão arterial realizado nos últimos 06 (seis) meses.",
                            25,
                            SIX_MONTHS),
                    ComponentSpec.practice(
                            "C",
                            "Ter pelo menos 01 (um) registro simultâneos de peso e altura realizado nos últimos 12 (doze) meses.",
                            25,
                            TWELVE_MONTHS),
                    ComponentSpec.practice(
                            "D",
                            "Ter pelo menos 02 (duas) visitas domiciliares realizadas por ACS/TACS, com intervalo mínimo de 30 (trinta) dias, nos últimos 12 (doze) meses.",
                            25,
                            TWELVE_MONTHS)),
            ReleaseGates.noneComplete(),
            List.of("Regra em implementação (ADR 0030): o pacote ainda não calcula."),
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.engineeringDefault(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c5-cuidado-da-pessoa-com-hipertensao",
                    "docs/metodologia/c5-cuidado-hipertensao.md"),
            List.of());

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow period = DateWindow.lastCivilMonths(competencia, 12);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(130), competencia.plusMonths(1).atDay(1));
        List<PartRequirement> parts = new ArrayList<>();
        parts.add(PartRequirement.personScoped(Capabilities.CITIZEN, period, births, codes(Capabilities.CITIZEN)));
        parts.add(PartRequirement.personScoped(
                Capabilities.INDIVIDUAL_REGISTRATION,
                DateWindow.lastCivilMonths(competencia, 24),
                births,
                codes(Capabilities.INDIVIDUAL_REGISTRATION)));
        parts.add(PartRequirement.personScoped(
                Capabilities.CARE_ENCOUNTER, period, births, codes(Capabilities.CARE_ENCOUNTER)));
        parts.add(PartRequirement.personScoped(
                Capabilities.PROCEDURE_PERFORMED, period, births, codes(Capabilities.PROCEDURE_PERFORMED)));
        parts.add(
                PartRequirement.personScoped(Capabilities.HOME_VISIT, period, births, codes(Capabilities.HOME_VISIT)));
        parts.add(PartRequirement.personScoped(
                Capabilities.MEASUREMENT_RECORD, period, births, codes(Capabilities.MEASUREMENT_RECORD)));
        parts.add(PartRequirement.personScoped(
                Capabilities.CONDITION_LIST,
                DateWindow.lastCivilMonths(competencia, 120),
                births,
                codes(Capabilities.CONDITION_LIST)));
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return RuleOutcomes.pending(DESCRIPTOR, context, "Regra em implementação (ADR 0030).");
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Bands.QUALIDADE_C2_C7.classify(value);
    }

    /** The code lists each capability binds; empty until the pack transcribes them from the ficha. */
    private static SortedMap<String, List<String>> codes(String capability) {
        return switch (capability) {
            case Capabilities.PROCEDURE_PERFORMED -> codeLists(Capabilities.PROCEDURE_CODES);
            case Capabilities.CONDITION_LIST -> codeLists(Capabilities.CIAP_CODES, Capabilities.CID_CODES);
            default -> new TreeMap<>();
        };
    }

    private static SortedMap<String, List<String>> codeLists(String... names) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        for (String name : names) {
            lists.put(name, List.of());
        }
        return lists;
    }
}
