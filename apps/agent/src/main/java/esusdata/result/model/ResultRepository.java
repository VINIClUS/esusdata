package esusdata.result.model;

import esusdata.indicator.ReleaseGateRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads published results. Every method requires an explicit municipality scope — §1.12.1:
 * "consultas sempre recebem escopo autorizado." There is no unscoped read path in this class.
 */
public interface ResultRepository {
    List<PublishedResult> findPublished(String municipalityIbge, String indicatorPack, String referencePeriod);

    /**
     * The newest published result of each (indicator pack, competência) in {@code [fromPeriod,
     * toPeriod]}, both {@code yyyy-MM} and inclusive, ordered by pack then competência — the rows
     * of an aggregate export (ADR 0024). A null {@code indicatorPack} means every pack.
     */
    List<PublishedResult> findLatestPublishedInRange(
            String municipalityIbge, String indicatorPack, String fromPeriod, String toPeriod);

    /** Reference periods with at least one published result in the municipality, newest first. */
    List<String> findPublishedPeriods(String municipalityIbge);

    /**
     * Every published (pack, competência, rule version, gate snapshot) of the municipality, ordered
     * by pack then competência: the raw material of {@link #findPublishedPeriodsByPack} and of the
     * scheduler, which compares it with the registry it was started with.
     */
    List<PublishedCoverage> findPublishedCoverage(String municipalityIbge);

    /**
     * The competências with a published result of each pack in the municipality (ADR 0030:
     * "publicado" is per pack), by pack id, each set in ascending order. Only a result of the rule
     * version this release computes, recorded under the bundled registry's current A and D, counts
     * (ADR 0032): a competência published under an older version or a superseded gate state is due
     * again.
     */
    default Map<String, Set<String>> findPublishedPeriodsByPack(String municipalityIbge) {
        return PublishedCoverage.coveredByPack(findPublishedCoverage(municipalityIbge), ReleaseGateRegistry.bundled());
    }

    /**
     * Looks up a result by id, scoped to a municipality. An object that exists but is out of
     * scope returns empty — identical to "not found" from the caller's perspective (§1.10.1:
     * "objeto inexistente e objeto fora do escopo têm a mesma resposta externa 404").
     */
    Optional<PublishedResult> findByIdInScope(String resultId, String municipalityIbge);

    /** Lets {@code GET /runs/{id}} surface where a SUCCEEDED job's result landed. */
    Optional<String> findResultIdByJobId(String jobId, String municipalityIbge);
}
