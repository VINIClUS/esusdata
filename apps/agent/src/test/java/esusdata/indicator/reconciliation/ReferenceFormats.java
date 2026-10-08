package esusdata.indicator.reconciliation;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * The spellings the captured reference artifacts agree on, checked once: the 7-digit IBGE code of
 * the municipality, the SIAPS quadrimestre ({@code 2026Q1}), a lowercase hex SHA-256 and a plain
 * decimal. A value that does not look like that is refused where it enters, so an artifact never
 * holds a spelling another artifact would read differently.
 */
final class ReferenceFormats {

    private static final Pattern IBGE = Pattern.compile("\\d{7}");
    private static final Pattern QUADRIMESTRE = Pattern.compile("\\d{4}Q[1-3]");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern PARSER = Pattern.compile("[a-z][a-z0-9-]*@[1-9]\\d*");

    private ReferenceFormats() {}

    /** The 7-digit IBGE code of the municipality (with its check digit), as the PEC spells it. */
    static String ibge7(String value) {
        return require(IBGE, value, "a 7-digit IBGE municipality code");
    }

    /** The SIAPS spelling of a quadrimestre, {@code 2026Q1}. */
    static String quadrimestre(String value) {
        return require(QUADRIMESTRE, value, "a quadrimestre like 2026Q1");
    }

    /** A lowercase hex SHA-256. */
    static String sha256(String value) {
        return require(SHA256, value, "a lowercase hex SHA-256");
    }

    /** The layout a file was read with, e.g. {@code siaps-team-export@1}: a name and a version. */
    static String parserVersion(String value) {
        return require(PARSER, value, "a parser version like siaps-team-export@1");
    }

    private static String require(Pattern pattern, String value, String what) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("not " + what + ": " + value);
        }
        return value;
    }

    /**
     * A non-negative decimal in its canonical scale: no trailing zeros and no exponent, so {@code
     * 21.20}, {@code 21.2} and {@code 100} against {@code 1E+2} compare (and print) alike.
     */
    static BigDecimal decimal(BigDecimal value, String what) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(what + " must be a non-negative number");
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /** The plain string a decimal is written as in the artifacts. */
    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
