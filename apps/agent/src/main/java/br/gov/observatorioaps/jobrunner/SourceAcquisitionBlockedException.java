package br.gov.observatorioaps.jobrunner;

/** A source is on cooldown after an abandoned live acquisition (ENG-51). */
public final class SourceAcquisitionBlockedException extends RuntimeException {
    public SourceAcquisitionBlockedException(String message) {
        super(message);
    }
}
