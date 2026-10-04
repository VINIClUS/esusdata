package esusdata.source.pec;

import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.PartRequirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The synthetic DW fixture of the foundation capabilities ({@code fixtures/pec_dw_v2_fixture.sql},
 * ADR 0030) and the parameters its tests read it with. Like C1's fixture, its two municipalities
 * share every surrogate key, so a query only isolates them through {@code tb_dim_municipio.co_ibge}
 * (ENG-37, ENG-38). Its {@link #checksum()} is the {@code fixture_checksum} of the foundation's
 * {@code NOT_TESTED} entries in {@code contracts/compatibility/pec-adapters.json}.
 */
public final class CapabilityFixture {

    public static final Path FILE = Path.of("src/test/resources/fixtures/pec_dw_v2_fixture.sql");

    public static final String MUNICIPALITY_A = "1100015";
    public static final String MUNICIPALITY_B = "3550308";

    /** The golden window: competência 2026-03. */
    public static final LocalDate PERIOD_START = LocalDate.of(2026, 3, 1);

    public static final LocalDate PERIOD_END_EXCLUSIVE = LocalDate.of(2026, 4, 1);

    /** The golden birth range, inclusive at both ends. */
    public static final LocalDate BORN_FROM = LocalDate.of(1990, 1, 1);

    public static final LocalDate BORN_TO = LocalDate.of(2025, 12, 31);

    private CapabilityFixture() {}

    /**
     * Runs the whole fixture in one statement: pgJDBC's simple query protocol splits statements and
     * comments itself (a naive split on {@code ;} breaks on comment text).
     */
    public static void load(Connection connection) throws IOException, SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(FILE));
        }
    }

    /** {@code sha256:<hex>} of the fixture file's bytes, as {@code fixture_checksum} records it. */
    static String checksum() throws IOException {
        try {
            return "sha256:"
                    + HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(FILE)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** The golden parameters of {@code capability} for {@code municipality}. */
    public static CapabilityQueryReader.Binds binds(String capability, String municipality) {
        return binds(municipality, PERIOD_START, PERIOD_END_EXCLUSIVE, BORN_FROM, BORN_TO, codes(capability));
    }

    public static CapabilityQueryReader.Binds binds(
            String municipality,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            LocalDate bornFrom,
            LocalDate bornTo,
            SortedMap<String, List<String>> codes) {
        SortedMap<String, LocalDate> dates = new TreeMap<>();
        dates.put(PartRequirement.BIRTH_DATE_FROM, bornFrom);
        dates.put(PartRequirement.BIRTH_DATE_TO, bornTo);
        return new CapabilityQueryReader.Binds(municipality, periodStart, periodEndExclusive, dates, codes);
    }

    /** The golden code lists of {@code capability}: some codes of the fixture, never all of them. */
    public static SortedMap<String, List<String>> codes(String capability) {
        SortedMap<String, List<String>> codes = new TreeMap<>();
        switch (capability) {
            case Capabilities.IMMUNIZATION_HISTORY ->
                codes.put(Capabilities.IMMUNOBIOLOGICAL_CODES, List.of("42", "67"));
            case Capabilities.EXAM_REQUEST_EVALUATION ->
                codes.put(Capabilities.PROCEDURE_CODES, List.of("0202010503", "ABEX008", "0203010019"));
            case Capabilities.PROCEDURE_PERFORMED ->
                codes.put(Capabilities.PROCEDURE_CODES, List.of("0301040095", "0201020033", "0307020070"));
            case Capabilities.CONDITION_LIST -> {
                codes.put(Capabilities.CIAP_CODES, List.of("T90", "K86", "W78", "ABP022"));
                codes.put(Capabilities.CID_CODES, List.of("E11", "Z34", "K02"));
            }
            default -> {
                // the other capabilities take no code list
            }
        }
        return codes;
    }
}
