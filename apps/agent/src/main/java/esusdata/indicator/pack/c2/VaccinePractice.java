package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.pack.c2.PracticeOutcome.Support;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Practice E (item 16 p.2; 24 g p.3–4; Quadro 05 p.6), as the component × dose matrix of the 24 g
 * "Esquema Primário": a component's doses are the distinct dates on which any listed code containing
 * it was applied (the same dose recorded twice — MIV and transcription — is one, MET-32).
 *
 * <ul>
 *   <li>Grupo 1 (pentavalente): difteria/tétano/pertussis, hepatite B and Hib, 3 doses each, "com
 *       intervalo mínimo de 30 dias entre as doses";
 *   <li>Grupo 2 (VIP): 3 doses, 30 days apart;
 *   <li>Grupo 3 (SCR/SCRV): "Duas doses", none "antes dos 12 meses de vida", no interval stated;
 *   <li>Grupo 4 (pneumocócica): 2 doses, 30 days apart.
 * </ul>
 *
 * <p>A transcription (registro anterior) counts by its application date: the record carries no
 * registration date (AMB-C2-09 v is a standing limitation). The dose field is not read: doses are
 * applications (AMB-C2-09 iv).
 */
final class VaccinePractice {

    private static final int MINIMUM_INTERVAL_DAYS = 30;
    private static final long TWO_YEARS_IN_MONTHS = ChildClock.TWO_YEARS_IN_MONTHS;

    /** "Dose ao nascer" of hepatitis B (AMB-C2-09 ii): a {@code 09} dose in the first 30 days (d ≤ 29). */
    private static final int BIRTH_DOSE_LAST_DAY = 29;

    private static final long TWELVE_MONTHS = 12;
    private static final int PRIMARY_DOSES = 3;
    private static final int TWO_DOSES = 2;

    private static final List<Reading> READINGS = List.of(
            Reading.PER_OCCASION,
            Reading.BIRTH_HEPATITIS_B,
            Reading.SHORT_INTERVAL_INVALIDATES,
            Reading.DOSES_AFTER_TWO_YEARS,
            Reading.VACCINE_CBO_RESTRICTED,
            Reading.ANNIVERSARY_DAY_INSIDE,
            Reading.ANNIVERSARY_NEXT_DAY);

    private VaccinePractice() {}

    private record Dose(LocalDate date, String code, String cbo, Support support) {}

    static PracticeOutcome evaluate(ChildRecords child) {
        List<Dose> doses = doses(child);
        ChildClock clock = child.clock();
        Readings.Verdict verdict = Readings.decide(READINGS, readings -> complete(clock, doses, readings));
        List<Support> support = new ArrayList<>();
        for (Dose d : doses) {
            support.add(d.support());
        }
        return PracticeOutcome.of("E", verdict, support);
    }

    private static boolean complete(ChildClock clock, List<Dose> all, Set<Reading> readings) {
        List<Dose> doses = new ArrayList<>();
        for (Dose d : all) {
            if (admitted(clock, d, readings)) {
                doses.add(d);
            }
        }
        NavigableSet<LocalDate> mmr =
                dates(doses, C2Codes.MMR, d -> clock.fromMonths(d.date(), TWELVE_MONTHS, readings));
        return groupOne(clock, doses, readings)
                && enough(dates(doses, C2Codes.POLIO, d -> true), PRIMARY_DOSES, readings)
                && mmr.size() >= TWO_DOSES
                && enough(dates(doses, C2Codes.PNEUMOCOCCAL, d -> true), TWO_DOSES, readings);
    }

    /** Grupo 1: per component (each of DTP, HepB, Hib on its own dates) or per occasion (AMB-C2-09 i). */
    private static boolean groupOne(ChildClock clock, List<Dose> doses, Set<Reading> readings) {
        NavigableSet<LocalDate> dtp = dates(doses, C2Codes.DTP, d -> true);
        NavigableSet<LocalDate> hepatitisB =
                dates(doses, C2Codes.HEPATITIS_B, d -> !birthDoseSkipped(clock, d, readings));
        NavigableSet<LocalDate> hib = dates(doses, C2Codes.HIB, d -> true);
        if (readings.contains(Reading.PER_OCCASION)) {
            NavigableSet<LocalDate> occasions = new TreeSet<>(dtp);
            occasions.retainAll(hepatitisB);
            occasions.retainAll(hib);
            return enough(occasions, PRIMARY_DOSES, readings);
        }
        return enough(dtp, PRIMARY_DOSES, readings)
                && enough(hepatitisB, PRIMARY_DOSES, readings)
                && enough(hib, PRIMARY_DOSES, readings);
    }

    /** The {@code 09} dose of the first 30 days, left out when the birth dose does not count (AMB-C2-09 ii). */
    private static boolean birthDoseSkipped(ChildClock clock, Dose dose, Set<Reading> readings) {
        return !readings.contains(Reading.BIRTH_HEPATITIS_B)
                && C2Codes.HEPATITIS_B_ONLY.equals(dose.code())
                && clock.day(dose.date()) <= BIRTH_DOSE_LAST_DAY;
    }

    private static boolean admitted(ChildClock clock, Dose dose, Set<Reading> readings) {
        boolean restrictedCbo = readings.contains(Reading.VACCINE_CBO_RESTRICTED)
                && dose.cbo() != null
                && !C2Codes.PROCEDURE.matches(dose.cbo());
        boolean inWindow = readings.contains(Reading.DOSES_AFTER_TWO_YEARS)
                || clock.upToMonths(dose.date(), TWO_YEARS_IN_MONTHS, readings);
        return !restrictedCbo && inWindow;
    }

    /**
     * Whether {@code dates} hold {@code required} doses at least 30 days apart: a dose too close is
     * skipped (earliest-first, which keeps the most doses) or, in the other reading of AMB-C2-09 iii,
     * invalidates the component.
     */
    private static boolean enough(NavigableSet<LocalDate> dates, int required, Set<Reading> readings) {
        int valid = 0;
        LocalDate last = null;
        for (LocalDate date : dates) {
            boolean spaced = last == null || !date.isBefore(last.plusDays(MINIMUM_INTERVAL_DAYS));
            if (spaced) {
                valid++;
                last = date;
            } else if (readings.contains(Reading.SHORT_INTERVAL_INVALIDATES)) {
                return false;
            }
        }
        return valid >= required;
    }

    private static NavigableSet<LocalDate> dates(List<Dose> doses, List<String> codes, Predicate<Dose> filter) {
        NavigableSet<LocalDate> dates = new TreeSet<>();
        for (Dose d : doses) {
            if (codes.contains(d.code()) && filter.test(d)) {
                dates.add(d.date());
            }
        }
        return dates;
    }

    /** The code as the ficha writes it: {@code 9} from a source without the leading zero is {@code 09}. */
    private static String code(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() == 1 ? "0" + trimmed : trimmed;
    }

    private static List<Dose> doses(ChildRecords child) {
        List<Dose> doses = new ArrayList<>();
        for (CanonicalImmunization i : child.doses()) {
            LocalDate date = LocalDate.parse(i.applicationDate());
            String code = code(i.immunobiologicalCode());
            if (C2Codes.IMMUNOBIOLOGICAL_CODES.contains(code) && child.inScope(date)) {
                String model = Boolean.TRUE.equals(i.transcription()) ? "MIV_TRANSCRICAO" : "MIV";
                doses.add(new Dose(
                        date,
                        code,
                        i.cbo(),
                        new PracticeOutcome.Support(i.sourceRef(), date, i.cbo(), i.cnes(), i.ine(), model)));
            }
        }
        return doses;
    }
}
