package esusdata.result.model;

import java.util.List;
import java.util.Optional;

/**
 * Reads published results. Every method requires an explicit municipality scope — §1.12.1:
 * "consultas sempre recebem escopo autorizado." There is no unscoped read path in this class.
 */
public interface ResultRepository {
    List<PublishedResult> findPublished(
            String municipalityIbge, String indicatorPack, String referencePeriod);

    /** Reference periods with at least one published result in the municipality, newest first. */
    List<String> findPublishedPeriods(String municipalityIbge);

    /**
     * Looks up a result by id, scoped to a municipality. An object that exists but is out of
     * scope returns empty — identical to "not found" from the caller's perspective (§1.10.1:
     * "objeto inexistente e objeto fora do escopo têm a mesma resposta externa 404").
     */
    Optional<PublishedResult> findByIdInScope(String resultId, String municipalityIbge);

    /** Lets {@code GET /runs/{id}} surface where a SUCCEEDED job's result landed. */
    Optional<String> findResultIdByJobId(String jobId, String municipalityIbge);
}
