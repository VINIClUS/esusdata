package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.Comparison.RowResult;
import esusdata.indicator.reconciliation.Comparison.TeamSplit;
import esusdata.indicator.reconciliation.ValidatedReference.UniverseConfidence;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The Portão D verdict of one pack, from the local classes and a {@link ValidatedReference}:
 * PASSED when at least one row was evaluated and every evaluated row passes, FAILED when one does
 * not, PENDING when there is nothing to compare. It fails closed (spec §12): a reference with a
 * gap (a team without its row, an official row or team list the SIAPS did not give, a row of
 * zeros that only a complete official universe could prove), an unknown team universe in a {@link
 * ReferencePurpose#GATE} run, or no row to evaluate, is PENDING, never a zero and never a pass. For a {@link ReferencePurpose#DIAGNOSTIC} reference the figures are the
 * same but the result is no evidence of the gate.
 *
 * @param quadrimestre the SIAPS quadrimestre compared (SIAPS spelling), or {@code null} when none
 * @param teamLines one line per team for the local, git-ignored output only
 * @param localSourceFingerprint the {@code InputFingerprint} of the local extracts the classes were
 *     computed from, or {@link #NO_LOCAL_SOURCE} when no local source was read
 */
public record PackVerdict(
        GatePack pack,
        String ruleVersion,
        ReferencePurpose purpose,
        Status status,
        String reason,
        String quadrimestre,
        List<RowResult> rows,
        int localNotInSiaps,
        List<TeamLine> teamLines,
        String localSourceFingerprint) {

    /** The fingerprint of a verdict that compared nothing, so read no local source. */
    public static final String NO_LOCAL_SOURCE = "";

    private static final Pattern FINGERPRINT = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final List<String> TYPES = List.of(SiapsParser.ESF, SiapsParser.EAP);
    private static final String UNKNOWN_UNIVERSE = "o agregado público não traz o universo histórico de equipes "
            + "(a lista atual de equipes não vale como universo do portão)";

    public enum Status {
        PASSED,
        FAILED,
        PENDING
    }

    /** A team of the SIAPS list with its local class, or a local team the list does not have. */
    public record TeamLine(String ine, String teamType, Classification local, String note) {}

    public PackVerdict {
        Objects.requireNonNull(purpose, "purpose");
        rows = List.copyOf(rows);
        teamLines = List.copyOf(teamLines);
        if (!localSourceFingerprint.isEmpty()
                && !FINGERPRINT.matcher(localSourceFingerprint).matches()) {
            throw new IllegalArgumentException(
                    "a local source fingerprint is sha256:<64 hex> (InputFingerprint), or empty when none was read");
        }
    }

    public static PackVerdict pending(GatePack pack, String ruleVersion, ReferencePurpose purpose, String reason) {
        return pending(pack, ruleVersion, purpose, reason, NO_LOCAL_SOURCE);
    }

    private static PackVerdict pending(
            GatePack pack, String ruleVersion, ReferencePurpose purpose, String reason, String localSourceFingerprint) {
        return new PackVerdict(
                pack,
                ruleVersion,
                purpose,
                Status.PENDING,
                reason,
                null,
                List.of(),
                0,
                List.of(),
                localSourceFingerprint);
    }

    /**
     * Compares one pack's local classes with the official side of {@code reference}.
     *
     * @param localSourceFingerprint what the local classes were computed from, carried by the verdict
     * @throws IllegalArgumentException when the reference was validated for another pack
     */
    public static PackVerdict evaluate(
            GatePack pack,
            String ruleVersion,
            ReferencePurpose purpose,
            ValidatedReference reference,
            LocalClasses local,
            String localSourceFingerprint) {
        if (!reference.pack().equals(pack)) {
            throw new IllegalArgumentException(
                    "the reference was validated for " + reference.pack().code() + ", not for " + pack.code());
        }
        Optional<String> blocked = blockedBy(reference, purpose);
        if (blocked.isPresent()) {
            return pending(pack, ruleVersion, purpose, blocked.get(), localSourceFingerprint);
        }
        List<RowResult> rows = new ArrayList<>();
        for (String type : TYPES) {
            TeamSplit split = Comparison.split(type, reference.teams(), local.byIne());
            rows.add(Comparison.row(type, reference.counts().get(type), split.local(), split.semClasseLocal()));
        }
        List<TeamLine> lines = new ArrayList<>();
        Set<String> listedInes = new HashSet<>();
        for (SiapsSnapshot.Team team : reference.teams()) {
            listedInes.add(team.ine());
            Classification classification = local.byIne().get(team.ine());
            lines.add(new TeamLine(
                    team.ine(), team.teamType(), classification, classification == null ? "sem classe local" : ""));
        }
        int notListed = 0;
        for (String ine : local.seen()) {
            if (!listedInes.contains(ine)) {
                notListed++;
                lines.add(new TeamLine(ine, "", local.byIne().get(ine), "fora da lista do SIAPS"));
            }
        }
        Status status = statusOf(rows);
        return new PackVerdict(
                pack,
                ruleVersion,
                purpose,
                status,
                reasonOf(status),
                reference.quadrimestre(),
                rows,
                notListed,
                lines,
                localSourceFingerprint);
    }

    /** Why nothing can be compared yet, if so: a gap in the reference, or no historical universe for the gate. */
    private static Optional<String> blockedBy(ValidatedReference reference, ReferencePurpose purpose) {
        if (!reference.gaps().isEmpty()) {
            return Optional.of("referência incompleta: " + String.join("; ", reference.gaps()));
        }
        if (purpose == ReferencePurpose.GATE && reference.universe() == UniverseConfidence.UNKNOWN) {
            return Optional.of(UNKNOWN_UNIVERSE);
        }
        return Optional.empty();
    }

    private static Status statusOf(List<RowResult> rows) {
        if (rows.stream().noneMatch(RowResult::evaluated)) {
            return Status.PENDING;
        }
        return Comparison.firstFailure(rows).isPresent() ? Status.FAILED : Status.PASSED;
    }

    private static String reasonOf(Status status) {
        return switch (status) {
            case PENDING -> "sem linhas avaliáveis";
            case FAILED -> "distância acima do limiar em ao menos uma linha";
            case PASSED -> "";
        };
    }

    /** True when the verdict is the gate's evidence: a gate reference and a decided status. */
    public boolean isGateEvidence() {
        return purpose == ReferencePurpose.GATE && status != Status.PENDING;
    }
}
