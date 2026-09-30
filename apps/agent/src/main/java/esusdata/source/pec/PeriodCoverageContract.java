package esusdata.source.pec;

/**
 * The frozen contract of the {@code period_coverage} capability (ADR 0027): one aggregate query
 * that counts a window's atendimentos per {@code tb_dim_municipio.co_ibge} and month
 * ({@code yyyy-MM} of {@code tb_dim_tempo.dt_registro}). Like {@link MunicipalIsolationContract}
 * it takes no municipality and reads nothing but those counts.
 */
// PMD: a holder of frozen contract constants, deliberately nothing else.
@SuppressWarnings("PMD.DataClass")
public final class PeriodCoverageContract {

    public static final String CAPABILITY = "period_coverage";
    public static final String ADAPTER_VERSION = "0.1.0";

    private static final String QUERY_RESOURCE = "/compatibility/queries/period_coverage@0.1.0.sql";

    public static final String QUERY = FrozenQuery.load(QUERY_RESOURCE);

    /** Recorded as {@code query_checksum} in the adapter matrix and checked in every handshake. */
    public static final String QUERY_CHECKSUM = FrozenQuery.checksum(QUERY);

    private PeriodCoverageContract() {}
}
