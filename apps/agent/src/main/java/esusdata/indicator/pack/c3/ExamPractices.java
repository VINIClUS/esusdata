package esusdata.indicator.pack.c3;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Practices G (sífilis, HIV, hepatites B e C in the 1º trimestre) and H (sífilis and HIV in the 3º
 * trimestre), Quadro 07. The ficha does not define the trimesters: the {@link TrimesterConvention}
 * does (AMB-C3-02) — the 1º trimestre is {@code [DUM, min(DUM + 97, D)]} and the 3º is {@code [DUM
 * + 196, D]}, the day D included (AMB-C3-04).
 */
final class ExamPractices {

    private static final Set<ExamEvidence.Agent> FIRST_TRIMESTER = EnumSet.allOf(ExamEvidence.Agent.class);
    private static final Set<ExamEvidence.Agent> THIRD_TRIMESTER =
            EnumSet.of(ExamEvidence.Agent.SYPHILIS, ExamEvidence.Agent.HIV);

    private final TrimesterConvention convention;

    ExamPractices(TrimesterConvention convention) {
        this.convention = convention;
    }

    /** G: the four agents in the 1º trimestre. */
    PracticeOutcome firstTrimester(List<ExamEvidence> evidence, GestationWindow window) {
        LocalDate last = window.dum().plusDays(convention.firstTrimesterLastDay());
        LocalDate through = last.isBefore(window.end()) ? last : window.end();
        return decide(evidence, FIRST_TRIMESTER, window.dum(), through);
    }

    /** H: sífilis and HIV in the 3º trimestre, from IG 28s0d to the end of the pregnancy. */
    PracticeOutcome thirdTrimester(List<ExamEvidence> evidence, GestationWindow window) {
        LocalDate first = window.dum().plusDays(convention.thirdTrimesterFirstDay());
        return decide(evidence, THIRD_TRIMESTER, first, window.end());
    }

    /** Met when every agent has a record in {@code [from, through]}. */
    private static PracticeOutcome decide(
            List<ExamEvidence> evidence, Set<ExamEvidence.Agent> agents, LocalDate from, LocalDate through) {
        List<Tally.Unit> units = new ArrayList<>();
        for (ExamEvidence.Agent agent : agents) {
            boolean found = false;
            for (ExamEvidence item : evidence) {
                if (item.agents().contains(agent) && C3Dates.within(item.event().date(), from, through)) {
                    units.add(Tally.Unit.of(item.event()));
                    found = true;
                }
            }
            if (!found) {
                return PracticeOutcome.NOT_MET;
            }
        }
        return PracticeOutcome.met(Tally.supports(units));
    }
}
