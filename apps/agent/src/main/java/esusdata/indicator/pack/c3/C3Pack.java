package esusdata.indicator.pack.c3;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ReleaseGates;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.indicator.model.ValueKind;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * C3 — Cuidado na gestação e puerpério (Tech Spec §2.4; ficha transcrita em {@code docs/metodologia/c3-gestacao-puerperio.md}).
 *
 * <p>Episódios (gestações) identificados pela DUM ou pela idade gestacional do MIAI; coorte da
 * competência (gestantes e puérperas ativas, vínculo no corte, óbito, aborto); onze práticas em
 * decisão tri-estado (cumpre / não cumpre / ambígua); escore {@code Σ pontos ÷ episódios
 * elegíveis} na escala 0–100. Ambiguidade da ficha nunca vira escolha silenciosa: o resultado fica
 * {@code RULE_AMBIGUITY}. Enquanto houver portão incompleto, {@link #evaluate} devolve {@code
 * BLOCKED} com as contagens ({@link RuleOutcomes#gate}).
 */
public final class C3Pack implements IndicatorRule {

    private static final String PREGNANCY = "gestação";
    private static final String PUERPERIUM = "puerpério";

    public static final String ID = "c3-gestacao-puerperio";
    public static final String RULE_VERSION = ID + "@0.1.0";

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            ID,
            RULE_VERSION,
            "qualidade-esf-eap-2026-06",
            "QUALIDADE_ESF_EAP",
            "C3",
            "Cuidado na gestação e puerpério",
            ValueKind.SCORE,
            "percentual",
            "GESTACOES_E_PUERPERIOS_VINCULADOS",
            "c3-exact-score@1",
            List.of(
                    Capabilities.CITIZEN,
                    Capabilities.INDIVIDUAL_REGISTRATION,
                    Capabilities.CONDITION_LIST,
                    Capabilities.CARE_ENCOUNTER,
                    Capabilities.DENTAL_ENCOUNTER,
                    Capabilities.PROCEDURE_PERFORMED,
                    Capabilities.EXAM_REQUEST_EVALUATION,
                    Capabilities.HOME_VISIT,
                    Capabilities.MEASUREMENT_RECORD,
                    Capabilities.IMMUNIZATION_HISTORY),
            List.of(
                    ComponentSpec.practice(
                            "A",
                            "Ter a 1ª consulta presencial ou remota realizada por médica(o) ou enfermeira(o), até a 12ª semana de gestação.",
                            10,
                            "até a 12ª semana de gestação"),
                    ComponentSpec.practice(
                            "B",
                            "Ter pelo menos 07 (sete) consultas presenciais ou remotas realizadas por médica(o) ou enfermeira(o) durante o período da gestação.",
                            9,
                            PREGNANCY),
                    ComponentSpec.practice(
                            "C",
                            "Ter pelo menos 07 (sete) registros de aferição de pressão arterial realizadas durante o período da gestação.",
                            9,
                            PREGNANCY),
                    ComponentSpec.practice(
                            "D",
                            "Ter pelo menos 07 (sete) registros simultâneos de peso e altura durante o período da gestação.",
                            9,
                            PREGNANCY),
                    ComponentSpec.practice(
                            "E",
                            "Ter pelo menos 03 (três) visitas domiciliares realizadas por ACS/TACS, após a primeira consulta do pré-natal.",
                            9,
                            "após a 1ª consulta do pré-natal"),
                    ComponentSpec.practice(
                            "F",
                            "Ter vacina acelular contra difteria, tétano, coqueluche (dTpa) registrada a partir da 20ª semana de cada gestação.",
                            9,
                            "a partir da 20ª semana"),
                    ComponentSpec.practice(
                            "G",
                            "Ter registro dos testes rápidos ou dos exames avaliados para sífilis, HIV e hepatites B e C realizados no 1º trimestre de cada gestação.",
                            9,
                            "1º trimestre"),
                    ComponentSpec.practice(
                            "H",
                            "Ter registro dos testes rápidos ou dos exames avaliados para sífilis e HIV realizados no 3º trimestre de cada gestação.",
                            9,
                            "3º trimestre"),
                    ComponentSpec.practice(
                            "I",
                            "Ter pelo menos 01 registro de consulta presencial ou remota realizada por médica(o) ou enfermeira(o) durante o puerpério.",
                            9,
                            PUERPERIUM),
                    ComponentSpec.practice(
                            "J",
                            "Ter pelo menos 01 visita domiciliar realizada por ACS/TACS durante o puerpério.",
                            9,
                            PUERPERIUM),
                    ComponentSpec.practice(
                            "K",
                            "Ter pelo menos 01 atividade em saúde bucal realizada por cirurgiã(ão) dentista ou técnica(o) de saúde bucal durante o período da gestação.",
                            9,
                            PREGNANCY)),
            ReleaseGates.noneComplete(),
            C3Limitations.STANDING,
            MonthlyEligibility.MONTHS_WITH_COHORT_EVENT,
            BudgetHint.engineeringDefault(),
            List.of(
                    "https://www.gov.br/saude/pt-br/composicao/saps/publicacoes/fichas-tecnicas/equipe-de-atencao-primaria-e-saude-da-familia/nota-metodologica-c3-cuidado-na-gestacao-e-puerperio",
                    "docs/metodologia/c3-gestacao-puerperio.md"),
            List.of());

    private static final int MONTHS_READ = 13;
    private static final int REGISTRATION_MONTHS_READ = 24;
    private static final int BIRTH_YEARS_READ = 130;

    private final TrimesterConvention convention;

    /** The production rule: no trimester convention is documented (AMB-C3-02). */
    public C3Pack() {
        this(null);
    }

    private C3Pack(TrimesterConvention convention) {
        this.convention = convention;
    }

    /** The rule once a trimester convention is documented at Portão B (AMB-C3-02). */
    static C3Pack withTrimesterConvention(TrimesterConvention convention) {
        return new C3Pack(Objects.requireNonNull(convention, "convention"));
    }

    @Override
    public PackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /**
     * 13 civil months (a DUM up to 336 days before the competência), 24 months of registration
     * versions, and people born in the last 130 years — the ficha has no age range.
     */
    @Override
    public DataRequirements requirements(YearMonth competencia) {
        DateWindow period = DateWindow.lastCivilMonths(competencia, MONTHS_READ);
        DateWindow registrations = DateWindow.lastCivilMonths(competencia, REGISTRATION_MONTHS_READ);
        DateWindow births = new DateWindow(
                competencia.atDay(1).minusYears(BIRTH_YEARS_READ),
                competencia.plusMonths(1).atDay(1));
        List<PartRequirement> parts = new ArrayList<>();
        for (String capability : DESCRIPTOR.requiredCapabilities()) {
            DateWindow window = Capabilities.INDIVIDUAL_REGISTRATION.equals(capability) ? registrations : period;
            parts.add(PartRequirement.personScoped(capability, window, births, codes(capability)));
        }
        return new DataRequirements(DataRequirements.V2, parts);
    }

    @Override
    public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
        return RuleOutcomes.gate(DESCRIPTOR, compute(data, context));
    }

    /** The exact outcome before the release gates (§4.4): what {@link #evaluate} gates. */
    RuleOutcome compute(CanonicalDataset data, EvaluationContext context) {
        List<String> gaps = SourceCoverage.gaps(requirements(context.competencia()), data);
        if (!gaps.isEmpty()) {
            return SourceCoverage.unsupported(DESCRIPTOR, context, gaps);
        }
        LocalDate cutoff = context.dataCutoff();
        SortedMap<String, PersonRecords> people = RecordIndex.of(data, context.municipalityIbge());
        TeamTypes teamTypes = new TeamTypes(data.teams(), cutoff);
        Subjects builder = new Subjects(
                new Cohort(context.competencia(), cutoff, teamTypes),
                new PracticeEvaluator(convention),
                teamTypes,
                cutoff);
        SortedMap<String, Subject> byKey = new TreeMap<>();
        for (PersonRecords person : people.values()) {
            for (Subject subject : builder.of(person)) {
                byKey.put(subject.key(), subject);
            }
        }
        List<Subject> subjects = List.copyOf(byKey.values());
        PracticeWeights weights = new PracticeWeights(DESCRIPTOR.components());
        C3Results results = new C3Results(DESCRIPTOR, context, weights, teamTypes);
        EvidenceRows rows = new EvidenceRows(weights);
        List<EvidenceItem> evidence = new ArrayList<>();
        subjects.forEach(s -> evidence.addAll(rows.of(s)));
        return new RuleOutcome(results.result(subjects), results.teams(subjects), evidence);
    }

    @Override
    public Optional<Classification> classify(ExactRatio value) {
        return Bands.QUALIDADE_C2_C7.classify(value);
    }

    /** The code lists each capability binds (24 f/g, 24 h, Quadro 07, 24 i); the rest bind none. */
    private static SortedMap<String, List<String>> codes(String capability) {
        SortedMap<String, List<String>> lists = new TreeMap<>();
        switch (capability) {
            case Capabilities.PROCEDURE_PERFORMED -> lists.put(Capabilities.PROCEDURE_CODES, C3Codes.PROCEDURE_SIGTAP);
            case Capabilities.EXAM_REQUEST_EVALUATION -> lists.put(Capabilities.PROCEDURE_CODES, C3Codes.TEST_SIGTAP);
            case Capabilities.IMMUNIZATION_HISTORY ->
                lists.put(Capabilities.IMMUNOBIOLOGICAL_CODES, List.of(C3Codes.DTPA_ADULT));
            case Capabilities.CONDITION_LIST -> {
                lists.put(Capabilities.CIAP_CODES, C3Codes.CONDITION_CIAP);
                lists.put(Capabilities.CID_CODES, C3Codes.CONDITION_CID);
            }
            default -> {
                // no code bind
            }
        }
        return lists;
    }
}
