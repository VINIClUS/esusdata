package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalProcedureEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One record of a rapid test or evaluated exam of the Quadro 07 (G, H): performed or evaluated,
 * never only requested (CT-C3-47), by a CBO of the quadro in any model (AMB-C3-18 (iv)); the date
 * is the record's (iii). Each SIGTAP covers the agent its name says; the anti-HTLV covers none
 * (ii).
 */
record ExamEvidence(EventRef event, String sigtap, Set<ExamEvidence.Agent> agents) {

    /** The agents of G and H, named in each code's own description (AMB-C3-18 (ii)). */
    enum Agent {
        SYPHILIS,
        HIV,
        HEPATITIS_B,
        HEPATITIS_C
    }

    ExamEvidence {
        agents = Set.copyOf(agents);
    }

    static List<ExamEvidence> of(PersonRecords person) {
        List<ExamEvidence> evidence = new ArrayList<>();
        for (CanonicalProcedureEvent procedure : person.procedures()) {
            if (Procedures.counts(procedure) && C3Codes.TEST_CBO.matches(procedure.cbo())) {
                add(evidence, EventRef.of(procedure), Procedures.sigtap(procedure));
            }
        }
        for (CanonicalCareEvent event : person.individualCare()) {
            if (C3Codes.TEST_CBO.matches(event.cbo())) {
                Set<String> codes = new LinkedHashSet<>();
                event.proceduresEvaluated().forEach(c -> codes.add(Procedures.digits(c)));
                event.proceduresPerformed().forEach(c -> codes.add(Procedures.digits(c)));
                for (String code : codes) {
                    add(evidence, EventRef.of(event), code);
                }
            }
        }
        evidence.sort((a, b) -> EventRef.ORDER.compare(a.event(), b.event()));
        return distinctByContent(evidence);
    }

    /**
     * The same exam (date and SIGTAP) recorded by the care event and by the exam capability is one
     * record (MET-32): the first in evidence order.
     */
    private static List<ExamEvidence> distinctByContent(List<ExamEvidence> evidence) {
        Map<String, ExamEvidence> byContent = new LinkedHashMap<>();
        for (ExamEvidence item : evidence) {
            byContent.putIfAbsent(item.event().date() + "|" + item.sigtap(), item);
        }
        return List.copyOf(byContent.values());
    }

    private static void add(List<ExamEvidence> evidence, EventRef event, String sigtap) {
        Set<Agent> agents = agentsOf(sigtap);
        if (event.date() != null && !agents.isEmpty()) {
            evidence.add(new ExamEvidence(event, sigtap, agents));
        }
    }

    private static Set<Agent> agentsOf(String sigtap) {
        Set<Agent> agents = EnumSet.noneOf(Agent.class);
        if (C3Codes.SYPHILIS_SIGTAP.contains(sigtap)) {
            agents.add(Agent.SYPHILIS);
        }
        if (C3Codes.HIV_SIGTAP.contains(sigtap)) {
            agents.add(Agent.HIV);
        }
        if (C3Codes.HEPATITIS_B_SIGTAP.contains(sigtap)) {
            agents.add(Agent.HEPATITIS_B);
        }
        if (C3Codes.HEPATITIS_C_SIGTAP.contains(sigtap)) {
            agents.add(Agent.HEPATITIS_C);
        }
        return agents;
    }
}
