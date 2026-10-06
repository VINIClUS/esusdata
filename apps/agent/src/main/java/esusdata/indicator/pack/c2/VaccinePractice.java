package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.pack.c2.PracticeOutcome.Support;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
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
 * <p>Intervals count application dates, and so do the 12-month limit of SCR/SCRV and every other
 * limit, also for a transcription (AMB-C2-09 v). Decided readings of the record of decisions C2:
 * doses are counted per component (AMB-C2-09 i); the hepatitis B dose at birth counts (ii); a dose
 * closer than 30 days is discarded (iii); the dose field is not read — doses are applications
 * (iv); a dose after the second birthday counts when applied by the end of the competência and
 * known on the cutoff (AMB-C2-10); the professional's CBO does not restrict a dose (AMB-C2-11).
 */
final class VaccinePractice {

    private static final int MINIMUM_INTERVAL_DAYS = 30;
    private static final long TWELVE_MONTHS = 12;
    private static final int PRIMARY_DOSES = 3;
    private static final int TWO_DOSES = 2;

    private VaccinePractice() {}

    /** {@code date} is the application: every limit of the practice uses it (AMB-C2-09 v). */
    private record Dose(LocalDate date, String code, Support support) {}

    static PracticeOutcome evaluate(ChildRecords child) {
        List<Dose> doses = doses(child);
        List<Support> support = new ArrayList<>();
        for (Dose d : doses) {
            support.add(d.support());
        }
        return PracticeOutcome.of("E", complete(child.clock(), doses), support);
    }

    private static boolean complete(ChildClock clock, List<Dose> doses) {
        NavigableSet<LocalDate> mmr = dates(doses, C2Codes.MMR, d -> clock.fromMonths(d.date(), TWELVE_MONTHS));
        return enough(dates(doses, C2Codes.DTP, d -> true), PRIMARY_DOSES)
                && enough(dates(doses, C2Codes.HEPATITIS_B, d -> true), PRIMARY_DOSES)
                && enough(dates(doses, C2Codes.HIB, d -> true), PRIMARY_DOSES)
                && enough(dates(doses, C2Codes.POLIO, d -> true), PRIMARY_DOSES)
                && mmr.size() >= TWO_DOSES
                && enough(dates(doses, C2Codes.PNEUMOCOCCAL, d -> true), TWO_DOSES);
    }

    /**
     * Whether {@code dates} hold {@code required} doses at least 30 days apart: a dose too close to
     * the last valid one is discarded and the next ones count from that last valid dose (AMB-C2-09 iii).
     */
    private static boolean enough(NavigableSet<LocalDate> dates, int required) {
        int valid = 0;
        LocalDate last = null;
        for (LocalDate date : dates) {
            if (last == null || !date.isBefore(last.plusDays(MINIMUM_INTERVAL_DAYS))) {
                valid++;
                last = date;
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

    private static List<Dose> doses(ChildRecords child) {
        List<Dose> doses = new ArrayList<>();
        for (CanonicalImmunization i : child.doses()) {
            LocalDate date = LocalDate.parse(i.applicationDate());
            boolean transcription = Boolean.TRUE.equals(i.transcription());
            LocalDate registered =
                    transcription && i.registrationDate() != null ? LocalDate.parse(i.registrationDate()) : date;
            String code = C2Codes.HEPATITIS_B_ONLY.equals("0" + i.immunobiologicalCode())
                    ? C2Codes.HEPATITIS_B_ONLY
                    : i.immunobiologicalCode();
            // A transcription recorded after the cutoff was not known on the cutoff.
            boolean known = child.inScope(date) && !registered.isAfter(child.cutoff());
            if (C2Codes.IMMUNOBIOLOGICAL_CODES.contains(code) && known) {
                String model = transcription ? "MIV_TRANSCRICAO" : "MIV";
                doses.add(new Dose(
                        date,
                        code,
                        new PracticeOutcome.Support(i.sourceRef(), date, i.cbo(), i.cnes(), i.ine(), model)));
            }
        }
        return doses;
    }
}
