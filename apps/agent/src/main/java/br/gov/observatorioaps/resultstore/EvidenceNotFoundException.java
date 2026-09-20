package br.gov.observatorioaps.resultstore;

/** Thrown when a result id does not resolve within the requested municipality scope. */
public final class EvidenceNotFoundException extends RuntimeException {
    public EvidenceNotFoundException(String message) {
        super(message);
    }
}
