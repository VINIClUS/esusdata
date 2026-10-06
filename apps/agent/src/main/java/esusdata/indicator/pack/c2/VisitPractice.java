package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.pack.c2.PracticeOutcome.Support;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Practice D (item 16 p.2; 24 e MIVDT p.3; Quadro 04 p.6): two home visits by ACS/TACS with motive
 * "recém-nascido" or "criança", "sendo a primeira até os primeiros 30 (trinta) dias de vida e a
 * segunda até os 06 (seis) meses de vida". Decided readings (AMB-C2-08): only a visit whose outcome
 * is "realizada" counts, and the second is after the 30th day (so it is never the first's duplicate
 * on the same day). The eAP tipo 76 exemption is applied by the pack, only when the team type is
 * known.
 */
final class VisitPractice {

    private static final long SIX_MONTHS = 6;
    private static final String MIVDT = C2Codes.MIVDT;

    private VisitPractice() {}

    private record Visit(LocalDate date, Support support) {}

    static PracticeOutcome evaluate(ChildRecords child) {
        ChildClock clock = child.clock();
        List<Visit> visits = visits(child);
        boolean first = false;
        boolean second = false;
        List<Support> support = new ArrayList<>();
        for (Visit v : visits) {
            first |= clock.withinFirst30Days(v.date());
            boolean afterFirst30 = clock.day(v.date()) > ChildClock.LAST_DAY_OF_FIRST_30;
            second |= afterFirst30 && clock.upToMonths(v.date(), SIX_MONTHS);
            if (clock.upToMonths(v.date(), SIX_MONTHS)) {
                support.add(v.support());
            }
        }
        return PracticeOutcome.of("D", first && second, support);
    }

    /** Completed visits by TACS/ACS with the motive the 24 e requires (AMB-C2-08 iii and iv). */
    private static List<Visit> visits(ChildRecords child) {
        List<Visit> visits = new ArrayList<>();
        for (CanonicalHomeVisit v : child.visits()) {
            LocalDate date = LocalDate.parse(v.visitDate());
            boolean motive = v.reasonCodes().contains(C2Codes.VISIT_REASON_NEWBORN)
                    || v.reasonCodes().contains(C2Codes.VISIT_REASON_CHILD);
            boolean done = C2Codes.VISIT_OUTCOME_DONE.equals(v.outcomeCode());
            if (motive && done && C2Codes.VISIT.matches(v.cbo()) && child.inScope(date)) {
                visits.add(new Visit(date, new Support(v.sourceRef(), date, v.cbo(), v.cnes(), v.ine(), MIVDT)));
            }
        }
        return visits;
    }
}
