package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.source.pec.MunicipalIsolationContract;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The preflight, against a fake session that records every call it gets: the order is what is
 * proved here (read-only first, rollback and close before the identity is returned), and that a
 * session that is not read-only is refused before anything else is read. The live PEC is not
 * touched; what only a real server can say is the orchestrator's to run.
 */
class ReadOnlyPecPreflightTest {

    private static final String PASSWORD = "s3cr3t-pa55-word";
    private static final String IBGE = "3541307";
    private static final String ANOTHER_IBGE = "3550308";
    private static final String SERVER_VERSION = "13.7 (Debian 13.7-1.pgdg110+1)";

    private static final Map<String, String> ENVIRONMENT = Map.of(
            "PEC_DB_HOST", "127.0.0.1",
            "PEC_DB_PORT", "15432",
            "PEC_DB_NAME", "esus",
            "PEC_DB_USER", "reader",
            "PEC_SOURCE_ID", "pec-test",
            "PEC_VERSION", "5.5.28",
            "PEC_MUNICIPALITY_IBGE", IBGE);

    private static final List<YearMonth> MONTHS = List.of(YearMonth.of(2026, 4), YearMonth.of(2026, 3));

    private static final String ISOLATION = "isolation ";

    private static ReadOnlyPecPreflight preflight(FakePec pec) {
        return new ReadOnlyPecPreflight(pec::connect, ref -> PASSWORD.toCharArray());
    }

    private static Map<String, String> without(String key) {
        Map<String, String> environment = new HashMap<>(ENVIRONMENT);
        environment.remove(key);
        return environment;
    }

    // ---- what is built to connect

    @Test
    void theUrlNamesTheServerAndHoldsNoCredential() {
        String url = ReadOnlyPecPreflight.url(ENVIRONMENT);

        assertThat(url).isEqualTo("jdbc:postgresql://127.0.0.1:15432/esus");
        assertThat(url).doesNotContain("reader").doesNotContain(PASSWORD);
    }

    @Test
    void theLoginIsReadOnlyFromTheFirstStatementOnWithTheEngineeringStatementBudget() {
        Properties login = ReadOnlyPecPreflight.properties(PASSWORD.toCharArray(), ENVIRONMENT);

        assertThat(login.getProperty("readOnly")).isEqualTo("true");
        assertThat(login.getProperty("options"))
                .isEqualTo("-c default_transaction_read_only=on -c statement_timeout=30000");
        assertThat(login.getProperty("user")).isEqualTo("reader");
        assertThat(login.getProperty("ApplicationName")).isEqualTo(ReadOnlyPecPreflight.APPLICATION_NAME);
    }

    @Test
    void thePasswordIsInTheLoginUnderItsOwnKeyAndNowhereElse() {
        Properties login = ReadOnlyPecPreflight.properties(PASSWORD.toCharArray(), ENVIRONMENT);

        assertThat(login.getProperty("password")).isEqualTo(PASSWORD);
        assertThat(login.stringPropertyNames())
                .filteredOn(name -> !"password".equals(name))
                .allSatisfy(name -> assertThat(login.getProperty(name)).doesNotContain(PASSWORD));
    }

    @Test
    void aPasswordIsAskedOfTheSecretFileOnlyByItsKeyAndZeroedAfterUse() throws SQLException {
        FakePec pec = new FakePec("on").holding(MONTHS.getFirst(), IBGE, 4);
        char[] password = PASSWORD.toCharArray();
        List<String> asked = new ArrayList<>();
        ReadOnlyPecPreflight preflight = new ReadOnlyPecPreflight(pec::connect, ref -> {
            asked.add(ref);
            return password;
        });

        preflight.run(ENVIRONMENT, MONTHS);

        assertThat(asked).containsExactly("PEC_DB_PASSWORD");
        assertThat(password).containsOnly('\0');
    }

    // ---- the order of what happens in the session

    @Test
    void theSessionIsProvedReadOnlyFirstThenRolledBackAndClosedBeforeTheIdentityComesBack() throws SQLException {
        FakePec pec = new FakePec("on").holding(MONTHS.getFirst(), IBGE, 4);

        SourceIdentity identity = preflight(pec).run(ENVIRONMENT, MONTHS);

        assertThat(pec.calls)
                .containsExactly(
                        "connect",
                        "setAutoCommit(false)",
                        "setReadOnly(true)",
                        ReadOnlyPecPreflight.SHOW_READ_ONLY,
                        ReadOnlyPecPreflight.SHOW_VERSION,
                        ISOLATION + "[2026-04-01, 2026-05-01]",
                        "rollback",
                        "close");
        assertThat(identity).isEqualTo(new SourceIdentity("pec-test", "5.5.28", SERVER_VERSION, IBGE, "PEC_DW"));
    }

    @Test
    void theConnectionIsOpenedWithTheUrlAndTheReadOnlyLoginOfTheSecretFile() throws SQLException {
        FakePec pec = new FakePec("on").holding(MONTHS.getFirst(), IBGE, 4);

        preflight(pec).run(ENVIRONMENT, MONTHS);

        assertThat(pec.url).isEqualTo("jdbc:postgresql://127.0.0.1:15432/esus");
        assertThat(pec.login.getProperty("readOnly")).isEqualTo("true");
        assertThat(pec.login.getProperty("options")).contains("default_transaction_read_only=on");
    }

    @Test
    void aSessionThatIsNotReadOnlyIsRefusedBeforeAnythingElseIsReadAndStillRolledBackAndClosed() {
        FakePec pec = new FakePec("off").holding(MONTHS.getFirst(), IBGE, 4);
        ReadOnlyPecPreflight preflight = preflight(pec);

        assertThatThrownBy(() -> preflight.run(ENVIRONMENT, MONTHS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not read-only");

        assertThat(pec.calls)
                .containsExactly(
                        "connect",
                        "setAutoCommit(false)",
                        "setReadOnly(true)",
                        ReadOnlyPecPreflight.SHOW_READ_ONLY,
                        "rollback",
                        "close");
    }

    @Test
    void theIsolationQueryIsTheFrozenOneBoundToTheFirstDayOfTheMonthAndOfTheNext() throws SQLException {
        FakePec pec = new FakePec("on").holding(YearMonth.of(2025, 12), IBGE, 4);

        preflight(pec).run(ENVIRONMENT, List.of(YearMonth.of(2025, 12)));

        assertThat(pec.prepared).containsExactly(MunicipalIsolationContract.QUERY);
        assertThat(pec.bound).containsExactly(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 1));
    }

    // ---- the municipality

    @Test
    void theProbeStopsAtTheFirstMonthTheRegisteredMunicipalityIsIn() throws SQLException {
        FakePec pec = new FakePec("on").holding(YearMonth.of(2026, 3), IBGE, 7);
        List<YearMonth> months = List.of(YearMonth.of(2026, 4), YearMonth.of(2026, 3), YearMonth.of(2026, 2));

        preflight(pec).run(ENVIRONMENT, months);

        assertThat(pec.calls)
                .filteredOn(call -> call.startsWith(ISOLATION))
                .containsExactly(ISOLATION + "[2026-04-01, 2026-05-01]", ISOLATION + "[2026-03-01, 2026-04-01]");
    }

    @Test
    void aMunicipalityAbsentFromEveryProbedMonthIsRefusedAfterTheConnectionIsClosed() {
        FakePec pec = new FakePec("on").holding(YearMonth.of(2026, 4), ANOTHER_IBGE, 900);
        ReadOnlyPecPreflight preflight = preflight(pec);

        assertThatThrownBy(() -> preflight.run(ENVIRONMENT, MONTHS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 months probed");

        assertThat(pec.calls).endsWith("rollback", "close");
        assertThat(pec.calls).filteredOn(call -> call.startsWith(ISOLATION)).hasSize(2);
    }

    @Test
    void anEmptyCountOrAnotherMunicipalityIsNotTheRegisteredOne() {
        FakePec pec = new FakePec("on")
                .holding(MONTHS.getFirst(), IBGE, 0)
                .holding(MONTHS.getFirst(), ANOTHER_IBGE, 31)
                .holding(MONTHS.getLast(), ANOTHER_IBGE, 31);
        ReadOnlyPecPreflight preflight = preflight(pec);

        assertThatThrownBy(() -> preflight.run(ENVIRONMENT, MONTHS)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theCodeThePecHandsBackIsComparedStripped() throws SQLException {
        FakePec pec = new FakePec("on").holding(MONTHS.getFirst(), " " + IBGE + " ", 3);

        assertThat(preflight(pec).run(ENVIRONMENT, MONTHS).municipalityIbge()).isEqualTo(IBGE);
    }

    // ---- what is refused before a connection is made

    @Test
    void aSecretFileWithoutAnEntryTheIdentityNeedsIsRefusedBeforeConnecting() {
        FakePec pec = new FakePec("on");
        ReadOnlyPecPreflight preflight = preflight(pec);

        for (String key : List.of("PEC_SOURCE_ID", "PEC_VERSION", "PEC_MUNICIPALITY_IBGE", "PEC_DB_HOST")) {
            Map<String, String> environment = without(key);
            assertThatThrownBy(() -> preflight.run(environment, MONTHS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(key);
        }
        assertThat(pec.calls).isEmpty();
    }

    @Test
    void aRunWithoutAnyMonthToLookInIsRefusedBeforeConnecting() {
        FakePec pec = new FakePec("on");
        ReadOnlyPecPreflight preflight = preflight(pec);
        List<YearMonth> noMonths = List.of();

        assertThatThrownBy(() -> preflight.run(ENVIRONMENT, noMonths)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pec.calls).isEmpty();
    }

    @Test
    void aPecThatCannotBeReachedIsAnErrorNotAnIdentity() {
        ReadOnlyPecPreflight unreachable = new ReadOnlyPecPreflight(
                (url, login) -> {
                    throw new SQLException("connection refused");
                },
                ref -> PASSWORD.toCharArray());

        assertThatThrownBy(() -> unreachable.run(ENVIRONMENT, MONTHS))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("connection refused");
    }

    // ---- the password

    @Test
    void noRefusalCarriesThePassword() {
        FakePec notReadOnly = new FakePec("off");
        FakePec absent = new FakePec("on");
        ReadOnlyPecPreflight refusingTheSession = preflight(notReadOnly);
        ReadOnlyPecPreflight refusingTheMunicipality = preflight(absent);

        assertThatThrownBy(() -> refusingTheSession.run(ENVIRONMENT, MONTHS)).hasMessageNotContaining(PASSWORD);
        assertThatThrownBy(() -> refusingTheMunicipality.run(ENVIRONMENT, MONTHS))
                .hasMessageNotContaining(PASSWORD);
        assertThat(String.join("|", notReadOnly.calls) + String.join("|", absent.calls))
                .doesNotContain(PASSWORD);
    }

    @Test
    void theSecretFileEntriesTheIdentityIsBuiltFromNeverIncludeThePassword(@TempDir Path directory) throws IOException {
        Path envFile = directory.resolve("pec.env");
        Files.writeString(envFile, """
                # the entries of a PEC secret file
                PEC_DB_HOST=127.0.0.1
                PEC_DB_PORT=15432
                PEC_DB_PASSWORD=%s
                """.formatted(PASSWORD));

        Map<String, String> environment = AcquisitionInputs.environmentOf(envFile);

        assertThat(environment).containsEntry("PEC_DB_HOST", "127.0.0.1").doesNotContainKey("PEC_DB_PASSWORD");
        assertThat(environment.values()).doesNotContain(PASSWORD);
    }

    /** A PEC session that records the calls it gets and answers the few queries the preflight makes. */
    private static final class FakePec {

        private final List<String> calls = new ArrayList<>();
        private final List<String> prepared = new ArrayList<>();
        private final List<Object> bound = new ArrayList<>();
        private final String readOnly;
        private final Map<YearMonth, List<List<Object>>> counts = new HashMap<>();
        private String url;
        private Properties login;

        FakePec(String readOnly) {
            this.readOnly = readOnly;
        }

        /** The month the PEC holds {@code count} atendimentos of {@code ibge} in. */
        FakePec holding(YearMonth month, String ibge, long count) {
            counts.computeIfAbsent(month, unused -> new ArrayList<>()).add(List.of(ibge, count));
            return this;
        }

        Connection connect(String url, Properties login) {
            this.url = url;
            this.login = login;
            calls.add("connect");
            return proxy(Connection.class, this::onConnection);
        }

        private Object onConnection(Object unused, Method method, Object... args) {
            return switch (method.getName()) {
                case "setAutoCommit", "setReadOnly" -> noted(method.getName() + "(" + args[0] + ")");
                case "rollback", "close" -> noted(method.getName());
                case "createStatement" -> proxy(Statement.class, this::onStatement);
                case "prepareStatement" -> {
                    prepared.add((String) args[0]);
                    yield proxy(PreparedStatement.class, this::onPrepared);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private Object onStatement(Object unused, Method method, Object... args) {
            return switch (method.getName()) {
                case "executeQuery" -> answer((String) args[0]);
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private Object onPrepared(Object unused, Method method, Object... args) {
            return switch (method.getName()) {
                case "setObject" -> {
                    bound.add(args[1]);
                    yield null;
                }
                case "executeQuery" -> {
                    LocalDate from = (LocalDate) bound.get(bound.size() - 2);
                    LocalDate to = (LocalDate) bound.getLast();
                    calls.add(ISOLATION + "[" + from + ", " + to + "]");
                    yield rows(counts.getOrDefault(YearMonth.from(from), List.of()));
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private ResultSet answer(String statement) {
            calls.add(statement);
            if (ReadOnlyPecPreflight.SHOW_READ_ONLY.equals(statement)) {
                return rows(List.of(List.of(readOnly)));
            }
            if (ReadOnlyPecPreflight.SHOW_VERSION.equals(statement)) {
                return rows(List.of(List.of(SERVER_VERSION)));
            }
            throw new UnsupportedOperationException(statement);
        }

        private Object noted(String call) {
            calls.add(call);
            return null;
        }

        private static ResultSet rows(List<List<Object>> rows) {
            Iterator<List<Object>> remaining = rows.iterator();
            AtomicReference<List<Object>> current = new AtomicReference<>();
            return proxy(ResultSet.class, (unused, method, args) -> switch (method.getName()) {
                case "next" -> {
                    boolean has = remaining.hasNext();
                    current.set(has ? remaining.next() : null);
                    yield has;
                }
                case "getString" -> String.valueOf(current.get().get((int) args[0] - 1));
                case "getLong" -> ((Number) current.get().get((int) args[0] - 1)).longValue();
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return type.cast(Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(), new Class<?>[] {type}, handler));
        }
    }
}
