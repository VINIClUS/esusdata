package esusdata.source.pec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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

    public static final String QUERY = loadQuery();

    /**
     * Real SHA-256 of {@link #QUERY}, computed once and reused everywhere a query checksum is
     * recorded (the adapter matrix, extraction manifests) — so those provenance fields can never
     * drift from the query text they claim to describe.
     */
    public static final String QUERY_CHECKSUM = computeQueryChecksum();

    private static String loadQuery() {
        try (InputStream resource = IndividualEncounterModalityContract.class.getResourceAsStream(QUERY_RESOURCE)) {
            if (resource == null) {
                throw new IllegalStateException("Packaged capability query is missing: " + QUERY_RESOURCE);
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read packaged capability query: " + QUERY_RESOURCE, e);
        }
    }

    private static String computeQueryChecksum() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(digest.digest(QUERY.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private IndividualEncounterModalityContract() {}
}
