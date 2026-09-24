package esusdata.source.pec;

import java.util.List;
import java.util.Map;

/**
 * Raw data one probe of a compatibility object gathered — {@code columns} is every column
 * {@code information_schema.columns} reported (fetched once, regardless of which of them are
 * actually requested), and {@code items} is one entry per requested {@code columns_used} string,
 * in the same order. {@link CompatibilityFingerprint#compute} turns this into the signature.
 */
public record CompatibilityProbeResult(String object, Map<String, ColumnMetadata> columns, List<ProbeItem> items) {}
