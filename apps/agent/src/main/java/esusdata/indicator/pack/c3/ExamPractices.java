package esusdata.indicator.pack.c3;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Practices G (sífilis, HIV, hepatites B e C in the 1º trimestre) and H (sífilis and HIV in the 3º
 * trimestre), Quadro 07. The ficha does not define the trimesters (AMB-C3-02): without a documented
 * {@link TrimesterConvention} every record of the pregnancy is "talvez", so the practice is decided
 * only when an agent has no record at all in {@code [DUM, D]} (not met).
 */
final class ExamPractices {

    private static final Set<ExamEvidence.Agent> FIRST_TRIMESTER = EnumSet.allOf(ExamEvidence.Agent.class);
    private static final Set<ExamEvidence.Agent> THIRD_TRIMESTER =
            EnumSet.of(ExamEvidence.Agent.SYPHILIS, ExamEvidence.Agent.HIV);

    private final TrimesterConvention convention;

    /** {@code convention} may be {@code null}: none documented (AMB-C3-02). */
    ExamPractices(TrimesterConvention convention) {
        this.convention = convention;
    }

    /** G: the four agents in the 1º trimestre {@code [DUM, DUM + first]}. */
    PracticeOutcome firstTrimester(List<ExamEvidence> evidence, GestationWindow window) {
        if (convention == null) {
            return decide(evidence, FIRST_TRIMESTER, wholePregnancy(window));
        }
        LocalDate last = window.dum().plusDays(convention.firstTrimesterLastDay());
        return decide(
                evidence,
                FIRST_TRIMESTER,
                e -> C3Dates.within(e.event().date(), window.dum(), last)
                        ? new TallyMark(e.quality(), List.of(e.event()))
                        : null);
    }

    /** H: sífilis and HIV in the 3º trimestre {@code [DUM + third, D)}; the day D is AMB-C3-04. */
    PracticeOutcome thirdTrimester(List<ExamEvidence> evidence, GestationWindow window) {
        if (convention == null) {
            return decide(evidence, THIRD_TRIMESTER, wholePregnancy(window));
        }
        LocalDate first = window.dum().plusDays(convention.thirdTrimesterFirstDay());
        return decide(evidence, THIRD_TRIMESTER, e -> {
            LocalDate date = e.event().date();
            if (!C3Dates.within(date, first, window.end())) {
                return null;
            }
            return new TallyMark(window.phaseOf(date).inPregnancy(e.quality()), List.of(e.event()));
        });
    }

    /** Without a convention: any record in {@code [DUM, D]} is "talvez" (AMB-C3-02 unless its own). */
    private static Function<ExamEvidence, TallyMark> wholePregnancy(GestationWindow window) {
        return e -> window.inPregnancy(e.event().date())
                ? new TallyMark(e.quality() == null ? Ambiguity.AMB_C3_02 : e.quality(), List.of(e.event()))
                : null;
    }

    /**
     * Met when every agent has a certain record in the window; not met when some agent has none,
     * certain or not; otherwise ambiguous, citing the most specific ambiguity found.
     */
    private static PracticeOutcome decide(
            List<ExamEvidence> evidence, Set<ExamEvidence.Agent> agents, Function<ExamEvidence, TallyMark> window) {
        List<TallyMark> all = new ArrayList<>();
        List<TallyMark> certain = new ArrayList<>();
        List<Ambiguity> undecided = new ArrayList<>();
        for (ExamEvidence.Agent agent : agents) {
            List<TallyMark> marks = new ArrayList<>();
            for (ExamEvidence item : evidence) {
                TallyMark mark = item.agents().contains(agent) ? window.apply(item) : null;
                if (mark != null) {
                    marks.add(mark);
                }
            }
            if (marks.isEmpty()) {
                return PracticeOutcome.NOT_MET;
            }
            all.addAll(marks);
            List<TallyMark> sure = marks.stream().filter(TallyMark::certain).toList();
            certain.addAll(sure);
            if (sure.isEmpty()) {
                marks.forEach(m -> undecided.add(m.ambiguity()));
            }
        }
        if (undecided.isEmpty()) {
            return PracticeOutcome.met(Tally.supports(certain));
        }
        Ambiguity cited = undecided.stream()
                .filter(a -> a != Ambiguity.AMB_C3_02)
                .findFirst()
                .orElse(Ambiguity.AMB_C3_02);
        return PracticeOutcome.ambiguous(cited, Tally.supports(all));
    }
}
