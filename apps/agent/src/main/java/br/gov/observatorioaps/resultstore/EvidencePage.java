package br.gov.observatorioaps.resultstore;

import java.util.List;

/** One deterministic page of evidence, plus the cursor for the next page ({@code null} if last). */
public record EvidencePage(List<EvidenceRecord> items, Long nextCursor) {
}
