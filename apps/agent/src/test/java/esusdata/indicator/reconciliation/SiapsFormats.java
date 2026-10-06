package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The spellings the SIAPS and the product disagree on: the quadrimestre ({@code 2026Q2} against
 * {@code 2026-Q2}), the municipality (6 digits, no check digit, against 7) and the INE (10 digits,
 * zero-padded; the PEC may hand it back without the leading zeros).
 */
public final class SiapsFormats {

    private static final Pattern SIAPS_QUADRIMESTRE = Pattern.compile("(\\d{4})Q([1-3])");
    private static final Pattern LONG_QUADRIMESTRE = Pattern.compile("([1-3])\\D*Quadrimestre/(\\d{4})");
    private static final int IBGE_SIAPS_LENGTH = 6;
    private static final int IBGE_LENGTH = 7;
    private static final int INE_LENGTH = 10;

    private SiapsFormats() {}

    /** {@code 2026Q2} or {@code 2º Quadrimestre/2026}, read as the product's quadrimestre. */
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

    /** The INE as 10 digits, zero-padded on the left. */
    public static String ine(String ine) {
        if (ine == null || !ine.matches("\\d{1," + INE_LENGTH + "}")) {
            throw new IllegalArgumentException("not an INE: " + ine);
        }
        return "0".repeat(INE_LENGTH - ine.length()) + ine;
    }
}
