package br.gov.observatorioaps.jobrunner.domain;

import java.time.Instant;

/** A source is on cooldown after an abandoned live acquisition (ENG-51). */
public final class SourceAcquisitionBlockedException extends RuntimeException {

    private final Instant blockedUntil;

    public SourceAcquisitionBlockedException(String message, Instant blockedUntil) {
        super(message);
        this.blockedUntil = blockedUntil;
    }

    /** When the cooldown this exception was thrown for actually lifts — never before this instant. */
    public Instant blockedUntil() {
        return blockedUntil;
    }
}
