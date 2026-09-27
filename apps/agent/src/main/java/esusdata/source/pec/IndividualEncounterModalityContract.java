package esusdata.source.pec;

/**
 * The frozen contract of the {@code individual_encounter_modality} capability that production code
 * still needs once the read itself runs in the execution plane (ADR 0017): its name, adapter
 * version and query text/checksum, all recorded in every manifest and checked in every handshake.
 */
// PMD: a holder of frozen contract constants, deliberately nothing else.
@SuppressWarnings("PMD.DataClass")
public final class IndividualEncounterModalityContract {

    public static final String CAPABILITY = "individual_encounter_modality";
    public static final String ADAPTER_VERSION = "0.1.0";

    /**
     * Loaded from {@code contracts/compatibility/queries/individual_encounter_modality@0.1.0.sql}
     * (the {@code pom.xml} resource copy makes {@code contracts/compatibility} the classpath root
     * — ADR 0009: {@code contracts/} is the single source of the compatibility contract), not an
     * inline text block, so a future non-Java execution plane can {@code include_str!} the exact
     * same bytes rather than keep a second, driftable copy of the query text. Its SHA-256 is
     * recorded as {@code query_checksum} in the adapter matrix.
     */
    private static final String QUERY_RESOURCE = "/compatibility/queries/individual_encounter_modality@0.1.0.sql";

    public static final String QUERY = FrozenQuery.load(QUERY_RESOURCE);

    /**
     * Real SHA-256 of {@link #QUERY}, computed once and reused everywhere a query checksum is
     * recorded (the adapter matrix, extraction manifests) — so those provenance fields can never
     * drift from the query text they claim to describe.
     */
    public static final String QUERY_CHECKSUM = FrozenQuery.checksum(QUERY);

    private IndividualEncounterModalityContract() {}
}
