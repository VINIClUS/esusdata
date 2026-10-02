package esusdata.indicator.pack.c5;

import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalRegistration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The C5 denominator as of the cutoff (item 14, p. 1; item 15, p. 1–2): every person the extract
 * shows with hypertension — a listed condition of any basis, or the self-reported flag of the
 * individual registration — gets exactly one decision, eligible or excluded with a stable reason,
 * so the population can be rebuilt from the evidence (ENG-36). The link to a team is the version of
 * the individual registration in force at the cutoff (§1.7.3), never the latest encounter.
 */
final class C5Cohort {

    static final String ELIGIBLE = "ELEGIVEL";
    static final String ONLY_SELF_REPORTED = "EXCLUIDO_SO_AUTORREFERIDO";
    /** A listed professional condition exists, but none recorded between 2013 and the cutoff. */
    static final String NO_CONDITION_IN_PERIOD = "EXCLUIDO_SEM_CONDICAO_AVALIADA";

    static final String DEATH = "EXCLUIDO_OBITO";
    static final String LEFT_TERRITORY = "EXCLUIDO_SAIDA_TERRITORIO";
    static final String NO_LINK = "EXCLUIDO_SEM_VINCULO";
    static final String CONDITIONS_RESOLVED = "EXCLUIDO_CONDICOES_RESOLVIDAS";

    /** «Saída do cidadão do cadastro» with «Mudança de território» (item 15, p. 1). */
    private static final Set<String> TERRITORY_EXITS = Set.of("136", "MUDANCA_TERRITORIO", "MUDANCA DE TERRITORIO");

    /** Death recorded as the exit of the individual registration («Óbito no CadSUS», item 15, p. 2). */
    private static final Set<String> DEATH_EXITS = Set.of("135", "OBITO");

    private final Map<String, C5Conditions.State> conditions;
    private final Set<String> selfReported = new HashSet<>();
    private final Map<String, CanonicalRegistration> links = new HashMap<>();
    private final Set<String> deaths = new HashSet<>();

    /** One person's decision, with the team of the registration in force (if any). */
    record Decision(String personKey, String reasonCode, String cnes, String ine) {
        boolean eligible() {
            return ELIGIBLE.equals(reasonCode);
        }
    }

    private C5Cohort(CanonicalDataset data, LocalDate cutoff) {
        conditions = C5Conditions.professionalStates(data.conditions(), cutoff);
        for (CanonicalCondition c : data.conditions()) {
            if (C5Conditions.isEligible(c) && C5Conditions.isSelfReported(c)) {
                selfReported.add(c.personKey());
            }
        }
        for (CanonicalRegistration r : data.registrations()) {
            if (Boolean.TRUE.equals(r.selfReportedHypertension())) {
                selfReported.add(r.personKey());
            }
            keepIfInForce(r, cutoff);
        }
        for (CanonicalPerson p : data.persons()) {
            LocalDate death = C5Event.date(p.deathDate());
            if (death != null && !death.isAfter(cutoff)) {
                deaths.add(p.personKey());
            }
        }
    }

    /** One decision per person of the reconstructed population, ordered by person key. */
    static SortedMap<String, Decision> decide(CanonicalDataset data, LocalDate cutoff) {
        C5Cohort cohort = new C5Cohort(data, cutoff);
        SortedMap<String, Decision> decisions = new TreeMap<>();
        for (String person : population(data)) {
            decisions.put(person, cohort.decision(person));
        }
        return decisions;
    }

    private static Set<String> population(CanonicalDataset data) {
        Set<String> people = new HashSet<>();
        for (CanonicalCondition c : data.conditions()) {
            if (C5Conditions.isEligible(c)) {
                people.add(c.personKey());
            }
        }
        for (CanonicalRegistration r : data.registrations()) {
            if (Boolean.TRUE.equals(r.selfReportedHypertension())) {
                people.add(r.personKey());
            }
        }
        people.remove(null);
        return people;
    }

    /**
     * The latest complete registration version up to the cutoff; on the same date the later one in
     * reading order. A simplified record is not an individual registration (§1.7).
     */
    private void keepIfInForce(CanonicalRegistration r, LocalDate cutoff) {
        LocalDate date = C5Event.date(r.registrationDate());
        if (date == null || date.isAfter(cutoff) || Boolean.TRUE.equals(r.simplified())) {
            return;
        }
        CanonicalRegistration current = links.get(r.personKey());
        if (current == null || !date.isBefore(C5Event.date(current.registrationDate()))) {
            links.put(r.personKey(), r);
        }
    }

    private Decision decision(String person) {
        CanonicalRegistration link = links.get(person);
        String reason = reason(person, link);
        return link == null
                ? new Decision(person, reason, null, null)
                : new Decision(person, reason, link.cnes(), link.ine());
    }

    /** The first reason that applies, in the order of the contract; {@link #ELIGIBLE} otherwise. */
    private String reason(String person, CanonicalRegistration link) {
        C5Conditions.State state = conditions.get(person);
        if (state == null) {
            return selfReported.contains(person) ? ONLY_SELF_REPORTED : NO_CONDITION_IN_PERIOD;
        }
        if (deaths.contains(person) || exitIn(link, DEATH_EXITS)) {
            return DEATH;
        }
        if (exitIn(link, TERRITORY_EXITS)) {
            return LEFT_TERRITORY;
        }
        if (link == null || Boolean.TRUE.equals(link.inactive()) || Boolean.TRUE.equals(link.refused())) {
            return NO_LINK;
        }
        return state == C5Conditions.State.ALL_RESOLVED ? CONDITIONS_RESOLVED : ELIGIBLE;
    }

    private static boolean exitIn(CanonicalRegistration link, Set<String> exits) {
        if (link == null || link.exitReason() == null) {
            return false;
        }
        return exits.contains(C5Conditions.normalized(link.exitReason()));
    }
}
