package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.pack.c2.PracticeOutcome.Support;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Practices A and B (item 16 p.2; Quadro 02 p.5): consultations by médica(o) or enfermeira(o) in
 * the individual-encounter model (MIAI) whose evaluated problems include puericultura (CIAP-2 A98 or
 * CID-10 Z001, AMB-C2-05). Decided readings: a home encounter is presential and a consultation
 * (AMB-C2-04); an encounter is remote only by a marker — participation type 3–7 or the teleconsult
 * procedure {@code 03.01.01.025-0} of the same day, CBO, CNES and INE (LACUNA-L3); a procedure alone
 * is never a consultation (AMB-C2-06); two distinct encounters on one day are two consultations
 * (AMB-C2-15); an encounter of a team whose known type is not 70 or 76 does not count (AMB-C2-11).
 */
final class ConsultPractices {

    private static final int REQUIRED_CONSULTS = 9;
    private static final long TWO_YEARS_IN_MONTHS = ChildClock.TWO_YEARS_IN_MONTHS;
    private static final String MIAI = C2Codes.MIAI;
    private static final Pattern CODE_PUNCTUATION = Pattern.compile("[.\\-\\s]");

    private ConsultPractices() {}

    /** One MIAI consultation; {@code key} collapses the same consultation recorded twice (MET-32). */
    private record Consult(LocalDate date, String key, boolean remote, Support support) {}

    /** A — "Ter a 1ª consulta presencial … até o 30º dia de vida." */
    static PracticeOutcome firstPresentialConsult(ChildRecords child) {
        List<Support> support = new ArrayList<>();
        for (Consult c : consults(child)) {
            if (!c.remote() && child.clock().withinFirst30Days(c.date())) {
                support.add(c.support());
            }
        }
        return PracticeOutcome.of("A", !support.isEmpty(), support);
    }

    /** B — "Ter pelo menos 09 (nove) consultas presenciais ou remotas … até dois anos de vida." */
    static PracticeOutcome nineConsults(ChildRecords child) {
        Set<String> keys = new HashSet<>();
        List<Support> support = new ArrayList<>();
        for (Consult c : consults(child)) {
            if (child.clock().upToMonths(c.date(), TWO_YEARS_IN_MONTHS)) {
                keys.add(c.key());
                support.add(c.support());
            }
        }
        return PracticeOutcome.of("B", keys.size() >= REQUIRED_CONSULTS, support);
    }

    private static List<Consult> consults(ChildRecords child) {
        List<Consult> consults = new ArrayList<>();
        for (CanonicalCareEvent e : child.encounters()) {
            LocalDate date = LocalDate.parse(e.careDate());
            if (C2Codes.INDIVIDUAL_FORM.equals(e.form())
                    && C2Codes.CONSULT.matches(e.cbo())
                    && child.inScope(date)
                    && childCare(e)
                    && consideredTeam(child, e)) {
                boolean home = C2Codes.HOME_CARE_LOCATION.equals(e.careLocationCode());
                String key = date + "|" + normalizedCbo(e.cbo()) + "|" + e.cnes() + "|" + e.ine();
                String model = home ? MIAI + "_DOMICILIAR" : MIAI;
                consults.add(new Consult(
                        date,
                        key,
                        Boolean.TRUE.equals(e.remote()) || teleconsultMarker(child, e, date),
                        new Support(e.sourceRef(), date, e.cbo(), e.cnes(), e.ine(), model)));
            }
        }
        return consults;
    }

    /** Puericultura among the problems evaluated: CIAP-2 A98 or CID-10 Z001, ignoring case and punctuation (AMB-C2-05). */
    private static boolean childCare(CanonicalCareEvent e) {
        return e.ciapCodes().stream().anyMatch(c -> C2Codes.PUERICULTURE_CIAP.equals(normalizedCode(c)))
                || e.cidCodes().stream().anyMatch(c -> C2Codes.PUERICULTURE_CID.equals(normalizedCode(c)));
    }

    private static String normalizedCode(String code) {
        return code == null ? "" : CODE_PUNCTUATION.matcher(code).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /** The encounter's team counts unless its type is known and neither eSF 70 nor eAP 76 (AMB-C2-11). */
    private static boolean consideredTeam(ChildRecords child, CanonicalCareEvent e) {
        String type = e.ine() == null ? null : child.teamTypes().get(e.ine());
        return type == null || C2Codes.CONSIDERED_TEAM_TYPES.contains(type);
    }

    /** A performed {@code 03.01.01.025-0} the same day by the same CBO, CNES and INE marks the encounter remote (LACUNA-L3). */
    private static boolean teleconsultMarker(ChildRecords child, CanonicalCareEvent e, LocalDate date) {
        for (CanonicalProcedureEvent p : child.procedures()) {
            if (C2Codes.TELECONSULT_SIGTAP.equals(p.sigtapCode())
                    && C2Codes.MIP.equals(p.origin())
                    && C2Codes.PERFORMED.equals(p.stage())
                    && date.equals(LocalDate.parse(p.eventDate()))
                    && Objects.equals(normalizedCbo(p.cbo()), normalizedCbo(e.cbo()))
                    && Objects.equals(p.cnes(), e.cnes())
                    && Objects.equals(p.ine(), e.ine())) {
                return true;
            }
        }
        return false;
    }

    /** CBO as text without punctuation, so "2251-42" and "225142" are one professional (MET-32). */
    private static String normalizedCbo(String cbo) {
        return cbo == null ? null : cbo.replaceAll("[-.\\s]", "");
    }
}
