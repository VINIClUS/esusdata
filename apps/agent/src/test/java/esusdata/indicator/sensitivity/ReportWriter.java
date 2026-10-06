package esusdata.indicator.sensitivity;

import esusdata.indicator.sensitivity.PackSensitivity.PackReport;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes the sensitivity report: a Markdown summary meant for the docs, where every count below
 * {@value #THRESHOLD} is rendered {@code <10}, and a CSV with the raw counts that must stay on the
 * local disk. Both carry aggregates by unit (the municipality or an INE) only.
 */
public final class ReportWriter {

    /** Counts below this are not shown in the Markdown. */
    public static final int THRESHOLD = 10;

    static final String MASKED = "<10";

    private static final String HEADER =
            "pack;código;leitura;unidade;componente;afetados;numerador;denominador;valor;ainda_ambíguos";
    private static final String SEPARATOR = ";";

    private ReportWriter() {}

    /** A count as the Markdown shows it: blank when absent, {@code <10} when small. */
    static String count(Long value) {
        if (value == null) {
            return "";
        }
        return value < THRESHOLD ? MASKED : Long.toString(value);
    }

    private static String count(BigInteger value) {
        if (value == null) {
            return "";
        }
        return small(value) ? MASKED : value.toString();
    }

    private static boolean small(BigInteger value) {
        return value != null && value.compareTo(BigInteger.valueOf(THRESHOLD)) < 0;
    }

    /**
     * The row as the Markdown shows it. Numerator and value are hidden when the denominator is
     * small; for a subgroup or practice (whose numerator is a count of people) also when the
     * numerator is, since the value would give it back.
     */
    static List<String> maskedCells(ReadingRow row) {
        boolean hide = small(row.denominator()) || (row.component() != null && small(row.numerator()));
        return List.of(
                row.code(),
                row.reading(),
                row.component() == null ? "" : row.component(),
                count(row.affected()),
                hide ? MASKED : row.numerator() == null ? "" : row.numerator().toString(),
                count(row.denominator()),
                hide ? MASKED : row.value() == null ? "" : row.value(),
                count(row.remaining()));
    }

    /** Writes {@code sensibilidade-<competencia>.md} and {@code .csv} under {@code directory}; returns the Markdown path. */
    public static Path write(Path directory, YearMonth competencia, List<PackReport> reports) throws IOException {
        Files.createDirectories(directory);
        Path csv = directory.resolve("sensibilidade-" + competencia + ".csv");
        Files.writeString(csv, csv(reports), StandardCharsets.UTF_8);
        Path markdown = directory.resolve("sensibilidade-" + competencia + ".md");
        Files.writeString(markdown, markdown(competencia, reports), StandardCharsets.UTF_8);
        return markdown;
    }

    /** The raw report, one row per reading and unit. Local only. */
    static String csv(List<PackReport> reports) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (PackReport report : reports) {
            for (ReadingRow row : report.rows()) {
                out.append(String.join(
                                SEPARATOR,
                                quoted(row.pack()),
                                quoted(row.code()),
                                quoted(row.reading()),
                                quoted(row.unit()),
                                quoted(row.component()),
                                text(row.affected()),
                                text(row.numerator()),
                                text(row.denominator()),
                                text(row.value()),
                                text(row.remaining())))
                        .append('\n');
            }
        }
        return out.toString();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String quoted(String value) {
        return value == null ? "" : '"' + value.replace("\"", "\"\"") + '"';
    }

    /** The summary for the docs: masked, by pack, the municipality first and then each INE. */
    static String markdown(YearMonth competencia, List<PackReport> reports) {
        List<String> lines = new ArrayList<>();
        lines.add("# Sensibilidade das ambiguidades — competência " + competencia);
        lines.add("");
        lines.add("Contagens abaixo de " + THRESHOLD + " aparecem como `" + MASKED
                + "`; numerador e valor somem quando o denominador (ou, nos subgrupos, o numerador)"
                + " é pequeno. \"Ainda ambíguos\" maior que zero indica que o valor é só o limite"
                + " inferior, com as práticas certas. Só agregados por INE; nenhum identificador.");
        lines.add("");
        for (PackReport report : reports) {
            lines.add("## " + report.pack() + " — resultado: " + report.status());
            lines.add("");
            report.notes().forEach(note -> lines.add("- " + note));
            List<ReadingRow> municipality = report.rows().stream()
                    .filter(r -> ReadingRow.MUNICIPALITY.equals(r.unit()))
                    .toList();
            List<ReadingRow> teams = report.rows().stream()
                    .filter(r -> !ReadingRow.MUNICIPALITY.equals(r.unit()))
                    .toList();
            if (!municipality.isEmpty()) {
                lines.addAll(table(municipality, false));
            }
            if (!teams.isEmpty()) {
                lines.add("### Por INE");
                lines.add("");
                lines.addAll(table(teams, true));
            }
            if (municipality.isEmpty() && teams.isEmpty()) {
                lines.add("");
            }
        }
        return String.join("\n", lines) + "\n";
    }

    private static List<String> table(List<ReadingRow> rows, boolean withUnit) {
        String prefix = withUnit ? "| INE " : "";
        List<String> lines = new ArrayList<>();
        lines.add(
                prefix + "| Código | Leitura | Comp. | Afetados | Numerador | Denominador | Valor | Ainda ambíguos |");
        lines.add((withUnit ? "|---" : "") + "|---|---|---|---|---|---|---|---|");
        for (ReadingRow row : rows) {
            String cells = maskedCells(row).stream().map(ReportWriter::cell).collect(Collectors.joining(" | "));
            lines.add((withUnit ? "| " + row.unit() + " " : "") + "| " + cells + " |");
        }
        lines.add("");
        return lines;
    }

    private static String cell(String text) {
        return Objects.requireNonNullElse(text, "").replace("|", "\\|");
    }
}
