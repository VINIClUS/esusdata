package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Captures, against a real PEC, what the {@code team} capability's matrix entry has to say (ADR
 * 0031): the {@code signature_fingerprint} of each object it uses, computed by the same probe the
 * application runs, compared with the ones in the packaged matrix; and one run of the frozen query
 * for the municipality given in {@code -Dobservatorio.team-capture.ibge=<7 digits>}, reported only
 * as counts (rows by type source and type code, distinct INEs, states per INE at most), never an
 * INE or a CNES. It writes {@code apps/agent/target/team-capture/} (git-ignored). The session is
 * {@link LivePecInventory}'s: read-only from the login on, behind the same opt-in as the other live
 * tests. It does not change the matrix: the maintainer pastes the fingerprints and approves.
 */
class TeamFingerprintCaptureLiveTest {

    private static final String IBGE_PROPERTY = "observatorio.team-capture.ibge";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void capturesTheFingerprintsAndCountsOfTheTeamCapability() throws Exception {
        String ibge = System.getProperty(IBGE_PROPERTY);
        Assumptions.assumeTrue(
                ibge != null && ibge.matches("\\d{7}"), "Skipping: give -D" + IBGE_PROPERTY + "=<7-digit IBGE code>");
        LivePecInventory session = LivePecInventory.assumeAvailable();
        CapabilityContract contract = CapabilityCatalog.packaged().require(Capabilities.TEAM);
        List<String> lines = new ArrayList<>();
        lines.add("-- team@" + contract.adapterVersion() + " capture, " + Instant.now() + ", read model "
                + contract.readModel());

        try (Connection connection = session.openReadOnly()) {
            captureFingerprints(connection, lines);
            captureCounts(connection, contract, ibge, lines);
            connection.rollback();
        }

        Path output = Path.of("target", "team-capture", "team-capture-" + System.currentTimeMillis() + ".txt");
        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
        assertThat(output).isNotEmptyFile();
    }

    private static void captureFingerprints(Connection connection, List<String> lines) throws Exception {
        JdbcCompatibilityCatalog catalog = new JdbcCompatibilityCatalog();
        lines.add("");
        lines.add("--- signature_fingerprint: object|captured|packaged|MATCH or DIFFERENT ---");
        for (JsonNode object : entry().get("objects_used")) {
            List<String> columns = new ArrayList<>();
            object.get("columns_used").forEach(column -> columns.add(column.asString()));
            String name = object.get("object").asString();
            String captured = catalog.fingerprint(connection, name, columns);
            String packaged = object.get("signature_fingerprint").asString();
            lines.add(
                    name + "|" + captured + "|" + packaged + "|" + (captured.equals(packaged) ? "MATCH" : "DIFFERENT"));
        }
    }

    private static void captureCounts(
            Connection connection, CapabilityContract contract, String ibge, List<String> lines) throws Exception {
        CapabilityQueryReader.Result result = CapabilityQueryReader.read(connection, contract, TeamFixture.binds(ibge));
        Map<String, Integer> byTypeAndSource = new TreeMap<>();
        Map<String, Set<String>> statesByIne = new TreeMap<>();
        for (Map<String, Object> row : result.rows()) {
            byTypeAndSource.merge(row.get("type_source") + "|" + row.get("team_type_code"), 1, Integer::sum);
            statesByIne
                    .computeIfAbsent(String.valueOf(row.get("ine")), ignored -> new TreeSet<>())
                    .add(row.get("source_entity_type") + ":" + row.get("source_record_id"));
        }
        int mostStates = statesByIne.values().stream().mapToInt(Set::size).max().orElse(0);
        lines.add("");
        lines.add("--- frozen query, one municipality: counts only ---");
        lines.add("rows|" + result.rows().size());
        lines.add("distinct_ines|" + statesByIne.size());
        lines.add("max_states_per_ine|" + mostStates);
        lines.add("type_source|team_type_code|rows");
        byTypeAndSource.forEach((key, count) -> lines.add(key + "|" + count));
    }

    private static JsonNode entry() throws IOException {
        try (InputStream in =
                TeamFingerprintCaptureLiveTest.class.getResourceAsStream(PecCompatibilityMatrix.RESOURCE)) {
            for (JsonNode candidate : MAPPER.readTree(in).get("tested_with")) {
                if (Capabilities.TEAM.equals(candidate.get("capability").asString())) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("the matrix has no team entry");
    }
}
