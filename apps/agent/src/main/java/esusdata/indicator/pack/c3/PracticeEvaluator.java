package esusdata.indicator.pack.c3;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Decides the eleven practices of one eligible episode. */
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

    /** The practices of the episode, A..K. */
    Map<Practice, PracticeOutcome> evaluate(PersonEvidence person, Episode episode, boolean eap76) {
        GestationWindow window = episode.window();
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
}
