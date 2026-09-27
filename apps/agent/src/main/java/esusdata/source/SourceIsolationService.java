package esusdata.source;

import esusdata.source.model.LastIsolationCheck;
import esusdata.source.model.SourceNotFoundException;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import esusdata.source.pec.SourceAcquisitionLimiter;
import esusdata.source.pec.SourceBudgetExceededException;
import java.net.InetAddress;
import java.time.Clock;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code POST /sources/{id}/isolation-check} (ADR 0023): counts one competência's atendimentos in
 * the source's PEC per municipality code and compares them with the municipality the source was
 * registered for. The read goes through {@link SourceIsolationCheck}, implemented by the execution
 * plane behind the same compatibility handshake as an acquisition; the allowlist, the
 * one-active-acquisition permit and the comparison stay here.
 *
 * <p>What the result says, and no more: how many of the competência's atendimentos are the
 * registered municipality's, how many are another municipality's (which an acquisition leaves out,
 * because its query binds on {@code co_ibge}) and how many have no municipality code at all. It
 * says nothing about data quality, CNES/INE or territory.
 *
 * <p>Every outcome but {@code SOURCE_BUSY} is stored as the source's last isolation check, pinned
 * to its configuration version ({@code V5__source_isolation_checks.sql}).
 */
public final class SourceIsolationService {

    private static final Pattern IBGE = Pattern.compile("\\d{7}");

    private final SourceRepository sourceRepository;
    private final AllowedDestinations allowedDestinations;
    private final SourceIsolationCheck isolationCheck;
    private final Clock clock;

    public enum Outcome {
        DESTINATION_NOT_ALLOWED,
        SOURCE_BUSY,
        SOURCE_AUTHENTICATION_FAILED,
        SOURCE_PERMISSION_DENIED,
        CONNECTION_FAILED,
        COMPATIBILITY_MISMATCH,
        SOURCE_BUDGET_EXCEEDED,
        CHECKED
    }

    /** The counts of a {@code CHECKED} outcome; see {@link LastIsolationCheck} for each field. */
    public record Summary(long registered, long otherMunicipalities, int otherMunicipalityCodes, long unidentified) {}

    public SourceIsolationService(
            SourceRepository sourceRepository,
            AllowedDestinations allowedDestinations,
            SourceIsolationCheck isolationCheck,
            Clock clock) {
        this.sourceRepository = sourceRepository;
        this.allowedDestinations = allowedDestinations;
        this.isolationCheck = isolationCheck;
        this.clock = clock;
    }

    public Optional<SourceRecord> find(String sourceId) {
        return sourceRepository.findById(sourceId);
    }

    /**
     * Only a PEC source with its whole identity can be checked: the matrix entry is chosen by
     * version, read model and installation role. An external dataset or a replay-only source has
     * nothing to count.
     */
    public static boolean canCheck(SourceRecord source) {
        if (!"PEC_POSTGRESQL".equals(source.sourceFamily())) {
            return false;
        }
        try {
            // The same rules the check itself applies: a semantic version, a known read model
            // and a known installation role.
            return new PecSourceIdentity(
                            source.id(), source.pecVersion(), source.readModel(), source.pecInstallationRole())
                    .isComplete();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * Runs the check for one registered source and competência.
     *
     * @throws SourceNotFoundException if {@code sourceId} does not resolve.
     * @throws IllegalArgumentException if the source fails {@link #canCheck}.
     */
    public LastIsolationCheck check(String sourceId, YearMonth referencePeriod) {
        SourceRecord source = sourceRepository
                .findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException("unknown source: " + sourceId));
        if (!canCheck(source)) {
            throw new IllegalArgumentException("source is not a PEC source with a complete identity: " + sourceId);
        }
        Checked checked = run(source, referencePeriod);
        Outcome outcome = checked.outcome();
        Summary summary = outcome == Outcome.CHECKED ? summarize(source.municipalityIbge(), checked.counts()) : null;
        LastIsolationCheck check = new LastIsolationCheck(
                source.sourceConfigurationVersion(),
                referencePeriod.toString(),
                outcome.name(),
                summary == null ? null : summary.registered(),
                summary == null ? null : summary.otherMunicipalities(),
                summary == null ? null : summary.otherMunicipalityCodes(),
                summary == null ? null : summary.unidentified(),
                clock.instant().toString());
        if (outcome != Outcome.SOURCE_BUSY) {
            sourceRepository.recordIsolationCheck(source.id(), check);
        }
        return check;
    }

    static Summary summarize(String registeredIbge, List<SourceIsolationCheck.MunicipalityCount> counts) {
        long registered = 0;
        long others = 0;
        long unidentified = 0;
        Set<String> otherCodes = new HashSet<>();
        for (SourceIsolationCheck.MunicipalityCount count : counts) {
            String ibge = count.ibge() == null ? null : count.ibge().strip();
            if (registeredIbge.equals(ibge)) {
                registered += count.count();
            } else if (ibge != null && IBGE.matcher(ibge).matches()) {
                others += count.count();
                otherCodes.add(ibge);
            } else {
                unidentified += count.count();
            }
        }
        return new Summary(registered, others, otherCodes.size(), unidentified);
    }

    private record Checked(Outcome outcome, List<SourceIsolationCheck.MunicipalityCount> counts) {
        Checked(Outcome outcome) {
            this(outcome, List.of());
        }
    }

    private static Checked checked(SourceIsolationCheck.Result result) {
        Outcome outcome = switch (result.status()) {
            case CHECKED -> Outcome.CHECKED;
            case COMPATIBILITY_MISMATCH -> Outcome.COMPATIBILITY_MISMATCH;
            case SOURCE_BUDGET_EXCEEDED -> Outcome.SOURCE_BUDGET_EXCEEDED;
            // Same SQLSTATE classes as the diagnostic: the session is the same one.
            case FAILED ->
                Outcome.valueOf(SourceDiagnosticsService.classifySqlState(result.sqlState())
                        .name());
        };
        return new Checked(outcome, result.counts());
    }

    // javac's try lint / PMD: the permit is held for the block's scope and released on close, never read.
    @SuppressWarnings({"try", "PMD.UnusedLocalVariable"})
    private Checked run(SourceRecord source, YearMonth referencePeriod) {
        InetAddress validatedAddress;
        try {
            validatedAddress = allowedDestinations.assertAllowed(source.host(), source.port());
        } catch (AllowedDestinations.DestinationNotAllowedException e) {
            return new Checked(Outcome.DESTINATION_NOT_ALLOWED);
        }
        PecConnectionProperties properties = new PecConnectionProperties(
                source.id(),
                source.host(),
                source.port(),
                source.databaseName(),
                source.dbUser(),
                source.secretRef(),
                source.municipalityIbge());
        PecSourceIdentity identity = new PecSourceIdentity(
                source.id(), source.pecVersion(), source.readModel(), source.pecInstallationRole());
        try (SourceAcquisitionLimiter.Permit permit = SourceAcquisitionLimiter.acquireOrFail(source.id())) {
            return checked(isolationCheck.check(
                    properties,
                    identity,
                    validatedAddress.getHostAddress(),
                    referencePeriod,
                    ReadBudget.initialEngineeringProposal()));
        } catch (SourceBudgetExceededException e) {
            return new Checked(Outcome.SOURCE_BUSY);
        }
    }
}
