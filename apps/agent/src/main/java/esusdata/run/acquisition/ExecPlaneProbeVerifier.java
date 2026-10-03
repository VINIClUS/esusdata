package esusdata.run.acquisition;

import esusdata.source.pec.ColumnMetadata;
import esusdata.source.pec.CompatibilityFingerprint;
import esusdata.source.pec.CompatibilityProbeResult;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ProbeItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * The ENG-43 compatibility verdict on a child's {@code probe} message, shared by every
 * conversation that runs a frozen query — the acquisition ({@link ExecPlaneAcquisition}) and the
 * municipal isolation check ({@link ExecPlaneIsolationCheck}). One copy, so both refuse the same
 * schema drift the same way; a canonical v2 acquisition (ADR 0030) runs the same check once per part.
 */
final class ExecPlaneProbeVerifier {

    private static final Logger log = LoggerFactory.getLogger(ExecPlaneProbeVerifier.class);

    private static final String POSTGRES_VERSION = "postgres_version";
    private static final String QUERY_CHECKSUM = "query_checksum";

    private ExecPlaneProbeVerifier() {}

    /**
     * Compares the child's probe with the matrix entry for this capability and source.
     *
     * @param expectedQueryChecksum the checksum of the query text this JVM packaged — the matrix
     *                              and the child must both agree with it
     * @return {@code null} when everything matches, otherwise why it does not
     */
    static String mismatch(
            PecCompatibilityMatrix matrix,
            String capability,
            String adapterVersion,
            String expectedQueryChecksum,
            PecSourceIdentity identity,
            JsonNode probe) {
        return mismatch(
                matrix,
                capability,
                adapterVersion,
                expectedQueryChecksum,
                identity,
                ExecPlaneProcess.text(probe, POSTGRES_VERSION),
                ExecPlaneProcess.text(probe, QUERY_CHECKSUM),
                probe.get("objects"));
    }

    /**
     * The canonical v2 probe (ADR 0030): one report per requested part, in part order, each checked
     * against its own capability's exact matrix entry exactly as {@link #mismatch} checks C1's one
     * capability — the part's query checksum from the command, the child's from its report.
     *
     * @return {@code null} when every part matches, otherwise the first part that does not, and why
     */
    static String mismatchV2(
            PecCompatibilityMatrix matrix, List<AcquisitionPart> parts, PecSourceIdentity identity, JsonNode probe) {
        JsonNode reported = probe.get("parts");
        if (reported == null || !reported.isArray() || reported.size() != parts.size()) {
            int count = reported == null || !reported.isArray() ? 0 : reported.size();
            return "execution plane probed " + count + " parts for the " + parts.size() + " requested";
        }
        String postgresVersion = ExecPlaneProcess.text(probe, POSTGRES_VERSION);
        for (int index = 0; index < parts.size(); index++) {
            AcquisitionPart part = parts.get(index);
            JsonNode partProbe = reported.get(index);
            String probedCapability = ExecPlaneProcess.text(partProbe, "capability");
            String probedVersion = ExecPlaneProcess.text(partProbe, "adapter_version");
            String label = "part " + index + " (" + part.capability() + "): ";
            if (partProbe.path("index").asInt(-1) != index
                    || !part.capability().equals(probedCapability)
                    || !part.adapterVersion().equals(probedVersion)) {
                return label + "execution plane probed " + probedCapability + "@" + probedVersion + " at index "
                        + partProbe.path("index");
            }
            String mismatch = mismatch(
                    matrix,
                    part.capability(),
                    part.adapterVersion(),
                    part.queryChecksum(),
                    identity,
                    postgresVersion,
                    ExecPlaneProcess.text(partProbe, QUERY_CHECKSUM),
                    partProbe.get("objects"));
            if (mismatch != null) {
                return label + mismatch;
            }
        }
        return null;
    }

    private static String mismatch(
            PecCompatibilityMatrix matrix,
            String capability,
            String adapterVersion,
            String expectedQueryChecksum,
            PecSourceIdentity identity,
            String postgresVersion,
            String probeQueryChecksum,
            JsonNode objects) {
        PecCompatibilityMatrix.Entry entry;
        try {
            entry = matrix.findExact(capability, adapterVersion, identity, postgresVersion);
        } catch (RuntimeException noEntry) { // NOPMD - any lookup failure is a reported compatibility mismatch
            return "no compatibility matrix entry: " + noEntry.getMessage();
        }
        if (!expectedQueryChecksum.equals(entry.queryChecksum())) {
            return "query checksum mismatch: matrix has " + entry.queryChecksum();
        }
        if (!entry.queryChecksum().equals(probeQueryChecksum)) {
            // The one place the child's own query text is checked against the frozen contract
            // (plan §2.3) — without this, a child running a different query would still pass.
            return "query checksum mismatch: matrix has " + entry.queryChecksum() + " but execution plane reported "
                    + probeQueryChecksum;
        }
        for (Map.Entry<String, String> expected : entry.objectFingerprints().entrySet()) {
            String object = expected.getKey();
            JsonNode objectNode = objects == null ? null : objects.get(object);
            if (objectNode == null) {
                return "no probe data reported for object " + object;
            }
            String actual;
            try {
                CompatibilityProbeResult probeResult = buildProbeResult(
                        object, objectNode, entry.objectColumns().get(object));
                actual = CompatibilityFingerprint.compute(probeResult);
            } catch (RuntimeException invalid) { // NOPMD - any fingerprint failure is a reported compatibility mismatch
                return "could not compute fingerprint for " + object + ": " + invalid.getMessage();
            }
            if (!expected.getValue().equals(actual)) {
                return "fingerprint mismatch for " + object + ": expected " + expected.getValue() + " but computed "
                        + actual;
            }
            // Evidence trail: the fingerprint computed from what the child measured, per object.
            log.info(
                    "execution plane probe matched {} for {} of source {} (PEC {}): {}",
                    object,
                    capability,
                    identity.sourceId(),
                    identity.pecVersion(),
                    actual);
        }
        return null;
    }

    /**
     * The child never reports a fingerprint string — only the raw data it measured (column
     * metadata, and one raw result per {@code columns_used} marker). This class computes the
     * fingerprint itself via {@link CompatibilityFingerprint#compute}, the exact same algorithm
     * the test-only {@code JdbcCompatibilityCatalog} uses (plan §1.3/§2.2) — there is no second implementation
     * of the ENG-43 signature algorithm for a child to drift from.
     */
    private static CompatibilityProbeResult buildProbeResult(
            String object, JsonNode objectNode, List<String> columnsUsed) {
        List<ProbeItem> items = new ArrayList<>();
        for (String requested : columnsUsed) {
            items.add(probeItem(object, objectNode, requested));
        }
        return new CompatibilityProbeResult(object, columnMetadata(objectNode), items);
    }

    private static Map<String, ColumnMetadata> columnMetadata(JsonNode objectNode) {
        Map<String, ColumnMetadata> columns = new LinkedHashMap<>();
        JsonNode columnsNode = objectNode.get("columns");
        if (columnsNode != null) {
            for (JsonNode column : columnsNode) {
                columns.put(
                        ExecPlaneProcess.text(column, "name"),
                        new ColumnMetadata(
                                ExecPlaneProcess.text(column, "data_type"),
                                ExecPlaneProcess.text(column, "udt_name"),
                                ExecPlaneProcess.text(column, "is_nullable"),
                                column.path("ordinal_position").asInt(0)));
            }
        }
        return columns;
    }

    private static ProbeItem probeItem(String object, JsonNode objectNode, String requested) {
        if (requested.startsWith("UNIQUE_KEY=")) {
            JsonNode uniqueKey = objectNode.get("unique_key");
            String matchedType = uniqueKey == null ? null : ExecPlaneProcess.text(uniqueKey, "matched_constraint_type");
            boolean violation = uniqueKey != null
                    && uniqueKey.path("uniqueness_violation_found").asBoolean(false);
            return new ProbeItem.UniqueKeyItem(requested, matchedType, violation);
        }
        if (requested.startsWith("REQUIRED_DIMENSIONS=")) {
            return requiredDimensionsItem(object, objectNode, requested);
        }
        if (requested.startsWith("LEAF_SEMANTICS=")) {
            return leafSemanticsItem(objectNode, requested);
        }
        if (requested.startsWith("LEAF_IDS=")) {
            return leafIdsItem(objectNode, requested);
        }
        return new ProbeItem.ColumnItem(requested);
    }

    private static ProbeItem requiredDimensionsItem(String object, JsonNode objectNode, String requested) {
        JsonNode requiredDimensions = objectNode.get("required_dimensions");
        // Neither the block nor its field being entirely absent is the same claim as the
        // field being explicitly null (a completed probe that found no violation) — a
        // child built against a mismatched protocol that drops either one must not be
        // read as "coverage OK" by default.
        if (requiredDimensions == null || !requiredDimensions.has("violating_fact_event_id")) {
            throw new IllegalStateException("execution plane omitted required_dimensions evidence for " + object);
        }
        JsonNode idNode = requiredDimensions.get("violating_fact_event_id");
        Long violatingFactId = idNode.isNull() ? null : idNode.asLong();
        return new ProbeItem.RequiredDimensionsItem(requested, violatingFactId);
    }

    private static ProbeItem leafSemanticsItem(JsonNode objectNode, String requested) {
        List<ProbeItem.LeafRow> rows = new ArrayList<>();
        JsonNode leafSemantics = objectNode.get("leaf_semantics");
        JsonNode rowsNode = leafSemantics == null ? null : leafSemantics.get("rows");
        if (rowsNode != null) {
            for (JsonNode row : rowsNode) {
                JsonNode parentNode = row.get("parent_id");
                Integer parent = (parentNode == null || parentNode.isNull()) ? null : parentNode.asInt();
                rows.add(new ProbeItem.LeafRow(
                        row.path("id").asInt(0), ExecPlaneProcess.text(row, "description"), parent));
            }
        }
        return new ProbeItem.LeafSemanticsItem(requested, rows);
    }

    private static ProbeItem leafIdsItem(JsonNode objectNode, String requested) {
        Set<Integer> found = new HashSet<>();
        JsonNode leafIds = objectNode.get("leaf_ids");
        JsonNode foundIdsNode = leafIds == null ? null : leafIds.get("found_ids");
        if (foundIdsNode != null) {
            for (JsonNode id : foundIdsNode) {
                found.add(id.asInt());
            }
        }
        return new ProbeItem.LeafIdsItem(requested, found);
    }
}
