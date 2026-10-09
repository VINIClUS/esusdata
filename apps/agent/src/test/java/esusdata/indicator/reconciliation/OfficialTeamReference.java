package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.NormalizedReference.FinalRow;
import esusdata.indicator.reconciliation.NormalizedReference.IndicatorRow;
import esusdata.indicator.reconciliation.NormalizedReference.TeamRow;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What one official SIAPS team export (layout {@value OfficialTeamExportCsvParser#PARSER_VERSION})
 * says about one municipality and one quadrimestre. Only {@link OfficialTeamExportCsvParser}
 * builds it, after the file passed every check of the layout.
 *
 * <p>The file is a table of team by indicator. Its eSF and eAP teams are the official universe:
 * every INE the file names with one of those types, whatever rows it has. A team's row for an
 * indicator (or its {@code Total} row, for the Nota Final) can be absent, and then that indicator
 * (or the Nota Final) is <em>incomplete</em> for the whole file: {@link #missingRows} counts the
 * teams that lack it, and nothing is filled in with a zero or with a class. An incomplete pack has
 * no {@link #subset} to capture and no verdict to give but PENDING.
 *
 * <p>Teams of the types the Componente de Qualidade of eSF/eAP does not cover (eSB, eMulti) are
 * only counted in {@link #skipped}: their rows are not read.
 *
 * @param municipalityIbge the 7-digit IBGE code the caller expected (the file says its first six
 *     digits, which the parser checked)
 * @param quadrimestre the SIAPS spelling, {@code 2026Q1}
 * @param officialStatus preliminary or final, as the file says it
 * @param officialGeneratedAt when the SIAPS says it generated the file (it changes with every
 *     download and is not part of the normalized content)
 * @param teamTypes the official universe: INE (10 digits) to {@code eSF} or {@code eAP}
 * @param indicatorRows the indicator rows by SIAPS code and INE
 * @param totalRows the {@code Total} (Nota final e classificação final) row by INE
 * @param skipped the teams and rows of each out-of-scope team type
 */
record OfficialTeamReference(
        String municipalityIbge,
        String quadrimestre,
        OfficialStatus officialStatus,
        LocalDateTime officialGeneratedAt,
        Map<String, String> teamTypes,
        Map<Integer, Map<String, IndicatorRow>> indicatorRows,
        Map<String, FinalRow> totalRows,
        Map<String, Skipped> skipped) {

    /** How many teams of an out-of-scope type the file held, and how many rows they took. */
    record Skipped(int teams, int rows) {

        Skipped {
            if (teams < 1 || rows < teams) {
                throw new IllegalArgumentException("a skipped team type has a team and a row for each team");
            }
        }
    }

    OfficialTeamReference {
        ReferenceFormats.ibge7(municipalityIbge);
        ReferenceFormats.quadrimestre(quadrimestre);
        Objects.requireNonNull(officialStatus, "officialStatus");
        Objects.requireNonNull(officialGeneratedAt, "officialGeneratedAt");
        if (teamTypes.isEmpty()) {
            throw new IllegalArgumentException("an official reference holds at least one eSF or eAP team");
        }
        teamTypes.forEach(OfficialTeamReference::requireTeam);
        teamTypes = Map.copyOf(teamTypes);
        indicatorRows = copyOfIndicatorRows(indicatorRows, teamTypes);
        totalRows = copyOfTotalRows(totalRows, teamTypes);
        skipped = Map.copyOf(skipped);
    }

    private static void requireTeam(String ine, String teamType) {
        if (!SiapsFormats.ine(ine).equals(ine)) {
            throw new IllegalArgumentException("a team is named by its 10-digit INE");
        }
        if (!SiapsParser.ESF.equals(teamType) && !SiapsParser.EAP.equals(teamType)) {
            throw new IllegalArgumentException("an official reference holds eSF and eAP teams only");
        }
    }

    private static Map<Integer, Map<String, IndicatorRow>> copyOfIndicatorRows(
            Map<Integer, Map<String, IndicatorRow>> rows, Map<String, String> teamTypes) {
        Map<Integer, Map<String, IndicatorRow>> copy = new TreeMap<>();
        for (Map.Entry<Integer, Map<String, IndicatorRow>> byCode : rows.entrySet()) {
            if (GatePack.bySiapsCode(byCode.getKey()).isEmpty()) {
                throw new IllegalArgumentException("not a SIAPS indicator of the gate: " + byCode.getKey());
            }
            byCode.getValue().forEach((ine, row) -> requireRowOfTeam(teamTypes, ine, row));
            copy.put(byCode.getKey(), Map.copyOf(byCode.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static Map<String, FinalRow> copyOfTotalRows(Map<String, FinalRow> rows, Map<String, String> teamTypes) {
        rows.forEach((ine, row) -> requireRowOfTeam(teamTypes, ine, row));
        return Map.copyOf(rows);
    }

    private static void requireRowOfTeam(Map<String, String> teamTypes, String ine, TeamRow row) {
        if (!ine.equals(row.ine()) || !row.teamType().equals(teamTypes.get(ine))) {
            throw new IllegalArgumentException("a row of a team the reference does not list, or of another type");
        }
    }

    /** Every eSF or eAP team the file names, by INE: the official universe of the file. */
    int teamCount() {
        return teamTypes.size();
    }

    /** The INEs of the teams of one type (eSF or eAP), in order. */
    Set<String> universe(String teamType) {
        return teamTypes.entrySet().stream()
                .filter(team -> team.getValue().equals(teamType))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * The teams of {@code teamType} that have an official row for the indicator (a {@code Total}
     * row for {@link GatePack#NOTA_FINAL_CODE}), in order. Equal to {@link #universe} when the
     * indicator is complete.
     */
    Set<String> officialUniverse(int indicatorCode, String teamType) {
        Set<String> ines = new TreeSet<>(universe(teamType));
        ines.retainAll(inesWithRow(indicatorCode));
        return ines;
    }

    private Set<String> inesWithRow(int indicatorCode) {
        if (indicatorCode == GatePack.NOTA_FINAL_CODE) {
            return totalRows.keySet();
        }
        Map<String, IndicatorRow> rows = indicatorRows.get(indicatorCode);
        return rows == null ? Set.of() : rows.keySet();
    }

    /** How many of the file's teams have no row for the pack (its {@code Total} row, for the Nota Final). */
    int missingRows(GatePack pack) {
        return teamTypes.size() - inesWithRow(pack.siapsCode()).size();
    }

    /** True when every team of the file has a row for the pack: nothing about it is left to a zero. */
    boolean isComplete(GatePack pack) {
        return missingRows(pack) == 0;
    }

    /**
     * The official class of each team that has a row for the pack: the concept of the indicator,
     * or the final class of the Nota Final.
     */
    Map<String, Classification> classes(GatePack pack) {
        Map<String, Classification> classes = new TreeMap<>();
        if (pack.isNotaFinal()) {
            totalRows.forEach((ine, row) -> classes.put(ine, row.finalClass()));
        } else {
            Map<String, IndicatorRow> rows = indicatorRows.get(pack.siapsCode());
            if (rows != null) {
                rows.forEach((ine, row) -> classes.put(ine, row.concept()));
            }
        }
        return classes;
    }

    /**
     * The normalized content of the pack: what is hashed, stored and registered as a revision.
     *
     * @throws IllegalStateException when a team has no row for the pack: an incomplete reference is
     *     not captured
     */
    NormalizedReference subset(GatePack pack) {
        int missing = missingRows(pack);
        if (missing > 0) {
            throw new IllegalStateException("the export is incomplete for " + pack.code() + ": " + missing + " of its "
                    + teamTypes.size() + " teams have no row");
        }
        List<TeamRow> rows = new ArrayList<>();
        if (pack.isNotaFinal()) {
            rows.addAll(totalRows.values());
        } else {
            rows.addAll(indicatorRows.get(pack.siapsCode()).values());
        }
        return new NormalizedReference(
                municipalityIbge,
                quadrimestre,
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                OfficialTeamExportCsvParser.PARSER_VERSION,
                officialStatus,
                pack.siapsCode(),
                rows);
    }
}
