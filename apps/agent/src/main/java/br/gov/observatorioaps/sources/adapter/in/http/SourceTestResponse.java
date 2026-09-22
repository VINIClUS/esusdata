package br.gov.observatorioaps.sources.adapter.in.http;

/** {@code detail} never contains the secret (§1.12.7 L550) — only a connection-class message. */
public record SourceTestResponse(String outcome, String detail, long maxRows, long maxDurationMs, long statementTimeoutMs) {
}
