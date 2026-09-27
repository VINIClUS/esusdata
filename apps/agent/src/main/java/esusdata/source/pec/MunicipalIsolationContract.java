package esusdata.source.pec;

/**
 * The frozen contract of the {@code municipal_isolation} capability (ADR 0023): one aggregate
 * query that counts a competência's atendimentos per {@code tb_dim_municipio.co_ibge}. It takes no
 * municipality — the counts are compared with the source's registered IBGE in Java — and reads
 * nothing but those counts.
 */
// PMD: a holder of frozen contract constants, deliberately nothing else.
@SuppressWarnings("PMD.DataClass")
public final class MunicipalIsolationContract {

    public static final String CAPABILITY = "municipal_isolation";
    public static final String ADAPTER_VERSION = "0.1.0";

    private static final String QUERY_RESOURCE = "/compatibility/queries/municipal_isolation@0.1.0.sql";

    public static final String QUERY = FrozenQuery.load(QUERY_RESOURCE);

    /** Recorded as {@code query_checksum} in the adapter matrix and checked in every handshake. */
    public static final String QUERY_CHECKSUM = FrozenQuery.checksum(QUERY);

    private MunicipalIsolationContract() {}
}
