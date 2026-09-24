package esusdata.result.model;

/**
 * Deterministic, scope-checked pagination over evidence for one published result (§1.10.1:
 * "cursor opaco vinculado ao resultado publicado/filtros/ordenação e autorização a cada página").
 * The cursor here is a monotonic {@code seq} scoped to one immutable {@code staging_id} — it
 * grants no access by itself; every call still re-resolves and re-checks the municipality scope.
 */
public interface EvidenceRepository {
    static final int DEFAULT_PAGE_SIZE = 100;
    static final int MAX_PAGE_SIZE = 500;

    EvidencePage page(String resultId, String municipalityIbge, Long afterSeq, int limit);

    /**
     * §1.12 L427 CNES/INE narrowing: a non-null {@code cnes}/{@code ine} filters the underlying
     * rows in SQL, before pagination — filtering the page's Java list afterward would corrupt
     * {@code hasMore}/{@code nextCursor} (a page could come back short of {@code limit} while more
     * matching rows exist further down the {@code seq} order).
     */
    EvidencePage page(String resultId, String municipalityIbge, String cnes, String ine, Long afterSeq, int limit);
}
