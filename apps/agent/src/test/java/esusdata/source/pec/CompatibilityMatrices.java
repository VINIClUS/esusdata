package esusdata.source.pec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Compatibility matrices for tests of capability eligibility (ADR 0030): which capabilities are
 * {@code VALIDATED} for which PEC versions, on the read model of each capability (PEC_DW, or PEC_OLTP for {@code team}) of a PRONTUARIO installation.
 * The entries carry every field {@link PecCompatibilityMatrix#entries()} parses; their fingerprints
 * are placeholders — nothing here is a validated contract.
 */
public final class CompatibilityMatrices {

    private CompatibilityMatrices() {}

    /** Every capability {@code VALIDATED} for {@code pecVersions}. */
    public static PecCompatibilityMatrix validated(List<String> pecVersions, Collection<String> capabilities) {
        List<String> entries = new ArrayList<>();
        for (String capability : capabilities) {
            entries.add(entry(capability, "VALIDATED", pecVersions));
        }
        return of(entries);
    }

    /** A matrix document of the given entries. */
    public static PecCompatibilityMatrix of(List<String> entries) {
        return PecCompatibilityMatrix.fromJson("{\"schema_version\":\"2\",\"validation_status\":\"VALIDATED\","
                + "\"tested_with\":[" + String.join(",", entries) + "]}");
    }

    /** One {@code tested_with} entry. */
    public static String entry(String capability, String status, List<String> pecVersions) {
        String hash = "sha256:" + "0".repeat(64);
        String readModel = CapabilityCatalog.packaged()
                .find(capability)
                .map(CapabilityContract::readModel)
                .orElse(CapabilityContract.DEFAULT_READ_MODEL);
        return "{\"pec_versions\":[\"" + String.join("\",\"", pecVersions) + "\"],"
                + "\"postgresql_version\":\"9.6.13\",\"adapter_version\":\"0.1.0\",\"read_model\":\"" + readModel
                + "\","
                + "\"installation_role\":\"PRONTUARIO\",\"capability\":\"" + capability + "\",\"status\":\""
                + status + "\",\"objects_used\":[{\"object\":\"tb_teste\",\"columns_used\":[\"co_seq\"],"
                + "\"signature_fingerprint\":\"" + hash + "\"}],\"municipal_isolation_evidence\":"
                + "{\"binding_column\":\"tb_dim_municipio.co_ibge\",\"mechanism\":\"teste\"},"
                + "\"query_checksum\":\"" + hash + "\",\"fixture_checksum\":\"" + hash + "\","
                + "\"test_result\":\"PASS\",\"approved_at\":\"2026-10-02\",\"approved_by\":\"teste\"}";
    }
}
