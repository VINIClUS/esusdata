package br.gov.observatorioaps.api;

import java.util.List;

/** One deterministic page of evidence, plus the opaque cursor for the next page ({@code null} if last). */
public record EvidenceResponse(List<EvidenceEntryResponse> items, String nextCursor) {
}
