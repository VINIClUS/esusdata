package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.Comparison.RowResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The summary document of one pack's Portão D result, the evidence the gate points at: one row per
 * indicator and team type with N_S and N_L (written {@code <10} below 10), D, T and the verdict.
 * Per-class counts and per-INE classes never appear here (see {@link RawWriter}).
 */
public final class SummaryWriter {

    static final String MASKED = "<10";
    static final int MASK_BELOW = 10;

    private SummaryWriter() {}

    /** A count as the document shows it. */
    public static String mask(int count) {
        return count < MASK_BELOW ? MASKED : Integer.toString(count);
    }

    /** The file name of a verdict's summary. */
    public static String fileName(PackVerdict verdict) {
        return (verdict.purpose() == ReferencePurpose.DIAGNOSTIC ? "diagnostico-" : "") + "portao-d-"
                + verdict.pack().packId() + "-" + verdict.quadrimestre() + ".md";
    }

    /**
     * Writes the summary of a verdict that compared something into {@code directory}; a pending
     * verdict without a reference writes nothing (a pending gate carries no evidence).
     */
    public static Optional<Path> write(Path directory, PackVerdict verdict, LocalDate date) throws IOException {
        if (verdict.quadrimestre() == null) {
            return Optional.empty();
        }
        Files.createDirectories(directory);
        Path file = directory.resolve(fileName(verdict));
        Files.writeString(file, render(verdict, date), StandardCharsets.UTF_8);
        return Optional.of(file);
    }

    public static String render(PackVerdict verdict, LocalDate date) {
        StringBuilder text = new StringBuilder(2048);
        text.append("# Portão D: conciliação com o SIAPS, %s (%s)\n\n"
                .formatted(verdict.pack().code(), verdict.quadrimestre()));
        if (verdict.purpose() == ReferencePurpose.DIAGNOSTIC) {
            text.append("""
                    > **Modo diagnóstico: não é evidência do Portão D.** A referência não decide o portão (não é
                    > uma referência de gate, ou a execução foi exploratória) e este documento nunca entra no
                    > registro de portões.

                    """);
        }
        String reason = verdict.reason().isEmpty() ? "" : " (" + verdict.reason() + ")";
        text.append("""
                - Check: `%s`
                - Pack: `%s`, regra `%s`
                - Quadrimestre de referência no SIAPS: %s
                - Data: %s
                %s- Veredito do pack: **%s**%s

                | Indicador | Tipo | N_S | N_L | Sem classe local | D | T | Veredito |
                |---|---|---|---|---|---|---|---|
                """.formatted(
                        verdict.pack().checkId(),
                        verdict.pack().packId(),
                        verdict.ruleVersion(),
                        verdict.quadrimestre(),
                        date,
                        fingerprintLine(verdict),
                        verdict.status(),
                        reason));
        for (RowResult row : verdict.rows()) {
            text.append(tableRow(verdict.pack().code(), row)).append('\n');
        }
        text.append(
                """

                Equipes locais fora da lista do SIAPS (excluídas): %s.

                Contagens por classe e classes por equipe ficam só no diretório local ignorado pelo controle de versão.
                Regra: `%s`.
                """.formatted(mask(verdict.localNotInSiaps()), verdict.pack().ruleDocument()));
        return text.toString();
    }

    /** The line that says which local extracts the figures come from, when the verdict read any. */
    private static String fingerprintLine(PackVerdict verdict) {
        return verdict.localSourceFingerprint().isEmpty()
                ? ""
                : "- Fingerprint da fonte local: `" + verdict.localSourceFingerprint() + "`\n";
    }

    private static String tableRow(String code, RowResult row) {
        String distance = row.evaluated() ? Integer.toString(row.distance()) : "-";
        String threshold = row.evaluated() ? Integer.toString(row.threshold()) : "-";
        return "| "
                + String.join(
                        " | ",
                        code,
                        row.teamType(),
                        mask(row.siapsTeams()),
                        mask(row.localTeams()),
                        mask(row.semClasseLocal()),
                        distance,
                        threshold,
                        verdictOf(row))
                + " |";
    }

    private static String verdictOf(RowResult row) {
        if (row.evaluated()) {
            return row.passed() ? "passa" : "reprova";
        }
        return "não avaliada";
    }

    /** The lowercase hex SHA-256 of a file. */
    public static String sha256(Path file) throws IOException {
        return sha256(Files.readAllBytes(file));
    }

    /** The lowercase hex SHA-256 of {@code content}. */
    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
