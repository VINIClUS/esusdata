package br.gov.observatorioaps.execution.domain.acquisition;

import java.util.List;
import java.util.Set;

/**
 * One raw, already-fetched probe result per entry in a compatibility object's {@code
 * columns_used} list — the boundary between I/O (running the frozen probe queries) and the pure
 * signature algorithm in {@link CompatibilityFingerprint}. Every variant carries exactly what the
 * algorithm needs to reproduce its verdict and string fragment without ever touching a
 * connection again.
 */
public sealed interface ProbeItem {

    record ColumnItem(String requested) implements ProbeItem {
    }

    record UniqueKeyItem(String marker, String matchedConstraintType, boolean uniquenessViolationFound)
            implements ProbeItem {
    }

    record RequiredDimensionsItem(String marker, Long violatingFactEventId) implements ProbeItem {
    }

    record LeafSemanticsItem(String marker, List<LeafRow> rows) implements ProbeItem {
    }

    record LeafIdsItem(String marker, Set<Integer> foundIds) implements ProbeItem {
    }

    record LeafRow(int id, String description, Integer parentId) {
    }
}
