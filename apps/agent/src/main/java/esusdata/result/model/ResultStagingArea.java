package esusdata.result.model;

import java.util.List;

/**
 * Staging area for one computed result before the short publication transaction (§1.9.5).
 * Nothing written here is visible to a reader of published results — {@code PublicationService}
 * is the only path from {@code result_staging} to {@code results}.
 */
public interface ResultStagingArea {
    /** Opens a new staging row in state {@code OPEN}. */
    String open(StagingRequest request);

    /**
     * Writes evidence rows in bounded batches (§1.9.5: "evidências são gravadas em lotes de
     * staging") — assigns a deterministic {@code seq} in list order, starting at 0, so pagination
     * over the eventual published result is reproducible.
     */
    void writeEvidence(String stagingId, List<EvidenceEntry> entries);

    /** Seals a staging row — the last step before it can be published. */
    void seal(String stagingId);

    /**
     * Neutralizes a staging row and its evidence — used on cancellation and on recovery of an
     * abandoned job (§1.9.4: "estágio parcial neutralizado"). A {@code PUBLISHED} row is never
     * neutralized; the CAS below only matches {@code OPEN}/{@code SEALED}.
     */
    void neutralize(String stagingId);
}
