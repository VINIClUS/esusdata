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
import esusdata.indicator.pack.PackSupport;
import esusdata.indicator.pack.c6.C6Cohort.Subject;
import esusdata.indicator.pack.c6.C6Practices.Practice;
import esusdata.indicator.pack.c6.C6Practices.Support;
import esusdata.indicator.pack.c6.C6Teams.TeamType;
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
 * dia da competência, sem ×100. As convenções provisórias das ambiguidades da ficha (AMB-C6-xx)
 * estão nas limitações permanentes do descritor; enquanto houver uma, ou um portão incompleto, o
 * resultado sai {@code BLOCKED} com as contagens.
 */
public final class C6Pack implements IndicatorRule {

    private static final String TWELVE_MONTHS = "12 meses";

    /** The ficha has no upper age; the birth bind only needs a finite start (no one is 130). */
    private static final int OLDEST_AGE_READ = 130;

    public static final String ID = "c6-cuidado-pessoa-idosa";
    public static final String RULE_VERSION = ID + "@0.1.0";

    /** Reason code of practice C for a person of an eAP tipo 76 team while P07 is open. */
    static final String C_REASON_EAP = "C_AMBIGUA_EAP76_AMB_C6_01";

    /** Reason code of practice C for a person whose team has two types at the same latest instant. */
    static final String C_REASON_CONFLICTING_TYPE = "C_AMBIGUA_TIPO_EQUIPE_CONFLITANTE";

    static final String EAP_LIMITATION = "AMB-C6-01: a boa prática (C) «não será condicionante de pontuação para eAP, "
            + "tipo 76» (item 24 b, p. 2) admite três leituras (crédito integral, renormalização sobre 75 ou só não "
            + "exigir); resultado sem valor até a P07 (MET-23). A, B e D seguem nos componentes; o componente C "
            + "fica RULE_AMBIGUITY com as contagens exatas e a prática C da pessoa, PRACTICE_AMBIGUOUS.";

    static final String CONFLICTING_TYPE_LIMITATION = "Tipo de equipe divergente na observação mais recente até o "
            + "corte (§1.7.3: sem escolher um): a prática C dessas equipes fica sem decisão e o resultado sem valor.";

    private static final List<String> STANDING_LIMITATIONS = List.of(
            "Dados fora do PEC local: doses só na RNDS/RIA (lacuna L4) não aparecem e a falta de integração não é "
                    + "ausência de vacinação; registros de profissionais de outros municípios («no país», item 4.4) "
                    + "e o «Óbito no CadSUS» (item 15) não estão no extrato.",
            "Vínculo: a ficha remete à Portaria SAPS/MS nº 161/2024 (AMB-C6-04); o vínculo é a versão completa "
                    + "do cadastro individual local vigente no corte, lida nos 24 meses civis até a competência (quem "
                    + "não teve versão nesse período fica sem vínculo); estimativa local que não equivale ao vínculo "
                    + "do Siaps (lacuna L8). Saída 136 (mudança de território) e 135 "
                    + "(óbito) são códigos LEDI do dicionário do DW, não da ficha; outro código de saída exclui "
                    + "(EXCLUIDO_SAIDA_CADASTRO_NAO_MAPEADA). Pessoa sem INE no cadastro vigente não está «vinculada "
                    + "à equipe» (item 23) e sai como EXCLUIDO_SEM_VINCULO.",
            "Exclusões que a ficha não define (item 15 só interrompe por território, equipe e óbito), adotadas "
                    + "localmente em vez do desempate da Portaria SAPS/MS nº 161/2024: data de nascimento divergente "
                    + "entre registros da mesma pessoa, versões do cadastro da mesma data divergentes, recusa de "
                    + "cadastro e ficha inativa. Se o DW marcar versões substituídas como inativas, a exclusão por "
                    + "ficha inativa precisa ser validada no Portão C.",
            "Tipo de equipe ausente no DW (lacuna L1): a exceção eAP tipo 76 da prática C (AMB-C6-01) só é "
                    + "reconhecida quando o extrato traz o tipo; sem ele, C é exigida de todas as equipes e a "
                    + "validação de equipes (Portaria GM/MS nº 3.493/2024, SCNES) não é feita. Com tipo conhecido "
                    + "fora de 70/76 a pessoa sai (EXCLUIDO_EQUIPE_FORA_DO_ESCOPO).",
            "Não verificados no PEC local: CNS profissional identificado, estabelecimento de APS, habilitação de "
                    + "CBO na tabela SIGTAP (item 24 f), identificação conforme CadSUS (item 24 a) e o corte do "
                    + "20º dia útil (item 11).",
            "AMB-C6-02: janela de 12 meses civis terminando no último dia da competência, nunca 365 dias. "
                    + "AMB-C6-05: idade completa no último dia da competência, aniversário CLAMP_TO_MONTH_END "
                    + "(29/02 + 1 ano = 28/02).",
            "AMB-C6-06: consulta (A) só pelo MIAI com CBO do Quadro 02, presencial ou remota, sem códigos "
                    + "SIGTAP de consulta e sem exigir o problema/condição avaliada do item 24 e.",
            "AMB-C6-07/AMB-C6-11: peso e altura (B) na mesma data civil, em qualquer combinação de MIAI, MIP, "
                    + "MIAC, MIVDT e SIGTAP 0101040083/0101040075, ou 0101040024 sozinho (SIGTAP só de MIP ou MIAI), "
                    + "medidas só de MIP ou MIAC e, no MIVDT, só de ACS/TACS com motivo preenchido (item 24 e), por CBO "
                    + "do Quadro 03; procedimento consolidado não chega da capacidade.",
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
            limitations.addAll(STANDING_LIMITATIONS);
            return new RuleOutcome(
                    build(IndicatorStatus.UNSUPPORTED_SOURCE, null, null, null, List.of(), limitations, context),
                    List.of(),
                    List.of());
        }
        LocalDate lastDay = context.competencia().atEndOfMonth();
        C6Teams teamTypes = C6Teams.resolve(data.teams(), context.dataCutoff());
        List<Subject> subjects = C6Cohort.resolve(data, lastDay, context.dataCutoff(), teamTypes);
        C6Practices practices = C6Practices.index(data, practiceWindow(context));
        List<Assessment> assessed = new ArrayList<>();
        for (Subject s : subjects) {
            if (s.eligible()) {
                assessed.add(Assessment.of(s, practices.assess(s.personKey()), visitsReason(s.teamType())));
            }
        }
        List<String> extra = new ArrayList<>();
        if (teamTypes.undated() > 0) {
            extra.add(teamTypes.undated() + " observação(ões) de tipo de equipe sem data ignorada(s) (§1.7.3).");
        }
        IndicatorResult municipal = result(assessed, extra, context);
        return new RuleOutcome(municipal, teams(assessed, extra, context), C6Evidence.of(subjects, assessed, lastDay));
    }

    /** Practice C cannot be decided for eAP tipo 76 (AMB-C6-01) or for a team of conflicting type. */
    private static String visitsReason(TeamType type) {
        if (type == TeamType.EAP) {
            return C_REASON_EAP;
        }
        return type == TeamType.CONFLICTING ? C_REASON_CONFLICTING_TYPE : null;
    }

    /** One eligible person with the events behind each practice and the points they earn. */
    record Assessment(
            Subject subject, Map<Practice, List<Support>> practices, String visitsAmbiguity, BigInteger points) {
        Assessment {
            practices = Collections.unmodifiableMap(new EnumMap<>(practices));
        }

        static Assessment of(Subject subject, Map<Practice, List<Support>> practices, String visitsAmbiguity) {
            List<ComponentSpec> satisfied = new ArrayList<>();
            for (Practice p : Practice.values()) {
                if (!practices.get(p).isEmpty()) {
                    satisfied.add(spec(p));
                }
            }
            return new Assessment(subject, practices, visitsAmbiguity, Scores.points(satisfied));
        }

        /** Practice C is undecided for this person: no points for C, none for the person. */
        boolean ambiguous() {
            return visitsAmbiguity != null;
        }

        boolean met(Practice practice) {
            return !practices.get(practice).isEmpty();
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
        Set<String> ambiguities = new TreeSet<>();
        for (Assessment a : group) {
            total = total.add(a.points());
            if (a.ambiguous()) {
                ambiguities.add(a.visitsAmbiguity());
            }
        }
        boolean ambiguous = !ambiguities.isEmpty();
        List<ResultComponent> components = new ArrayList<>();
        for (Practice p : Practice.values()) {
            BigInteger met =
                    BigInteger.valueOf(group.stream().filter(a -> a.met(p)).count());
            components.add(
                    ambiguous && p == Practice.C
                            ? ambiguousComponent(met, subjects)
                            : ResultComponent.of(spec(p), met, subjects));
        }
        List<String> limitations = new ArrayList<>(STANDING_LIMITATIONS);
        limitations.addAll(extra);
        if (ambiguous) {
            limitations.add(ambiguities.contains(C_REASON_EAP) ? EAP_LIMITATION : CONFLICTING_TYPE_LIMITATION);
            if (ambiguities.size() > 1) {
                limitations.add(CONFLICTING_TYPE_LIMITATION);
            }
            return build(IndicatorStatus.RULE_AMBIGUITY, null, null, subjects, components, limitations, context);
        }
        Optional<ExactRatio> value = Scores.meanPoints(total, subjects);
        IndicatorStatus status = value.isPresent() ? IndicatorStatus.COMPUTED : IndicatorStatus.NO_DENOMINATOR;
        return build(status, value.orElse(null), total, subjects, components, limitations, context);
    }

    /** C of a group with eAP tipo 76 people (AMB-C6-01): exact counts, no value. */
    private static ResultComponent ambiguousComponent(BigInteger met, BigInteger subjects) {
        ComponentSpec c = spec(Practice.C);
        return new ResultComponent(c.code(), c.kind(), c.weight(), met, subjects, null, IndicatorStatus.RULE_AMBIGUITY);
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
