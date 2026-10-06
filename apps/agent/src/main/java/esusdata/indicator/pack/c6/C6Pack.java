package esusdata.indicator.pack.c6;

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
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Limitation;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.Scores;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.TeamScope;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.PackSupport;
import esusdata.indicator.pack.c6.C6Cohort.Subject;
import esusdata.indicator.pack.c6.C6Practices.Practice;
import esusdata.indicator.pack.c6.C6Practices.Support;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * C6 — Cuidado da pessoa idosa (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c6-cuidado-pessoa-idosa.md}).
 *
 * <p>Média dos pontos (A, B, C e D, 25 cada) das pessoas de 60 anos ou mais vinculadas no último
 * dia da competência, sem ×100. As convenções decididas das ambiguidades da ficha (AMB-C6-xx)
 * viajam como limitações permanentes (C6-LIM-nn) do descritor; enquanto houver uma, ou um portão incompleto, o
 * resultado sai {@code BLOCKED} com as contagens.
 */
public final class C6Pack implements IndicatorRule {

    private static final String TWELVE_MONTHS = "12 meses";

    /** The ficha has no upper age; the birth bind only needs a finite start (no one is 130). */
    private static final int OLDEST_AGE_READ = 130;

    public static final String ID = "c6-cuidado-pessoa-idosa";
    public static final String RULE_VERSION = ID + "@0.3.0";

    /** Reason code of practice C for a person of an eAP 76 team without the visits: credited in full (C6-D1). */
    static final String C_REASON_CREDITED_EAP = TeamScope.REASON_CREDITED_EAP76;

    /**
     * C6-LIM-14 (P07, C6-D1): practice C is credited in full to the people of eAP 76 teams, observed
     * or not (item 24 b).
     */
    static final String EAP_CREDIT = "C6-LIM-14/contagem: C creditada integralmente (%d pontos) para %d pessoa(s) de"
            + " equipes eAP 76, conforme o item 24 b; observada em %d.";

    /** C6-LIM-15 (C6-D2): the people left out because their team is not a considered one. */
    static final String TEAM_EXCLUSIONS = "C6-LIM-15/contagem: %d pessoa(s) vinculada(s) a equipe fora da regra de tipo"
            + " (70 ou 76 vigente no fim da competência) ficaram fora: %d de equipe sem tipo, %d de tipo conflitante e"
            + " %d de outro tipo.";

    private static final List<Limitation> STANDING_LIMITATIONS = List.of(
            Limitation.outOfReach(
                    "C6-LIM-01",
                    "Doses só no RIA/RNDS, registros de outros municípios e o óbito no CadSUS não estão no PEC "
                            + "local; D pode sair subestimada."),
            Limitation.outOfReach(
                    "C6-LIM-02",
                    "O vínculo é a versão do cadastro individual local vigente no corte (24 meses lidos), "
                            + "estimativa que não equivale ao vínculo do SIAPS."),
            Limitation.convention(
                    "C6-LIM-03",
                    "Só vale a versão completa do cadastro de maior data até o corte; cadastro simplificado e "
                            + "pessoa sem INE não vinculam (EXCLUIDO_SEM_VINCULO) e versões do mesmo dia divergentes excluem "
                            + "como conflito. Saída 136 (mudança de território) e 135 (óbito) são os códigos LEDI "
                            + "reconhecidos; outro código de saída exclui (EXCLUIDO_SAIDA_CADASTRO_NAO_MAPEADA), com "
                            + "contagem."),
            Limitation.convention(
                    "C6-LIM-04",
                    "Recusa de cadastro, ficha inativa, data de nascimento divergente e versões conflitantes "
                            + "excluem a pessoa com motivo próprio; a semântica de ficha inativa no DW não é publicada e a "
                            + "contagem por motivo é divulgada."),
            Limitation.outOfReach(
                    "C6-LIM-06",
                    "Habilitação de CBO na tabela SIGTAP e estabelecimento de APS não são conferidos; o CNS "
                            + "profissional é presumido presente em registro do PEC."),
            Limitation.outOfReach("C6-LIM-07", "A conformidade da identificação com o CadSUS não é conferida."),
            Limitation.outOfReach(
                    "C6-LIM-08",
                    "O SIAPS extrai no 20º dia útil e só vê o que chegou até lá; a leitura local pode incluir "
                            + "registros enviados depois."),
            Limitation.convention(
                    "C6-LIM-09",
                    "A janela é de 12 meses civis terminando no último dia da competência, nunca 365 dias. A "
                            + "idade é em anos completos no último dia da competência; quem completa 60 anos em qualquer "
                            + "dia do mês entra, e o aniversário de 29/02 cai em 01/03."),
            Limitation.convention(
                    "C6-LIM-10",
                    "A consulta (A) vale só pelo MIAI com CBO do Quadro 02, presencial ou remota, sem códigos "
                            + "SIGTAP de consulta e sem exigir problema ou condição avaliada."),
            Limitation.convention(
                    "C6-LIM-11",
                    "Peso e altura (B) contam na mesma data civil, em qualquer combinação de MIAI, MIP, MIAC, "
                            + "MIVDT e SIGTAP 0101040083/0101040075, ou 0101040024 sozinho (só de MIP ou MIAI), por CBO do "
                            + "Quadro 03; MIAC só com participante identificado e MIVDT só de ACS/TACS com motivo "
                            + "preenchido; 2239 vale por quatro dígitos."),
            Limitation.convention(
                    "C6-LIM-12",
                    "As visitas (C) são de ACS/TACS com motivo preenchido; a primeira e a última visita válida "
                            + "na janela distam 30 dias corridos ou mais, no mesmo dia não formam par, e o desfecho não é "
                            + "filtrado."),
            Limitation.convention(
                    "C6-LIM-13",
                    "Influenza (D): pelo menos uma dose de 33 ou 77 aplicada nos 12 meses da janela, "
                            + "transcrição com data de aplicação incluída, sem filtro de CBO; a mesma vacina na mesma data "
                            + "é uma dose, e doses distintas não somam nem anulam."),
            Limitation.convention(
                    "C6-LIM-14",
                    "C creditada integralmente (25 pontos) para as pessoas de equipes eAP 76, conforme o item 24 b; "
                            + "a contagem creditada e a observada de cada resultado estão nas limitações dele."),
            Limitation.convention(
                    "C6-LIM-15",
                    "Só equipes de tipo 70 ou 76 vigente no fim da competência entram; equipes de outro tipo, "
                            + "conflitantes ou sem tipo ficam fora, com motivo e contagem."));

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
            "c6-exact-score@2",
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.HOME_VISIT,
                    Capabilities.MEASUREMENT_RECORD,
                    Capabilities.IMMUNIZATION_HISTORY,
                    Capabilities.TEAM),
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
            STANDING_LIMITATIONS,
            MonthlyEligibility.ALL_MONTHS,
            BudgetHint.practicesPack(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c6-cuidado-da-pessoa-idosa",
                    "docs/metodologia/c6-cuidado-pessoa-idosa.md"),
            List.of());

    private static final Map<Practice, ComponentSpec> SPECS = specs();

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
                competencia.atDay(1).minusYears(OLDEST_AGE_READ),
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
        parts.add(PackSupport.teamPart(competencia));
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return compute(data, context);
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Bands.QUALIDADE_C2_C7.classify(value);
    }

    /** The rule before the release gates: exact value, teams and evidence. */
    static RuleOutcome compute(CanonicalDataset data, EvaluationContext context) {
        PackSupport.requireMunicipality(data, context.municipalityIbge());
        List<String> uncovered =
                PackSupport.uncoveredParts(
                                data,
                                new C6Pack().requirements(context.competencia()).parts())
                        .stream()
                        .map(PartRequirement::capability)
                        .toList();
        if (!uncovered.isEmpty()) {
            List<String> limitations = new ArrayList<>();
            limitations.add("Capacidade não lida ou lida com janela menor que a pedida: " + String.join(", ", uncovered)
                    + " — sem valor e sem contagens, nunca zero.");
            limitations.addAll(DESCRIPTOR.standingLimitationLines());
            return new RuleOutcome(
                    build(IndicatorStatus.UNSUPPORTED_SOURCE, null, null, null, List.of(), limitations, context),
                    List.of(),
                    List.of());
        }
        LocalDate lastDay = context.competencia().atEndOfMonth();
        TeamScope teamTypes = TeamScope.of(data.teams(), lastDay);
        List<Subject> subjects = C6Cohort.resolve(data, lastDay, context.dataCutoff(), teamTypes);
        C6Practices practices = C6Practices.index(data, practiceWindow(context));
        List<Assessment> assessed = new ArrayList<>();
        for (Subject s : subjects) {
            if (s.eligible()) {
                assessed.add(Assessment.of(s, practices.assess(s.personKey())));
            }
        }
        List<String> extra = new ArrayList<>();
        IndicatorResult municipal = result(assessed, extra, context);
        String left = teamExclusions(subjects);
        if (left != null) {
            municipal = municipal.withLimitation(left);
        }
        return new RuleOutcome(municipal, teams(assessed, extra, context), C6Evidence.of(subjects, assessed, lastDay));
    }

    /** The people the team-type rule left out, by reason: a disclosure for the municipal result (C6-LIM-15). */
    private static String teamExclusions(List<Subject> subjects) {
        long without = count(subjects, TeamScope.REASON_WITHOUT_TYPE);
        long conflict = count(subjects, TeamScope.REASON_CONFLICT);
        long other = count(subjects, TeamScope.REASON_OUT_OF_SCOPE);
        long all = without + conflict + other;
        return all == 0 ? null : TEAM_EXCLUSIONS.formatted(all, without, conflict, other);
    }

    private static long count(List<Subject> subjects, String reason) {
        return subjects.stream().filter(s -> reason.equals(s.reasonCode())).count();
    }

    /** One eligible person with the events behind each practice and the points they earn. */
    record Assessment(Subject subject, Map<Practice, List<Support>> practices, BigInteger points) {
        Assessment {
            practices = Collections.unmodifiableMap(new EnumMap<>(practices));
        }

        static Assessment of(Subject subject, Map<Practice, List<Support>> practices) {
            List<ComponentSpec> counted = new ArrayList<>();
            for (Practice p : Practice.values()) {
                if (!practices.get(p).isEmpty() || isCredited(subject, practices, p)) {
                    counted.add(spec(p));
                }
            }
            return new Assessment(subject, practices, Scores.points(counted));
        }

        private static boolean isCredited(Subject subject, Map<Practice, List<Support>> practices, Practice p) {
            return p == Practice.C && subject.eap76() && practices.get(p).isEmpty();
        }

        /** The practice was observed in the source. */
        boolean met(Practice practice) {
            return !practices.get(practice).isEmpty();
        }

        /** C of an eAP 76 person without the visits: credited in full (C6-D1). */
        boolean credited(Practice practice) {
            return isCredited(subject, practices, practice);
        }

        /** The practice counts: observed, or credited. */
        boolean counts(Practice practice) {
            return met(practice) || credited(practice);
        }
    }

    static ComponentSpec spec(Practice practice) {
        return SPECS.get(practice);
    }

    private static Map<Practice, ComponentSpec> specs() {
        Map<Practice, ComponentSpec> specs = new EnumMap<>(Practice.class);
        for (ComponentSpec spec : DESCRIPTOR.components()) {
            specs.put(Practice.valueOf(spec.code()), spec);
        }
        return Collections.unmodifiableMap(specs);
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

    private static IndicatorResult result(List<Assessment> group, List<String> extra, EvaluationContext context) {
        BigInteger subjects = BigInteger.valueOf(group.size());
        BigInteger total = BigInteger.ZERO;
        for (Assessment a : group) {
            total = total.add(a.points());
        }
        List<ResultComponent> components = new ArrayList<>();
        for (Practice p : Practice.values()) {
            BigInteger met =
                    BigInteger.valueOf(group.stream().filter(a -> a.counts(p)).count());
            components.add(ResultComponent.of(spec(p), met, subjects));
        }
        List<String> limitations = new ArrayList<>(DESCRIPTOR.standingLimitationLines());
        limitations.addAll(extra);
        long eap = group.stream().filter(a -> a.subject().eap76()).count();
        if (eap > 0) {
            long observed = group.stream()
                    .filter(a -> a.subject().eap76() && a.met(Practice.C))
                    .count();
            limitations.add(EAP_CREDIT.formatted(spec(Practice.C).weight(), eap, observed));
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

    /** One result per INE of the link in force, in INE order. */
    private static List<TeamResult> teams(List<Assessment> assessed, List<String> extra, EvaluationContext context) {
        Map<String, List<Assessment>> byTeam = new TreeMap<>(Comparator.nullsLast(Comparator.naturalOrder()));
        for (Assessment a : assessed) {
            byTeam.computeIfAbsent(a.subject().ine(), k -> new ArrayList<>()).add(a);
        }
        List<TeamResult> teams = new ArrayList<>(byTeam.size());
        byTeam.forEach(
                (ine, members) -> teams.add(new TeamResult(ine, cnes(members), result(members, extra, context))));
        return teams;
    }

    /** The team's CNES when its members agree on one, else {@code null}. */
    private static String cnes(List<Assessment> members) {
        Set<String> cnes = new TreeSet<>();
        members.forEach(a -> cnes.add(Objects.requireNonNullElse(a.subject().cnes(), "")));
        return cnes.size() == 1 && !cnes.contains("") ? cnes.iterator().next() : null;
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
