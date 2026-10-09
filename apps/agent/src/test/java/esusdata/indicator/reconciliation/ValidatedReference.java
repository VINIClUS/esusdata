package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.NormalizedReference.FinalRow;
import esusdata.indicator.reconciliation.NormalizedReference.IndicatorRow;
import esusdata.indicator.reconciliation.NormalizedReference.TeamRow;
import esusdata.indicator.reconciliation.SiapsSnapshot.Team;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What {@link PackVerdict#evaluate} compares the local classes with: the official side of one
 * pack, once it was checked for what can make a comparison wrong (spec §11 and §12). It is built
 * only by {@link #from} (an official team export), {@link #fromStored} (the subset of one, stored
 * and verified again) and {@link #fromPublicAggregate} (the public SIAPS answer), which say what
 * is missing instead of filling it in:
 *
 * <ul>
 *   <li>the official counts of a team type are present or the reference has a gap; an absent row
 *       is never a zero. A zero is a count that a complete official universe proves: a team type
 *       with no team among the teams of an official export. A row of zeros in the public answer
 *       does not prove it (the answer has no universe to say the type has no team) and is a gap
 *       too;
 *   <li>the universe of teams is the official one ({@link UniverseConfidence#OFFICIAL}: the teams of
 *       the export that have a row) or unknown ({@link UniverseConfidence#UNKNOWN}). The public
 *       answer has no historical universe: the team list that comes with it is the <em>current</em>
 *       directory, which only tells which of the local teams are eSF and which are eAP, and so
 *       a reference built from it can run as a diagnostic but never decide the gate.
 * </ul>
 *
 * @param pack the pack the reference is about (the Nota Final included)
 * @param municipalityIbge the municipality as the SIAPS spells it: 6 digits
 * @param quadrimestre the SIAPS spelling, {@code 2026Q1}
 * @param gaps why the reference cannot be compared, in Portuguese as the verdict shows them; empty
 *     when it is complete
 * @param counts the official class counts by team type; both {@code eSF} and {@code eAP} when
 *     there is no gap
 * @param teams the official universe, or the current directory that splits the local teams by type
 *     when the universe is unknown
 * @param officialClasses the official class of each team of an official export; empty for the
 *     public answer, which has counts only
 */
record ValidatedReference(
        GatePack pack,
        SourceKind sourceKind,
        String municipalityIbge,
        String quadrimestre,
        UniverseConfidence universe,
        List<String> gaps,
        Map<String, ClassCounts> counts,
        List<Team> teams,
        Map<String, Classification> officialClasses) {

    private static final List<String> TYPES = List.of(SiapsParser.ESF, SiapsParser.EAP);

    /** Whether the teams of the reference are the teams the SIAPS counted in the quadrimestre. */
    enum UniverseConfidence {
        /** The universe comes from an official artifact of that municipality and quadrimestre. */
        OFFICIAL,
        /** There is no historical universe: only today's team directory. Diagnostic only. */
        UNKNOWN
    }

    ValidatedReference {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(universe, "universe");
        municipalityIbge = SiapsFormats.ibgeOfSiaps(municipalityIbge);
        ReferenceFormats.quadrimestre(quadrimestre);
        gaps = List.copyOf(gaps);
        counts = Map.copyOf(counts);
        teams = List.copyOf(teams);
        officialClasses = Map.copyOf(officialClasses);
        if ((sourceKind == SourceKind.PUBLIC_AGGREGATE) != (universe == UniverseConfidence.UNKNOWN)) {
            throw new IllegalArgumentException("only the public aggregate lacks a historical universe");
        }
        if (gaps.isEmpty() && !counts.keySet().containsAll(TYPES)) {
            throw new IllegalArgumentException("a reference without gaps has the counts of eSF and eAP");
        }
    }

    /**
     * The reference of one pack from an official team export. Its gap, when a team of the export
     * has no row for the pack (its Total row, for the Nota Final), is the count of those teams.
     */
    static ValidatedReference from(OfficialTeamReference reference, GatePack pack) {
        int missing = reference.missingRows(pack);
        List<String> gaps = missing == 0
                ? List.of()
                : List.of(missing + " de " + reference.teamCount() + " equipes do arquivo oficial sem a linha "
                        + (pack.isNotaFinal() ? "Total (nota final)" : "de " + pack.code()));
        Map<String, Classification> classes = reference.classes(pack);
        List<Team> teams = new ArrayList<>();
        Map<String, ClassCounts> counts = new TreeMap<>();
        for (String type : TYPES) {
            ClassCounts ofType = ClassCounts.EMPTY;
            for (String ine : reference.officialUniverse(pack.siapsCode(), type)) {
                teams.add(new Team(ine, type));
                ofType = ofType.plus(classes.get(ine));
            }
            counts.put(type, ofType);
        }
        return new ValidatedReference(
                pack,
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                reference.municipalityIbge(),
                reference.quadrimestre(),
                UniverseConfidence.OFFICIAL,
                gaps,
                counts,
                teams,
                classes);
    }

    /**
     * The reference of one pack from its stored subset ({@link ReferenceArtifactStore#load}, which
     * re-verified its hashes). A subset is whole by construction ({@link OfficialTeamReference#subset}
     * refuses a pack some team has no row for), so there is no gap, and its teams are the official
     * universe: the result equals {@link #from} over the export the subset was taken from.
     *
     * @throws IllegalArgumentException if the subset is not an official team export's, or is of
     *     another pack
     */
    static ValidatedReference fromStored(NormalizedReference stored, GatePack pack) {
        if (stored.sourceKind() != SourceKind.OFFICIAL_TEAM_EXPORT_CSV) {
            throw new IllegalArgumentException("only an official team export has a historical universe");
        }
        if (stored.indicatorCode() != pack.siapsCode()) {
            throw new IllegalArgumentException("the stored subset is not that of " + pack.code());
        }
        Map<String, Classification> classes = new TreeMap<>();
        for (TeamRow row : stored.rows()) {
            classes.put(row.ine(), classOf(row));
        }
        List<Team> teams = new ArrayList<>();
        Map<String, ClassCounts> counts = new TreeMap<>();
        for (String type : TYPES) {
            ClassCounts ofType = ClassCounts.EMPTY;
            for (TeamRow row : stored.rows()) {
                if (row.teamType().equals(type)) {
                    teams.add(new Team(row.ine(), type));
                    ofType = ofType.plus(classes.get(row.ine()));
                }
            }
            counts.put(type, ofType);
        }
        return new ValidatedReference(
                pack,
                stored.sourceKind(),
                stored.municipalityIbge(),
                stored.quadrimestre(),
                UniverseConfidence.OFFICIAL,
                List.of(),
                counts,
                teams,
                classes);
    }

    private static Classification classOf(TeamRow row) {
        return switch (row) {
            case IndicatorRow indicator -> indicator.concept();
            case FinalRow last -> last.finalClass();
        };
    }

    /**
     * The reference of one pack from the public SIAPS answer: diagnostic only, with an unknown
     * universe. A row, a team list or (for the Nota Final) the final classification the answer
     * lacks is a gap, and so is a row of zeros: only the complete universe of an official export
     * proves that a team type has no team.
     */
    static ValidatedReference fromPublicAggregate(SiapsSnapshot snapshot, GatePack pack) {
        List<String> gaps = new ArrayList<>();
        Map<String, ClassCounts> counts = new TreeMap<>();
        boolean hasRows = !pack.isNotaFinal() || snapshot.hasFinalRows();
        if (!hasRows) {
            gaps.add("o SIAPS não devolveu a classificação final (QUALIDADE)");
        }
        for (String type : TYPES) {
            Optional<ClassCounts> row = snapshot.counts(pack.siapsCode(), type);
            if (row.isPresent() && row.get().total() > 0) {
                counts.put(type, row.get());
            } else if (hasRows) {
                gaps.add(row.isPresent() ? zeroRowGap(type, pack) : absentRowGap(type, pack));
            }
        }
        Optional<List<Team>> teams =
                pack.isNotaFinal() ? snapshot.notaFinalTeams() : snapshot.teamsOf(pack.siapsCode());
        if (teams.isEmpty()) {
            gaps.add(
                    pack.isNotaFinal()
                            ? "faltam listas de equipes de C1–C7 no SIAPS"
                            : "o SIAPS não devolveu a lista de equipes de " + pack.code());
        }
        return new ValidatedReference(
                pack,
                SourceKind.PUBLIC_AGGREGATE,
                snapshot.municipalityIbge(),
                snapshot.quadrimestre(),
                UniverseConfidence.UNKNOWN,
                gaps,
                counts,
                teams.orElse(List.of()),
                Map.of());
    }

    private static String absentRowGap(String type, GatePack pack) {
        return "o SIAPS não devolveu a linha de " + type + " de " + pack.code();
    }

    private static String zeroRowGap(String type, GatePack pack) {
        return "a linha de " + type + " de " + pack.code()
                + " do SIAPS é só de zeros, e só o arquivo oficial por equipe prova que o tipo não tem equipes";
    }
}
