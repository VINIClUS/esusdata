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
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.Scores;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import esusdata.indicator.pack.c7.C7Cohort.Member;
import esusdata.indicator.pack.c7.C7Practices.Decision;
import esusdata.indicator.pack.c7.C7Practices.Fact;
import esusdata.indicator.pack.c7.C7Practices.Outcome;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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

    private C7Rule() {}

    /** The ungated outcome: municipal result, one per team (INE) and the evidence. */
    public static RuleOutcome compute(CanonicalDataset data, EvaluationContext context) {
        validateScope(data, context.municipalityIbge());
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
            teams.add(new TeamResult(team.getKey(), group.get(0).member().cnes(), result(group, context)));
        }
        return new RuleOutcome(result(eligible, context), teams, evidence(evaluated));
    }

    /** A person with the decision of each subgroup of their age (empty when not eligible). */
    private record Evaluated(Member member, Map<String, Decision> decisions) {}

    private static Evaluated evaluate(Member m, C7Practices practices) {
        Map<String, Decision> decisions = new LinkedHashMap<>();
        if (m.eligible()) {
            for (ComponentSpec spec : C7Pack.COMPONENTS) {
                if (C7Practices.inSubgroup(spec.code(), m.age())) {
                    decisions.put(spec.code(), practices.decide(spec.code(), m));
                }
            }
        }
        return new Evaluated(m, decisions);
    }

    private static IndicatorResult result(List<Evaluated> group, EvaluationContext context) {
        List<ResultComponent> components = new ArrayList<>(C7Pack.COMPONENTS.size());
        List<String> limitations = new ArrayList<>();
        for (ComponentSpec spec : C7Pack.COMPONENTS) {
            ResultComponent component = component(spec, group);
            components.add(component);
            if (component.status() != IndicatorStatus.COMPUTED) {
                limitations.add(undefinedReason(spec.code(), component.status()));
            }
        }
        IndicatorStatus status;
        Optional<ExactRatio> value = Scores.weightedSum(components);
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
    private static ResultComponent component(ComponentSpec spec, List<Evaluated> group) {
        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = BigInteger.ZERO;
        boolean ambiguous = false;
        for (Evaluated e : group) {
            Decision d = e.decisions().get(spec.code());
            if (d == null) {
                continue;
            }
            switch (d.outcome()) {
                case MET -> {
                    numerator = numerator.add(BigInteger.ONE);
                    denominator = denominator.add(BigInteger.ONE);
                }
                case NOT_MET -> denominator = denominator.add(BigInteger.ONE);
                case AMBIGUOUS_PRACTICE -> {
                    denominator = denominator.add(BigInteger.ONE);
                    ambiguous = true;
                }
                case AMBIGUOUS_DENOMINATOR -> ambiguous = true;
            }
        }
        if (!ambiguous) {
            return ResultComponent.of(spec, numerator, denominator);
        }
        return new ResultComponent(
                spec.code(), spec.kind(), spec.weight(), numerator, denominator, null, IndicatorStatus.RULE_AMBIGUITY);
    }

    private static String undefinedReason(String code, IndicatorStatus status) {
        if (status == IndicatorStatus.NO_DENOMINATOR) {
            return "AMB-C7-01: subpopulação " + code + " sem denominador; a ficha não define o escore "
                    + "(P10) — sem zerar nem renormalizar as demais.";
        }
        String cases = "A".equals(code)
                ? "AMB-C7-08 (02.02.10.025-1 anterior à competência 2026-01)"
                : "AMB-C7-05 (homem transgênero de 9 a 14 anos) ou AMB-C7-06 (dose HPV além de 60 meses)";
        return "Subpopulação " + code + " com caso que a ficha não define, " + cases
                + ": escore indisponível até esclarecimento (ver evidência).";
    }

    private static List<EvidenceItem> evidence(List<Evaluated> evaluated) {
        List<EvidenceItem> items = new ArrayList<>();
        for (Evaluated e : evaluated) {
            Member m = e.member();
            items.add(row(m, null, m.eligible() ? EvidenceDecision.ELIGIBLE : EvidenceDecision.EXCLUDED, m.reason()));
            for (Map.Entry<String, Decision> entry : e.decisions().entrySet()) {
                Decision d = entry.getValue();
                items.add(row(m, entry.getKey(), decisionOf(d.outcome()), d.reason()));
                for (Fact f : d.support()) {
                    items.add(new EvidenceItem(
                            EvidenceSubjectKind.PERSON,
                            m.personKey(),
                            f.sourceRef(),
                            f.date().toString(),
                            entry.getKey(),
                            EvidenceDecision.SUPPORTING_EVENT,
                            EVENTO_SUSTENTA_PRATICA,
                            null,
                            f.cnes(),
                            f.ine(),
                            f.cbo(),
                            null));
                }
            }
        }
        return items;
    }

    private static EvidenceDecision decisionOf(Outcome outcome) {
        return switch (outcome) {
            case MET -> EvidenceDecision.PRACTICE_MET;
            case NOT_MET, AMBIGUOUS_PRACTICE -> EvidenceDecision.PRACTICE_NOT_MET;
            case AMBIGUOUS_DENOMINATOR -> EvidenceDecision.EXCLUDED;
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
            if (!municipality.equals(municipalityOf.apply(row))) {
                throw new IllegalArgumentException(
                        "record municipality does not match requested municipality: " + municipalityOf.apply(row));
            }
        }
    }
}
