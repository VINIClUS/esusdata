package esusdata.indicator.pack.c7;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.Scores;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.PackSupport;
import esusdata.indicator.pack.c7.C7Cohort.Member;
import esusdata.indicator.pack.c7.C7Practices.Decision;
import esusdata.indicator.pack.c7.C7Practices.Fact;
import esusdata.indicator.pack.c7.C7Practices.Outcome;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * C7 for one competência, before the release gates (item 23 da ficha, pp. 2–3):
 * {@code 20·(a/b) + 30·(c/d) + 30·(e/f) + 20·(g/h)}, each subgroup over its own denominator, on
 * the 0–100 scale (never ×100 again). A subgroup without denominator or with a case the ficha
 * leaves open leaves the score undefined ({@code RULE_AMBIGUITY}, P10/AMB-C7-01) — never zeroed,
 * never renormalized. {@link C7Pack#evaluate} applies the gates.
 */
public final class C7Rule {

    static final String EVENTO_SUSTENTA_PRATICA = "EVENTO_SUSTENTA_PRATICA";

    /** A record that made the practice ambiguous for the person (AMB-C7-06/08). */
    static final String EVENTO_AMBIGUO = "EVENTO_AMBIGUO";

    private C7Rule() {}

    /** The ungated outcome: municipal result, one per team (INE) and the evidence. */
    public static RuleOutcome compute(CanonicalDataset data, EvaluationContext context) {
        validateScope(data, context.municipalityIbge());
        List<String> missing = PackSupport.uncoveredParts(data, C7Pack.parts(context.competencia())).stream()
                .map(PartRequirement::capability)
                .toList();
        if (!missing.isEmpty()) {
            return new RuleOutcome(unsupported(missing, context), List.of(), List.of());
        }
        List<Member> members = C7Cohort.resolve(
                data.persons(), data.registrations(), context.competencia().atEndOfMonth());
        C7Practices practices = new C7Practices(data, context.competencia());
        List<Evaluated> evaluated = new ArrayList<>(members.size());
        for (Member m : members) {
            evaluated.add(evaluate(m, practices));
        }
        List<Evaluated> eligible =
                evaluated.stream().filter(e -> e.member().eligible()).toList();

        Map<String, List<Evaluated>> byTeam = new TreeMap<>();
        for (Evaluated e : eligible) {
            byTeam.computeIfAbsent(e.member().ine(), k -> new ArrayList<>()).add(e);
        }
        List<TeamResult> teams = new ArrayList<>(byTeam.size());
        for (Map.Entry<String, List<Evaluated>> team : byTeam.entrySet()) {
            List<Evaluated> group = team.getValue();
            teams.add(new TeamResult(team.getKey(), commonCnes(group), result(group, context)));
        }
        return new RuleOutcome(result(eligible, context), teams, evidence(evaluated));
    }

    /** The CNES of the team's links, or null when they disagree — never the first one by order. */
    private static String commonCnes(List<Evaluated> group) {
        String cnes = group.get(0).member().cnes();
        for (Evaluated e : group) {
            if (!Objects.equals(cnes, e.member().cnes())) {
                return null;
            }
        }
        return cnes;
    }

    /** A person with the decision of each subgroup of their age (empty when not eligible). */
    private record Evaluated(Member member, Map<C7Subgroup, Decision> decisions) {}

    /** One subgroup's counts and the reason codes of the cases the ficha leaves open in it. */
    private record Count(ResultComponent component, SortedSet<String> ambiguities) {}

    private static Evaluated evaluate(Member m, C7Practices practices) {
        Map<C7Subgroup, Decision> decisions = new EnumMap<>(C7Subgroup.class);
        if (m.eligible()) {
            for (C7Subgroup subgroup : C7Subgroup.values()) {
                if (subgroup.includes(m.age())) {
                    decisions.put(subgroup, practices.decide(subgroup, m));
                }
            }
        }
        return new Evaluated(m, decisions);
    }

    private static IndicatorResult result(List<Evaluated> group, EvaluationContext context) {
        List<ResultComponent> components = new ArrayList<>(C7Pack.COMPONENTS.size());
        List<String> limitations = new ArrayList<>();
        for (ComponentSpec spec : C7Pack.COMPONENTS) {
            Count count = count(spec, group);
            components.add(count.component());
            if (count.component().status() == IndicatorStatus.NO_DENOMINATOR) {
                limitations.add("AMB-C7-01: subpopulação " + spec.code() + " sem denominador; a ficha não define "
                        + "o escore (P10) — sem zerar nem renormalizar as demais.");
            } else if (!count.ambiguities().isEmpty()) {
                limitations.add("Subpopulação " + spec.code() + " com caso que a ficha não define ("
                        + String.join(", ", count.ambiguities()) + "): escore indisponível até esclarecimento.");
            }
        }
        Optional<ExactRatio> value = Scores.weightedSum(components);
        IndicatorStatus status;
        if (components.stream().allMatch(c -> c.status() == IndicatorStatus.NO_DENOMINATOR)) {
            status = IndicatorStatus.NO_DENOMINATOR;
            limitations.clear();
        } else {
            status = value.isPresent() ? IndicatorStatus.COMPUTED : IndicatorStatus.RULE_AMBIGUITY;
        }
        limitations.addAll(C7Pack.STANDING_LIMITATIONS);
        ExactRatio exact = value.orElse(null);
        return new IndicatorResult(
                status,
                exact == null ? null : exact.toScaledBigDecimal(4).toPlainString(),
                null,
                null,
                null,
                exact == null ? null : C7Pack.BANDS.classify(exact).orElse(null),
                context.referencePeriod(),
                C7Pack.RULE_VERSION,
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations,
                C7Pack.CALCULATION_POLICY_VERSION,
                ValueKind.COMPOSITE_SCORE,
                exact,
                components,
                true);
    }

    /** Exact counts of one subgroup; any open case of the ficha makes it {@code RULE_AMBIGUITY}. */
    private static Count count(ComponentSpec spec, List<Evaluated> group) {
        C7Subgroup subgroup = C7Subgroup.valueOf(spec.code());
        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = BigInteger.ZERO;
        SortedSet<String> ambiguities = new TreeSet<>();
        for (Evaluated e : group) {
            Decision d = e.decisions().get(subgroup);
            if (d == null) {
                continue;
            }
            if (d.outcome() != Outcome.AMBIGUOUS_DENOMINATOR) {
                denominator = denominator.add(BigInteger.ONE);
            }
            if (d.outcome() == Outcome.MET) {
                numerator = numerator.add(BigInteger.ONE);
            } else if (d.outcome() != Outcome.NOT_MET) {
                ambiguities.add(ambiguityId(d.reason()));
            }
        }
        if (ambiguities.isEmpty()) {
            return new Count(ResultComponent.of(spec, numerator, denominator), ambiguities);
        }
        return new Count(
                new ResultComponent(
                        spec.code(),
                        spec.kind(),
                        spec.weight(),
                        numerator,
                        denominator,
                        null,
                        IndicatorStatus.RULE_AMBIGUITY),
                ambiguities);
    }

    /** {@code AMB_C7_08_HPV_…} → {@code AMB-C7-08}, the id of the transcription. */
    private static String ambiguityId(String reasonCode) {
        return reasonCode.substring(0, "AMB_C7_NN".length()).replace('_', '-');
    }

    private static List<EvidenceItem> evidence(List<Evaluated> evaluated) {
        List<EvidenceItem> items = new ArrayList<>();
        for (Evaluated e : evaluated) {
            Member m = e.member();
            items.add(row(m, null, m.eligible() ? EvidenceDecision.ELIGIBLE : EvidenceDecision.EXCLUDED, m.reason()));
            for (Map.Entry<C7Subgroup, Decision> entry : e.decisions().entrySet()) {
                Decision d = entry.getValue();
                String component = entry.getKey().name();
                items.add(row(m, component, decisionOf(d.outcome()), d.reason()));
                String supportReason = d.outcome() == Outcome.MET ? EVENTO_SUSTENTA_PRATICA : EVENTO_AMBIGUO;
                for (Fact f : d.support()) {
                    items.add(new EvidenceItem(
                            EvidenceSubjectKind.PERSON,
                            m.personKey(),
                            f.sourceRef(),
                            f.date().toString(),
                            component,
                            EvidenceDecision.SUPPORTING_EVENT,
                            supportReason,
                            null,
                            f.cnes(),
                            f.ine(),
                            f.cbo(),
                            f.modality()));
                }
            }
        }
        return items;
    }

    private static EvidenceDecision decisionOf(Outcome outcome) {
        return switch (outcome) {
            case MET -> EvidenceDecision.PRACTICE_MET;
            case NOT_MET -> EvidenceDecision.PRACTICE_NOT_MET;
            case AMBIGUOUS_PRACTICE, AMBIGUOUS_DENOMINATOR -> EvidenceDecision.PRACTICE_AMBIGUOUS;
        };
    }

    /** A person-level row; points stay null: C7 scores subpopulations, not people (AMB-C7-02). */
    private static EvidenceItem row(Member m, String component, EvidenceDecision decision, String reason) {
        return new EvidenceItem(
                EvidenceSubjectKind.PERSON,
                m.personKey(),
                null,
                null,
                component,
                decision,
                reason,
                null,
                m.cnes(),
                m.ine(),
                null,
                null);
    }

    private static IndicatorResult unsupported(List<String> missing, EvaluationContext context) {
        List<String> limitations = new ArrayList<>();
        limitations.add("Capacidade não lida ou lida com janela menor que a pedida: " + String.join(", ", missing)
                + " — sem valor, nunca zero.");
        limitations.addAll(C7Pack.STANDING_LIMITATIONS);
        return new IndicatorResult(
                IndicatorStatus.UNSUPPORTED_SOURCE,
                null,
                null,
                null,
                null,
                null,
                context.referencePeriod(),
                C7Pack.RULE_VERSION,
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations,
                C7Pack.CALCULATION_POLICY_VERSION,
                ValueKind.COMPOSITE_SCORE,
                null,
                List.of(),
                true);
    }

    /** Every record must belong to the authorized municipality; anything else is refused. */
    private static void validateScope(CanonicalDataset data, String municipality) {
        check(data.persons(), CanonicalPerson::municipalityIbge, municipality);
        check(data.registrations(), CanonicalRegistration::municipalityIbge, municipality);
        check(data.procedureEvents(), CanonicalProcedureEvent::municipalityIbge, municipality);
        check(data.careEvents(), CanonicalCareEvent::municipalityIbge, municipality);
        check(data.immunizations(), CanonicalImmunization::municipalityIbge, municipality);
    }

    private static <T> void check(List<T> rows, Function<T, String> municipalityOf, String municipality) {
        for (T row : rows) {
            String recorded = municipalityOf.apply(row);
            if (!municipality.equals(recorded)) {
                throw new IllegalArgumentException(
                        "record municipality does not match requested municipality: " + recorded);
            }
        }
    }
}
