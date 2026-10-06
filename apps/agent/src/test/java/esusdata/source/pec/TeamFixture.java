package esusdata.source.pec;

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
 * The synthetic transactional fixture of the {@code team} capability ({@code
 * fixtures/pec_oltp_team_fixture.sql}, ADR 0031) and the binds its tests read it with. It is its own
 * file, not part of the DW fixture, so the foundation's approvals, pinned to the DW fixture's
 * checksum, are untouched. Its {@link #checksum()} is the {@code fixture_checksum} of the team entry
 * of {@code contracts/compatibility/pec-adapters.json}.
 */
public final class TeamFixture {

    public static final Path FILE = Path.of("src/test/resources/fixtures/pec_oltp_team_fixture.sql");

    public static final String MUNICIPALITY_A = "1100015";
    public static final String MUNICIPALITY_B = "3550308";

    /** An IBGE code that is in no row of the fixture. */
    public static final String UNKNOWN_MUNICIPALITY = "5300108";

    private TeamFixture() {}

    /** Runs the whole file in one statement: pgJDBC's simple protocol splits it itself. */
    public static void load(Connection connection) throws IOException, SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(FILE));
        }
    }

    /** {@code sha256:<hex>} of the file's bytes, as {@code fixture_checksum} records it. */
    static String checksum() throws IOException {
        try {
            return "sha256:"
                    + HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(FILE)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** The binds of {@code team}: only the municipality; the window and the lists are not read. */
    public static CapabilityQueryReader.Binds binds(String municipality) {
        SortedMap<String, LocalDate> dates = new TreeMap<>();
        SortedMap<String, List<String>> codes = new TreeMap<>();
        return new CapabilityQueryReader.Binds(
                municipality, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1), dates, codes);
    }
}
