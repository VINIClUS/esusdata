package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The spellings the SIAPS and the product disagree on: the quadrimestre ({@code 2026Q2} against
 * {@code 2026-Q2}, and {@code Q2/26} in the official team export), the municipality (6 digits, no
 * check digit, against 7) and the INE (10 digits, zero-padded; the PEC may hand it back without the
 * leading zeros).
 */
public final class SiapsFormats {

    private static final Pattern SIAPS_QUADRIMESTRE = Pattern.compile("(\\d{4})Q([1-3])");
    private static final Pattern LONG_QUADRIMESTRE = Pattern.compile("([1-3])\\D*Quadrimestre/(\\d{4})");
    private static final Pattern EXPORT_QUADRIMESTRE = Pattern.compile("Q([1-3])/(\\d{2})");
    private static final int CENTURY = 2000;
    private static final int IBGE_SIAPS_LENGTH = 6;
    private static final int IBGE_LENGTH = 7;
    private static final int INE_LENGTH = 10;
    private static final int UF_ENTRY = 4;

    /** The IBGE state codes and their two letters, four characters each. */
    private static final String UF_CODES =
            "11RO12AC13AM14RR15PA16AP17TO21MA22PI23CE24RN25PB26PE27AL28SE29BA31MG32ES33RJ"
                    + "35SP41PR42SC43RS50MS51MT52GO53DF";

    private SiapsFormats() {}

    /**
     * {@code 2026Q2}, {@code 2º Quadrimestre/2026} or the official export's {@code Q2/26} (a
     * two-digit year, read as 20yy), read as the product's quadrimestre.
     */
    public static Quadrimestre quadrimestre(String text) {
        String value = text == null ? "" : text.strip();
        Matcher plain = SIAPS_QUADRIMESTRE.matcher(value);
        if (plain.matches()) {
            return new Quadrimestre(Integer.parseInt(plain.group(1)), Integer.parseInt(plain.group(2)));
        }
        Matcher longForm = LONG_QUADRIMESTRE.matcher(value);
        if (longForm.matches()) {
            return new Quadrimestre(Integer.parseInt(longForm.group(2)), Integer.parseInt(longForm.group(1)));
        }
        Matcher export = EXPORT_QUADRIMESTRE.matcher(value);
        if (export.matches()) {
            return new Quadrimestre(CENTURY + Integer.parseInt(export.group(2)), Integer.parseInt(export.group(1)));
        }
        throw new IllegalArgumentException("unknown quadrimestre spelling: " + text);
    }

    /** The SIAPS spelling, {@code 2026Q2}. */
    public static String quadrimestre(Quadrimestre quadrimestre) {
        return quadrimestre.year() + "Q" + quadrimestre.index();
    }

    /** The quadrimestre after this one. */
    public static Quadrimestre next(Quadrimestre quadrimestre) {
        YearMonth after = quadrimestre.lastMonth().plusMonths(1);
        return Quadrimestre.of(after);
    }

    /** The SIAPS code of a municipality: the 7-digit IBGE code without its check digit. */
    public static String ibgeOfSiaps(String ibge) {
        if (ibge != null && ibge.matches("\\d{" + IBGE_LENGTH + "}")) {
            return ibge.substring(0, IBGE_SIAPS_LENGTH);
        }
        if (ibge != null && ibge.matches("\\d{" + IBGE_SIAPS_LENGTH + "}")) {
            return ibge;
        }
        throw new IllegalArgumentException("not a municipality code: " + ibge);
    }

    /**
     * The two-letter code of the state of a municipality, {@code SP}: the first two digits of its
     * IBGE code are the state's.
     *
     * @throws IllegalArgumentException when they are not those of a state
     */
    public static String uf(String ibge) {
        String siaps = ibgeOfSiaps(ibge);
        for (int at = 0; at < UF_CODES.length(); at += UF_ENTRY) {
            if (UF_CODES.startsWith(siaps.substring(0, 2), at)) {
                return UF_CODES.substring(at + 2, at + UF_ENTRY);
            }
        }
        throw new IllegalArgumentException("not the IBGE code of a municipality of a state: " + ibge);
    }

    /** The INE as 10 digits, zero-padded on the left. */
    public static String ine(String ine) {
        if (ine == null || !ine.matches("\\d{1," + INE_LENGTH + "}")) {
            throw new IllegalArgumentException("not an INE: " + ine);
        }
        return "0".repeat(INE_LENGTH - ine.length()) + ine;
    }
}
