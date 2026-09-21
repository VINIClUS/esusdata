package br.gov.observatorioaps.sourceconnector.domain;

/**
 * Side effects an {@link AcquisitionPort} reports back to the caller while a live acquisition is
 * in flight. Kept separate from the return value because both effects matter before the
 * acquisition either succeeds or fails.
 */
public interface AcquisitionListener {

    /** A unit of durable progress happened (the connection opened; the extract was finalized). */
    void onProgress();

    /**
     * A live PEC read ended without proof the backend actually stopped — cancellation, a
     * transient network error, or any failure once the connection was live all count. Closing the
     * JDBC connection on the way out never proves the PostgreSQL backend stopped executing
     * (ENG-51): the caller is expected to block new acquisitions on this source for a cooldown.
     * Never fired for a failure to even open the connection — nothing was live yet.
     */
    void onUncertainOutcome(String reason);
}
