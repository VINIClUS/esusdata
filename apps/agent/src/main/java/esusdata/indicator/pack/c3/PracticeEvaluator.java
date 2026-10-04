package esusdata.indicator.pack.c3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides the eleven practices of one eligible episode under every reading of its dates; a
 * practice whose decision changes with the reading is ambiguous (AMB-C3-03 (i)).
 */
final class PracticeEvaluator {

    private final ExamPractices tests;

    PracticeEvaluator(TrimesterConvention convention) {
        this.tests = new ExamPractices(convention);
    }

    /** One person's evidence, read once and shared by the person's episodes. */
    record PersonEvidence(PersonRecords records, List<Consultation> consultations, List<ExamEvidence> tests) {
        PersonEvidence {
            consultations = List.copyOf(consultations);
            tests = List.copyOf(tests);
        }

        static PersonEvidence of(PersonRecords records) {
            return new PersonEvidence(records, Consultation.of(records), ExamEvidence.of(records));
        }
    }

    /** The practices of the episode, A..K, agreed across its readings. */
    Map<Practice, PracticeOutcome> evaluate(PersonEvidence person, Episode episode, boolean eap76) {
        List<Map<Practice, PracticeOutcome>> perReading = new ArrayList<>();
        for (GestationWindow reading : episode.readings()) {
            perReading.add(evaluate(person, reading, eap76));
        }
        Map<Practice, PracticeOutcome> agreed = new EnumMap<>(Practice.class);
        for (Practice practice : Practice.values()) {
            agreed.put(practice, agree(perReading, practice));
        }
        return agreed;
    }

    private Map<Practice, PracticeOutcome> evaluate(PersonEvidence person, GestationWindow window, boolean eap76) {
        PersonRecords records = person.records();
        List<Consultation> consultations = person.consultations();
        Map<Practice, PracticeOutcome> outcomes = new EnumMap<>(Practice.class);
        outcomes.put(Practice.A, ConsultationPractices.early(consultations, window));
        outcomes.put(Practice.B, ConsultationPractices.seven(consultations, window));
        outcomes.put(Practice.C, RecordingPractices.bloodPressure(records, window));
        outcomes.put(Practice.D, RecordingPractices.anthropometry(records, window));
        outcomes.put(Practice.E, VisitPractices.afterFirstConsultation(records, consultations, window));
        outcomes.put(Practice.F, OtherPractices.dtpa(records, window));
        outcomes.put(Practice.G, tests.firstTrimester(person.tests(), window));
        outcomes.put(Practice.H, tests.thirdTrimester(person.tests(), window));
        outcomes.put(Practice.I, ConsultationPractices.puerperal(consultations, window));
        outcomes.put(Practice.J, VisitPractices.puerperal(records, window));
        outcomes.put(Practice.K, OtherPractices.dental(records, window));
        if (eap76) {
            for (Practice practice : Practice.values()) {
                if (practice.exemptForEap76()) {
                    outcomes.put(practice, PracticeOutcome.EXEMPT);
                }
            }
        }
        return outcomes;
    }

    /**
     * The decision every reading agrees on; when they diverge, AMB-C3-03, with the supports every
     * reading shares keeping their label and the others relabelled AMB-C3-03.
     */
    private static PracticeOutcome agree(List<Map<Practice, PracticeOutcome>> perReading, Practice practice) {
        PracticeOutcome first = perReading.get(0).get(practice);
        boolean diverge = perReading.stream().anyMatch(r -> r.get(practice).decision() != first.decision());
        if (!diverge) {
            return first;
        }
        Map<String, Support> byRef = new LinkedHashMap<>();
        for (Map<Practice, PracticeOutcome> reading : perReading) {
            for (Support support : reading.get(practice).supports()) {
                boolean shared = perReading.stream()
                        .allMatch(r -> r.get(practice).supports().contains(support));
                byRef.putIfAbsent(
                        support.event().refKey(), shared ? support : new Support(support.event(), Ambiguity.AMB_C3_03));
            }
        }
        List<Support> supports = new ArrayList<>(byRef.values());
        supports.sort((x, y) -> EventRef.ORDER.compare(x.event(), y.event()));
        return PracticeOutcome.ambiguous(Ambiguity.AMB_C3_03, supports);
    }
}
