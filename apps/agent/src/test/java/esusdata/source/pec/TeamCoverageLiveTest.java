package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamScope;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Coverage of the team type over the INEs that carry an active registration, for one competência,
 * against a real PEC (decision records C1–C7, "Regra de tipo de equipe": L1 closes only with proven
 * coverage). Two reads, both inside a session that is read-only from the login on ({@link
 * LivePecInventory}), behind the same opt-in as the other live tests:
 *
 * <ol>
 *   <li>the frozen {@code team} query for the municipality, as the application runs it;
 *   <li>one aggregate over the DW registrations: per INE, how many people whose latest registration
 *       version up to the competência's last day is not inactive and not refused link to it. No person
 *       key, no INE and no CNES leaves the database or reaches the output.
 * </ol>
 *
 * It applies {@link TeamScope} to each INE and writes, to {@code apps/agent/target/team-coverage/}
 * (git-ignored), only counts, those from 1 to 9 as {@code <10}. Give the municipality with {@code
 * -Dobservatorio.team-coverage.ibge=<7 digits>} and the competência with {@code
 * -Dobservatorio.team-coverage.competencia=2026-08}.
 */
class TeamCoverageLiveTest {

    private static final String IBGE_PROPERTY = "observatorio.team-coverage.ibge";
    private static final String COMPETENCIA_PROPERTY = "observatorio.team-coverage.competencia";

    private static final String LINKS_BY_INE = """
            WITH mun AS (
                SELECT m.co_seq_dim_municipio FROM public.tb_dim_municipio m WHERE CAST(m.co_ibge AS text) = ?
            ),
            latest AS (
                SELECT DISTINCT ON (c.co_fat_cidadao_pec)
                       c.co_fat_cidadao_pec,
                       NULLIF(btrim(CAST(eq.nu_ine AS text)), '-') AS ine,
                       CAST(c.st_ficha_inativa AS text) IN ('1', 'true') AS inactive,
                       CAST(c.st_recusa_cadastro AS text) IN ('1', 'true') AS refused
                  FROM public.tb_fat_cad_individual c
                  JOIN mun ON mun.co_seq_dim_municipio = c.co_dim_municipio
                  JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = c.co_dim_tempo
                  LEFT JOIN public.tb_dim_equipe eq ON eq.co_seq_dim_equipe = c.co_dim_equipe
                 WHERE c.co_fat_cidadao_pec IS NOT NULL
                   AND t.dt_registro <= ?
                 ORDER BY c.co_fat_cidadao_pec, t.dt_registro DESC NULLS LAST, c.co_seq_fat_cad_individual DESC
            )
            SELECT ine, count(*) AS people
              FROM latest
             WHERE NOT inactive AND NOT refused
             GROUP BY ine
            """;

    @Test
    void countsTheCoverageOfTheTeamTypeOverTheLinkedInesOfOneCompetencia() throws Exception {
        String ibge = System.getProperty(IBGE_PROPERTY);
        String competencia = System.getProperty(COMPETENCIA_PROPERTY);
        Assumptions.assumeTrue(
                ibge != null && ibge.matches("\\d{7}") && competencia != null && competencia.matches("\\d{4}-\\d{2}"),
                "Skipping: give -D" + IBGE_PROPERTY + "=<7 digits> and -D" + COMPETENCIA_PROPERTY + "=<YYYY-MM>");
        LivePecInventory session = LivePecInventory.assumeAvailable();
        LocalDate end = YearMonth.parse(competencia).atEndOfMonth();
        CapabilityContract contract = CapabilityCatalog.packaged().require(Capabilities.TEAM);

        List<CanonicalTeam> teams = new ArrayList<>();
        Map<String, Long> peopleByIne = new java.util.TreeMap<>();
        try (Connection connection = session.openReadOnly()) {
            CapabilityQueryReader.Result result =
                    CapabilityQueryReader.read(connection, contract, TeamFixture.binds(ibge));
            for (Map<String, Object> row : result.rows()) {
                teams.add(team(row));
            }
            try (PreparedStatement statement = connection.prepareStatement(LINKS_BY_INE)) {
                statement.setString(1, ibge);
                statement.setDate(2, Date.valueOf(end));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String ine = rs.getString("ine");
                        peopleByIne.merge(ine == null ? "" : ine, rs.getLong("people"), Long::sum);
                    }
                }
            }
            connection.rollback();
        }

        TeamScope scope = TeamScope.of(teams, end);
        Map<TeamScope.Verdict, long[]> byVerdict = new EnumMap<>(TeamScope.Verdict.class);
        for (TeamScope.Verdict verdict : TeamScope.Verdict.values()) {
            byVerdict.put(verdict, new long[2]);
        }
        long withoutIne = 0;
        for (Map.Entry<String, Long> link : peopleByIne.entrySet()) {
            if (link.getKey().isEmpty()) {
                withoutIne += link.getValue();
                continue;
            }
            long[] counts = byVerdict.get(scope.decide(link.getKey()).verdict());
            counts[0]++;
            counts[1] += link.getValue();
        }
        long linkedInes =
                peopleByIne.keySet().stream().filter(k -> !k.isEmpty()).count();
        long people = peopleByIne.values().stream().mapToLong(Long::longValue).sum();

        List<String> lines = new ArrayList<>();
        lines.add("-- team coverage, competencia " + competencia + ", last day " + end + ", " + Instant.now());
        lines.add("team_rows_read|" + mask(teams.size()));
        lines.add("distinct_team_ines_read|"
                + mask(teams.stream().map(CanonicalTeam::ine).distinct().count()));
        lines.add("ines_with_active_registrations|" + mask(linkedInes));
        lines.add("people_with_active_registrations|" + mask(people));
        lines.add("people_without_ine|" + mask(withoutIne));
        lines.add("verdict|ines|people");
        byVerdict.forEach((verdict, counts) -> lines.add(verdict + "|" + mask(counts[0]) + "|" + mask(counts[1])));

        Path output = Path.of("target", "team-coverage", "team-coverage-" + competencia + ".txt");
        Files.createDirectories(output.getParent());
        Files.write(output, lines, StandardCharsets.UTF_8);
        assertThat(output).isNotEmptyFile();
    }

    private static CanonicalTeam team(Map<String, Object> row) {
        return new CanonicalTeam(
                new SourceRef("pec-coverage", String.valueOf(row.get("source_entity_type")), "x"),
                String.valueOf(row.get("municipality_ibge")),
                String.valueOf(row.get("ine")),
                row.get("cnes") == null ? null : String.valueOf(row.get("cnes")),
                String.valueOf(row.get("team_type_code")),
                null,
                row.get("valid_from") == null ? null : String.valueOf(row.get("valid_from")),
                row.get("valid_to") == null ? null : String.valueOf(row.get("valid_to")),
                String.valueOf(row.get("type_source")));
    }

    /** Small counts never leave: 1 to 9 print as {@code <10}. */
    private static String mask(long count) {
        return count > 0 && count < 10 ? "<10" : String.valueOf(count);
    }
}
