package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.pack.c2.PracticeOutcome.Support;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Practice D (item 16 p.2; 24 e MIVDT p.3; Quadro 04 p.6): two home visits by ACS/TACS with motive
 * "recém-nascido" or "criança", "sendo a primeira até os primeiros 30 (trinta) dias de vida e a
 * segunda até os 06 (seis) meses de vida". The eAP tipo 76 exemption is applied by the pack, only
 * when the team type is known.
 */
final class VisitPractice {

    private static final long SIX_MONTHS = 6;
    private static final String MIVDT = "MIVDT";

    private static final List<Reading> READINGS = List.of(
            Reading.DAY_30_INSIDE,
            Reading.ANNIVERSARY_DAY_INSIDE,
            Reading.ANNIVERSARY_NEXT_DAY,
            Reading.SECOND_VISIT_EARLY,
            Reading.SAME_DAY_VISITS,
            Reading.UNCONFIRMED_VISIT);

    private VisitPractice() {}

    private record Visit(LocalDate date, boolean done, Support support) {}

    static PracticeOutcome evaluate(ChildRecords child) {
        List<Visit> visits = visits(child);
        ChildClock clock = child.clock();
        Readings.Verdict verdict = Readings.decide(READINGS, readings -> twoVisits(clock, visits, readings));
        Set<Reading> widest = Set.of(Reading.ANNIVERSARY_DAY_INSIDE, Reading.ANNIVERSARY_NEXT_DAY);
        List<Support> support = new ArrayList<>();
        for (Visit v : visits) {
            if (clock.upToMonths(v.date(), SIX_MONTHS, widest)) {
                support.add(v.support());
            }
        }
        return PracticeOutcome.of("D", verdict, support);
    }

    private static boolean twoVisits(ChildClock clock, List<Visit> visits, Set<Reading> readings) {
        for (int first = 0; first < visits.size(); first++) {
            Visit f = visits.get(first);
            if (counts(f, readings) && clock.withinFirst30Days(f.date(), readings)) {
                for (int second = 0; second < visits.size(); second++) {
                    if (second != first && isSecond(clock, f, visits.get(second), readings)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSecond(ChildClock clock, Visit first, Visit second, Set<Reading> readings) {
        boolean sameDay = second.date().isEqual(first.date());
        return counts(second, readings)
                && !second.date().isBefore(first.date())
                && (!sameDay || readings.contains(Reading.SAME_DAY_VISITS))
                && (readings.contains(Reading.SECOND_VISIT_EARLY) || !clock.withinFirst30Days(second.date(), readings))
                && clock.upToMonths(second.date(), SIX_MONTHS, readings);
    }

    private static boolean counts(Visit visit, Set<Reading> readings) {
        return visit.done() || readings.contains(Reading.UNCONFIRMED_VISIT);
    }

    /** Visits by TACS/ACS with the motive the 24 e requires (AMB-C2-08 iii: other motives never count). */
    private static List<Visit> visits(ChildRecords child) {
        List<Visit> visits = new ArrayList<>();
        for (CanonicalHomeVisit v : child.visits()) {
            LocalDate date = LocalDate.parse(v.visitDate());
            boolean motive = v.reasonCodes().contains(C2Codes.VISIT_REASON_NEWBORN)
                    || v.reasonCodes().contains(C2Codes.VISIT_REASON_CHILD);
            if (motive && C2Codes.VISIT.matches(v.cbo()) && child.inScope(date)) {
                boolean done = C2Codes.VISIT_OUTCOME_DONE.equals(v.outcomeCode());
                visits.add(new Visit(date, done, new Support(v.sourceRef(), date, v.cbo(), v.cnes(), v.ine(), MIVDT)));
            }
        }
        return visits;
    }
}
