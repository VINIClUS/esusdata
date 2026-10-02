package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.pack.c2.PracticeOutcome.Support;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Practices A and B (item 16 p.2; Quadro 02 p.5): consultations by médica(o) or enfermeira(o) in
 * the individual-encounter model (MIAI). "Puericultura" cannot be told apart in the DW (gap L7), so
 * it is not filtered — a standing limitation of the pack.
 */
final class ConsultPractices {

    private static final int REQUIRED_CONSULTS = 9;
    private static final long TWO_YEARS_IN_MONTHS = ChildClock.TWO_YEARS_IN_MONTHS;
    private static final String MIAI = C2Codes.MIAI;
    private static final String MIP = C2Codes.MIP;

    /**
     * Only the individual-encounter model (Quadro 02: MIAI); a home-care record of another model
     * ({@code form} HOME) is not it. A MIAI encounter at home (local de atendimento 4) is AMB-C2-04.
     */
    private static final String INDIVIDUAL = "INDIVIDUAL";

    private static final List<Reading> FIRST_CONSULT_READINGS = List.of(
            Reading.DAY_30_INSIDE,
            Reading.HOME_CARE_COUNTS,
            Reading.UNKNOWN_MODALITY_PRESENTIAL,
            Reading.PROCEDURE_ONLY_CONSULT);

    private static final List<Reading> NINE_CONSULTS_READINGS = List.of(
            Reading.ANNIVERSARY_DAY_INSIDE,
            Reading.ANNIVERSARY_NEXT_DAY,
            Reading.HOME_CARE_COUNTS,
            Reading.PROCEDURE_ONLY_CONSULT,
            Reading.SAME_DAY_CONSULTS);

    private ConsultPractices() {}

    /** One MIAI consultation; {@code key} collapses the same consultation recorded twice (MET-32). */
    private record Consult(LocalDate date, String key, boolean home, Boolean remote, Support support) {}

    /**
     * A teleconsultation or child-development code in the MIP (AMB-C2-06); {@code sameDayConsult}
     * when a MIAI consultation exists that day, so B does not count the same visit twice.
     */
    private record ProcedureConsult(LocalDate date, String code, boolean sameDayConsult, Support support) {}

    /** A — "Ter a 1ª consulta presencial … até o 30º dia de vida." */
    static PracticeOutcome firstPresentialConsult(ChildRecords child) {
        List<Consult> consults = consults(child);
        List<ProcedureConsult> procedureOnly = procedureOnly(child, consults);
        ChildClock clock = child.clock();
        Readings.Verdict verdict = Readings.decide(
                FIRST_CONSULT_READINGS, readings -> firstConsultSeen(clock, consults, procedureOnly, readings));
        Set<Reading> widest = Set.of(Reading.DAY_30_INSIDE);
        List<Support> support = new ArrayList<>();
        for (Consult c : consults) {
            if (!Boolean.TRUE.equals(c.remote()) && clock.withinFirst30Days(c.date(), widest)) {
                support.add(c.support());
            }
        }
        for (ProcedureConsult p : procedureOnly) {
            if (C2Codes.CHILD_DEVELOPMENT_SIGTAP.equals(p.code()) && clock.withinFirst30Days(p.date(), widest)) {
                support.add(p.support());
            }
        }
        return PracticeOutcome.of("A", verdict, support);
    }

    /** B — "Ter pelo menos 09 (nove) consultas presenciais ou remotas … até dois anos de vida." */
    static PracticeOutcome nineConsults(ChildRecords child) {
        List<Consult> consults = consults(child);
        List<ProcedureConsult> procedureOnly = procedureOnly(child, consults);
        ChildClock clock = child.clock();
        Readings.Verdict verdict = Readings.decide(
                NINE_CONSULTS_READINGS,
                readings -> consultCount(clock, consults, procedureOnly, readings) >= REQUIRED_CONSULTS);
        Set<Reading> widest = Set.of(Reading.ANNIVERSARY_DAY_INSIDE, Reading.ANNIVERSARY_NEXT_DAY);
        List<Support> support = new ArrayList<>();
        for (Consult c : consults) {
            if (clock.upToMonths(c.date(), TWO_YEARS_IN_MONTHS, widest)) {
                support.add(c.support());
            }
        }
        for (ProcedureConsult p : procedureOnly) {
            if (clock.upToMonths(p.date(), TWO_YEARS_IN_MONTHS, widest)) {
                support.add(p.support());
            }
        }
        return PracticeOutcome.of("B", verdict, support);
    }

    private static boolean firstConsultSeen(
            ChildClock clock, List<Consult> consults, List<ProcedureConsult> procedureOnly, Set<Reading> readings) {
        for (Consult c : consults) {
            if (clock.withinFirst30Days(c.date(), readings) && presential(c, readings)) {
                return true;
            }
        }
        if (!readings.contains(Reading.PROCEDURE_ONLY_CONSULT)) {
            return false;
        }
        for (ProcedureConsult p : procedureOnly) {
            if (C2Codes.CHILD_DEVELOPMENT_SIGTAP.equals(p.code()) && clock.withinFirst30Days(p.date(), readings)) {
                return true;
            }
        }
        return false;
    }

    /** Distinct consultations (or distinct days, the strict reading of AMB-C2-15) before the 2nd birthday. */
    private static int consultCount(
            ChildClock clock, List<Consult> consults, List<ProcedureConsult> procedureOnly, Set<Reading> readings) {
        Set<String> keys = new HashSet<>();
        Set<LocalDate> days = new HashSet<>();
        for (Consult c : consults) {
            if (clock.upToMonths(c.date(), TWO_YEARS_IN_MONTHS, readings)
                    && (!c.home() || readings.contains(Reading.HOME_CARE_COUNTS))) {
                keys.add(c.key());
                days.add(c.date());
            }
        }
        if (readings.contains(Reading.PROCEDURE_ONLY_CONSULT)) {
            for (ProcedureConsult p : procedureOnly) {
                if (!p.sameDayConsult() && clock.upToMonths(p.date(), TWO_YEARS_IN_MONTHS, readings)) {
                    keys.add(MIP + "|" + p.date());
                    days.add(p.date());
                }
            }
        }
        return readings.contains(Reading.SAME_DAY_CONSULTS) ? keys.size() : days.size();
    }

    private static boolean presential(Consult consult, Set<Reading> readings) {
        if (Boolean.TRUE.equals(consult.remote())) {
            return false;
        }
        if (consult.home()) {
            return readings.contains(Reading.HOME_CARE_COUNTS);
        }
        return consult.remote() != null || readings.contains(Reading.UNKNOWN_MODALITY_PRESENTIAL);
    }

    private static List<Consult> consults(ChildRecords child) {
        List<Consult> consults = new ArrayList<>();
        for (CanonicalCareEvent e : child.encounters()) {
            LocalDate date = LocalDate.parse(e.careDate());
            if (INDIVIDUAL.equals(e.form()) && C2Codes.CONSULT.matches(e.cbo()) && child.inScope(date)) {
                boolean home = C2Codes.HOME_CARE_LOCATION.equals(e.careLocationCode());
                String key = date + "|" + normalizedCbo(e.cbo()) + "|" + e.cnes() + "|" + e.ine();
                String model = home ? MIAI + "_DOMICILIAR" : MIAI;
                consults.add(new Consult(
                        date,
                        key,
                        home,
                        e.remote(),
                        new Support(e.sourceRef(), date, e.cbo(), e.cnes(), e.ine(), model)));
            }
        }
        return consults;
    }

    /** CBO as text without punctuation, so "2251-42" and "225142" are one professional (MET-32). */
    private static String normalizedCbo(String cbo) {
        return cbo == null ? null : cbo.replaceAll("[-.\\s]", "");
    }

    private static List<ProcedureConsult> procedureOnly(ChildRecords child, List<Consult> consults) {
        Set<LocalDate> consultDays = new HashSet<>();
        for (Consult c : consults) {
            consultDays.add(c.date());
        }
        List<ProcedureConsult> list = new ArrayList<>();
        for (CanonicalProcedureEvent p : child.procedures()) {
            LocalDate date = LocalDate.parse(p.eventDate());
            boolean code = C2Codes.TELECONSULT_SIGTAP.equals(p.sigtapCode())
                    || C2Codes.CHILD_DEVELOPMENT_SIGTAP.equals(p.sigtapCode());
            if (code && MIP.equals(p.origin()) && child.inScope(date)) {
                list.add(new ProcedureConsult(
                        date,
                        p.sigtapCode(),
                        consultDays.contains(date),
                        new Support(p.sourceRef(), date, p.cbo(), p.cnes(), p.ine(), MIP)));
            }
        }
        return list;
    }
}
