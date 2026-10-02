package esusdata.indicator.pack.c6;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ReleaseGates;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.indicator.model.Scores;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.c6.C6Cohort.Subject;
import esusdata.indicator.pack.c6.C6Practices.Practice;
import esusdata.indicator.pack.c6.C6Practices.Support;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * C6 — Cuidado da pessoa idosa (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c6-cuidado-pessoa-idosa.md}).
 *
 * <p>Média dos pontos (A, B, C e D, 25 cada) das pessoas de 60 anos ou mais vinculadas no último
 * dia da competência, sem ×100. As convenções provisórias das ambiguidades da ficha (AMB-C6-xx)
 * estão nas limitações permanentes do descritor; enquanto houver uma, ou um portão incompleto, o
 * resultado sai {@code BLOCKED} com as contagens.
 */
public final class C6Pack implements IndicatorRule {

    private static final String TWELVE_MONTHS = "12 meses";

    public static final String ID = "c6-cuidado-pessoa-idosa";
    public static final String RULE_VERSION = ID + "@0.1.0";

    /** The reason code of the visits practice of an eAP tipo 76 person while P07 is open. */
    static final String EAP_INFORMATIVE = "C_INFORMATIVA_EAP76_AMB_C6_01";

    static final String EAP_AMBIGUITY = "AMB-C6-01: a boa prática (C) «não será condicionante de pontuação para eAP, "
            + "tipo 76» (item 24 b, p. 2) admite três leituras (crédito integral, renormalização sobre 75 ou só não "
            + "exigir); resultado sem valor até a P07 (MET-23). A, B e D seguem nos componentes; C é informativa.";

    private static final List<String> STANDING_LIMITATIONS = List.of(
            "Dados fora do PEC local: doses só na RNDS/RIA (lacuna L4) não aparecem e a falta de integração não é "
                    + "ausência de vacinação; registros de profissionais de outros municípios («no país», item 4.4) "
                    + "e o «Óbito no CadSUS» (item 15) não estão no extrato.",
            "Vínculo: a ficha remete à Portaria SAPS/MS nº 161/2024 (AMB-C6-04); o vínculo é a versão completa "
                    + "do cadastro individual local vigente no corte (lida nos últimos 24 meses), estimativa local "
                    + "que não equivale ao vínculo do Siaps (lacuna L8). Saída 136 (mudança de território) e 135 "
                    + "(óbito) são códigos LEDI do dicionário do DW, não da ficha.",
            "Tipo de equipe ausente no DW (lacuna L1): a exceção eAP tipo 76 da prática C (AMB-C6-01) só é "
                    + "reconhecida quando o extrato traz o tipo; sem ele, C é exigida de todas as equipes e a "
                    + "validação de equipes (Portaria GM/MS nº 3.493/2024, SCNES) não é feita.",
            "Não verificados no PEC local: CNS profissional identificado, estabelecimento de APS, habilitação de "
                    + "CBO na tabela SIGTAP (item 24 f), identificação conforme CadSUS (item 24 a) e o corte do "
                    + "20º dia útil (item 11).",
            "AMB-C6-02: janela de 12 meses civis terminando no último dia da competência, nunca 365 dias. "
                    + "AMB-C6-05: idade completa no último dia da competência, aniversário CLAMP_TO_MONTH_END "
                    + "(29/02 + 1 ano = 28/02).",
            "AMB-C6-06: consulta (A) só pelo MIAI com CBO do Quadro 02, presencial ou remota, sem códigos "
                    + "SIGTAP de consulta e sem exigir o problema/condição avaliada do item 24 e.",
            "AMB-C6-07/AMB-C6-11: peso e altura (B) na mesma data civil, em qualquer combinação de MIAI, MIP, "
                    + "MIAC, MIVDT e SIGTAP 0101040083/0101040075, ou 0101040024 sozinho, por CBO do Quadro 03; "
                    + "a exclusão do procedimento consolidado fica com a consulta da capacidade.",
            "AMB-C6-03: visitas (C) de ACS/TACS com motivo preenchido e a primeira e a última distantes "
                    + "≥ 30 dias corridos; desfecho não filtrado.",
            "AMB-C6-08/09/10: influenza (D) 33 ou 77 com data de aplicação na janela, transcrição incluída, "
                    + "qualquer profissional; a mesma vacina na mesma data é uma dose e doses distintas não somam.");

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            RULE_VERSION,
            "qualidade-esf-eap-2026-06",
            "QUALIDADE_ESF_EAP",
            "C6",
            "Cuidado da pessoa idosa",
            ValueKind.SCORE,
            "percentual",
            "PESSOAS_IDOSAS_VINCULADAS",
            "c6-exact-score@1",
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.HOME_VISIT,
                    Capabilities.MEASUREMENT_RECORD,
                    Capabilities.IMMUNIZATION_HISTORY),
            List.of(
                    ComponentSpec.practice(
                            "A",
                            "Ter registro de pelo menos 01 (uma) consulta presencial ou remota por profissional médica(o) ou enfermeira(o) realizada nos últimos 12 meses.",
                            25,
                            TWELVE_MONTHS),
                    ComponentSpec.practice(
                            "B",
                            "Ter realizado pelo menos 01 (um) registro simultâneo (no mesmo dia) de peso e altura para avaliação antropométrica nos últimos 12 meses.",
                            25,
                            TWELVE_MONTHS),
                    ComponentSpec.practice(
                            "C",
                            "Ter pelo menos 02 (duas) visitas domiciliares realizadas por ACS/TACS, com intervalo mínimo de 30 (trinta) dias entre as visitas, realizadas nos últimos 12 meses.",
                            25,
                            TWELVE_MONTHS),
                    ComponentSpec.practice(
                            "D",
                            "Ter registro de 01 (uma) dose da vacina contra influenza, nos últimos 12 meses.",
                            25,
                            TWELVE_MONTHS)),
            ReleaseGates.noneComplete(),
            STANDING_LIMITATIONS,
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.engineeringDefault(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c6-cuidado-da-pessoa-idosa",
                    "docs/metodologia/c6-cuidado-pessoa-idosa.md"),
            List.of());

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /**
     * Every part reads the 12 civil months of the practices (AMB-C6-02), except the registration
     * versions (24 months, to find the version in force at the cutoff), for people born up to 60
     * years before the last day of the competência.
     */
    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow period = DateWindow.lastCivilMonths(competencia, C6Codes.WINDOW_MONTHS);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(130),
                competencia.plusMonths(1).atDay(1).minusYears(C6Codes.MINIMUM_AGE_YEARS));
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
                Capabilities.IMMUNIZATION_HISTORY, period, births, codes(Capabilities.IMMUNIZATION_HISTORY)));
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return RuleOutcomes.gate(DESCRIPTOR, compute(data, context));
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Bands.QUALIDADE_C2_C7.classify(value);
    }

    /** The rule before the release gates: exact value, teams and evidence. */
    static RuleOutcome compute(CanonicalDataset data, EvaluationContext context) {
        C6Scope.requireMunicipality(data, context.municipalityIbge());
        LocalDate lastDay = context.competencia().atEndOfMonth();
        DateWindow window = practiceWindow(context);
        List<Subject> subjects = C6Cohort.resolve(data, lastDay, context.dataCutoff());
        C6Practices practices = C6Practices.index(data, window);
        Set<String> eapTeams = eapTeams(data.teams(), context.dataCutoff());
        List<Assessment> assessed = new ArrayList<>();
        for (Subject s : subjects) {
            if (s.eligible()) {
                assessed.add(new Assessment(s, practices.assess(s.personKey()), eapTeams.contains(s.ine())));
            }
        }
        IndicatorResult municipal = result(assessed, context);
        return new RuleOutcome(municipal, teams(assessed, context), C6Evidence.of(subjects, assessed, lastDay));
    }

    /** One eligible person with the events behind each practice. */
    record Assessment(Subject subject, Map<Practice, List<Support>> practices, boolean eap) {
        boolean met(Practice practice) {
            return !practices.get(practice).isEmpty();
        }

        BigInteger points() {
            List<ComponentSpec> satisfied = new ArrayList<>();
            for (Practice p : Practice.values()) {
                if (met(p)) {
                    satisfied.add(spec(p));
                }
            }
            return Scores.points(satisfied);
        }
    }

    static ComponentSpec spec(Practice practice) {
        for (ComponentSpec spec : DESCRIPTOR.components()) {
            if (spec.code().equals(practice.name())) {
                return spec;
            }
        }
        throw new IllegalStateException("no component " + practice);
    }

    /** The 12 civil months ending with the competência (AMB-C6-02), never past the cutoff. */
    private static DateWindow practiceWindow(EvaluationContext context) {
        DateWindow months = DateWindow.lastCivilMonths(context.competencia(), C6Codes.WINDOW_MONTHS);
        LocalDate afterCutoff = context.dataCutoff().plusDays(1);
        if (afterCutoff.isBefore(months.endExclusive())) {
            return new DateWindow(months.start(), afterCutoff.isBefore(months.start()) ? months.start() : afterCutoff);
        }
        return months;
    }

    private static IndicatorResult result(List<Assessment> group, EvaluationContext context) {
        BigInteger subjects = BigInteger.valueOf(group.size());
        BigInteger total = BigInteger.ZERO;
        List<ResultComponent> components = new ArrayList<>();
        for (Practice p : Practice.values()) {
            long met = group.stream().filter(a -> a.met(p)).count();
            components.add(ResultComponent.of(spec(p), BigInteger.valueOf(met), subjects));
        }
        boolean ambiguous = false;
        long withoutTeam = 0;
        for (Assessment a : group) {
            total = total.add(a.points());
            ambiguous |= a.eap();
            withoutTeam += a.subject().ine() == null ? 1 : 0;
        }
        List<String> limitations = new ArrayList<>(STANDING_LIMITATIONS);
        if (withoutTeam > 0) {
            limitations.add(withoutTeam + " pessoa(s) elegível(is) sem INE no cadastro vigente, contadas no município "
                    + "e na equipe sem INE.");
        }
        if (ambiguous) {
            limitations.add(EAP_AMBIGUITY);
            return build(IndicatorStatus.RULE_AMBIGUITY, null, null, subjects, components, limitations, context);
        }
        Optional<ExactRatio> value = Scores.meanPoints(total, subjects);
        IndicatorStatus status = value.isPresent() ? IndicatorStatus.COMPUTED : IndicatorStatus.NO_DENOMINATOR;
        return build(status, value.orElse(null), total, subjects, components, limitations, context);
    }

    private static IndicatorResult build(
            IndicatorStatus status,
            ExactRatio value,
            BigInteger numerator,
            BigInteger denominator,
            List<ResultComponent> components,
            List<String> limitations,
            EvaluationContext context) {
        return new IndicatorResult(
                status,
                value == null ? null : value.toScaledBigDecimal(4).toPlainString(),
                numerator,
                denominator,
                DESCRIPTOR.denominatorKind(),
                value == null ? null : Bands.QUALIDADE_C2_C7.classify(value).orElse(null),
                context.referencePeriod(),
                RULE_VERSION,
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations,
                DESCRIPTOR.calculationPolicyVersion(),
                ValueKind.SCORE,
                value,
                components,
                true);
    }

    /** One result per INE of the link in force, in INE order; people without INE last. */
    private static List<TeamResult> teams(List<Assessment> assessed, EvaluationContext context) {
        Map<String, List<Assessment>> byTeam = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));
        for (Assessment a : assessed) {
            byTeam.computeIfAbsent(a.subject().ine(), k -> new ArrayList<>()).add(a);
        }
        List<TeamResult> teams = new ArrayList<>(byTeam.size());
        byTeam.forEach((ine, members) -> teams.add(new TeamResult(ine, cnes(members), result(members, context))));
        return teams;
    }

    private static String cnes(List<Assessment> members) {
        return members.stream()
                .map(a -> a.subject().cnes())
                .filter(c -> c != null)
                .sorted()
                .findFirst()
                .orElse(null);
    }

    /**
     * INEs whose latest team-type observation on or before the cutoff is eAP tipo 76 (item 24 b).
     * The DW has no team type (lacuna L1), so today this is empty unless an extract brings one.
     */
    private static Set<String> eapTeams(List<CanonicalTeam> teams, LocalDate cutoff) {
        Map<String, CanonicalTeam> latest = new HashMap<>();
        Function<CanonicalTeam, LocalDate> observed =
                t -> LocalDate.parse(t.observedAt().substring(0, 10));
        for (CanonicalTeam t : teams) {
            if (t.ine() == null || t.observedAt() == null || observed.apply(t).isAfter(cutoff)) {
                continue;
            }
            latest.merge(t.ine(), t, (a, b) -> observed.apply(b).isAfter(observed.apply(a)) ? b : a);
        }
        Set<String> eap = new HashSet<>();
        latest.forEach((ine, t) -> {
            if (C6Codes.TEAM_TYPE_EAP.equals(t.teamTypeCode())) {
                eap.add(ine);
            }
        });
        return eap;
    }

    /** The code lists each capability binds (C6Codes, transcribed from the ficha). */
    private static SortedMap<String, List<String>> codes(String capability) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        if (Capabilities.PROCEDURE_PERFORMED.equals(capability)) {
            lists.put(Capabilities.PROCEDURE_CODES, C6Codes.PROCEDURE_CODES);
        } else if (Capabilities.IMMUNIZATION_HISTORY.equals(capability)) {
            lists.put(Capabilities.IMMUNOBIOLOGICAL_CODES, C6Codes.INFLUENZA_CODES);
        }
        return lists;
    }
}
