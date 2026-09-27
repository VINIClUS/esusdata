package esusdata.report.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stores aggregate exports. Every read takes the municipality and the current instant, so an
 * export of another municipality and an expired one are both simply absent — the same 404 as an
 * unknown id (§1.10.1).
 */
public interface ReportExportRepository {

    /**
     * Inserts the export unless its creator already made {@code quota} exports since {@code since}.
     * The count and the insert are one statement, so two concurrent requests cannot both slip
     * under the quota. Returns false when the quota refused it.
     */
    boolean insertWithinQuota(ReportExport export, byte[] content, Instant since, int quota);

    Optional<ReportExportContent> findInScope(String exportId, String municipalityIbge, Instant now);

    /** The municipality's unexpired exports, newest first. */
    List<ReportExport> listRecent(String municipalityIbge, Instant now, int limit);

    /** Deletes every export past its expiry and returns how many it deleted. */
    int purgeExpired(Instant now);
}
