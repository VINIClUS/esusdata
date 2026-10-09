package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.DiagnosticMatrix.Cell;
import esusdata.indicator.reconciliation.DiagnosticMatrix.Figures;
import esusdata.indicator.reconciliation.DiagnosticMatrix.ProbeRow;
import esusdata.indicator.reconciliation.DiagnosticMatrix.Row;
import esusdata.run.worker.SourceIdentity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes a {@link DiagnosticMatrix} as {@code matriz.md} (for people) and {@code matriz.json} (for
 * tools) into a directory of its own under {@code <artifact-dir>/diagnostico/}, named by the moment
 * the run ended. The directory and both files are created, never overwritten, and nothing is
 * written anywhere else: not under {@code docs/}, not in a registry.
 */
final class DiagnosticMatrixWriter {

    static final String DIRECTORY = "diagnostico";
    static final String MARKDOWN = "matriz.md";
    static final String JSON = "matriz.json";
    static final String LOCAL_DETAIL = "probes-detalhe-local.txt";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> INTRODUCTION = List.of(
            "# Portão D: matriz diagnóstica dos períodos capturados",
            "",
            "> **Modo diagnóstico: não é evidência do Portão D.** Cada referência é comparada como",
            "> diagnóstico; os períodos são os dos manifestos capturados e nada daqui entra no registro",
            "> de portões nem em docs/. Contagens de equipes abaixo de 10 aparecem como `<10`.",
            "");
    private static final List<String> MATRIX_HEADER = List.of(
            "",
            "## Matriz",
            "",
            "| Período | Pack | Referência | Veredito | Tipo | N_S | N_L | Sem classe local | D | T | Linha"
                    + " | Fora da lista do SIAPS | Fingerprint da fonte local |",
            "|---|---|---|---|---|---|---|---|---|---|---|---|---|");
    private static final List<String> PROBES_HEADER = List.of(
            "",
            "## Probes metodológicos",
            "",
            "Diagnóstico: o que cada probe mediu na entrada da célula. Divergentes são equipes da revisão cujo"
                    + " resultado muda sob a outra leitura; `NONE` não tem contagem, e não é zero. Um canal coberto"
                    + " por limitação declarada fica fora do veredito: o probe é completo no resto e cita a limitação.",
            "",
            "| Período | Pack | Referência | Probe | Observabilidade | Afetados | Divergentes | Limitações declaradas"
                    + " | Motivo |",
            "|---|---|---|---|---|---|---|---|---|");
    private static final String OBSERVABILITY = "observability";
    private static final String PROBE_ID = "probe_id";
    private static final String REASON = "reason";
    private static final List<String> PERIODS_HEADER =
            List.of("", "## Períodos", "", "| Período | Execução | Meses locais ausentes |", "|---|---|---|");

    private DiagnosticMatrixWriter() {}

    /**
     * Writes the two files.
     *
     * @return the directory created, {@code <artifactDir>/diagnostico/<yyyyMMdd'T'HHmmss'Z'>}
     * @throws java.nio.file.FileAlreadyExistsException if a matrix of the same second exists
     */
    static Path write(DiagnosticMatrix matrix, Path artifactDir) throws IOException {
        Path parent = artifactDir.resolve(DIRECTORY);
        Files.createDirectories(parent);
        Path directory = Files.createDirectory(parent.resolve(STAMP.format(matrix.generatedAt())));
        Files.writeString(
                directory.resolve(MARKDOWN), markdown(matrix), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(directory.resolve(JSON), json(matrix), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return directory;
    }

    static String markdown(DiagnosticMatrix matrix) {
        SourceIdentity source = matrix.source();
        List<String> lines = new ArrayList<>(INTRODUCTION);
        lines.add("- Gerada em: " + matrix.generatedAt());
        lines.add("- Fonte local: `%s`, PEC %s, PostgreSQL %s, município %s, modelo %s"
                .formatted(
                        source.pecSourceId(),
                        source.pecVersion(),
                        source.postgresVersion(),
                        source.municipalityIbge(),
                        source.readModel()));
        lines.add("- Células: " + summary(matrix));
        lines.addAll(PERIODS_HEADER);
        for (PeriodExecutionPlan period : matrix.plan()) {
            lines.add(tableRow(
                    SiapsFormats.quadrimestre(period.quadrimestre()),
                    period.decision().name(),
                    period.runs() ? Row.NONE : String.join(", ", months(period))));
        }
        lines.addAll(MATRIX_HEADER);
        matrix.rows().forEach(row -> lines.addAll(rowLines(row)));
        lines.addAll(reasons(matrix.rows()));
        if (!matrix.probes().isEmpty()) {
            lines.addAll(PROBES_HEADER);
            matrix.probes().forEach(probe -> lines.add(probeLine(probe)));
        }
        return String.join("\n", lines) + "\n";
    }

    private static String probeLine(ProbeRow probe) {
        return tableRow(
                SiapsFormats.quadrimestre(probe.period()),
                probe.pack(),
                probe.referenceId(),
                probe.result().get(PROBE_ID),
                probe.result().get(OBSERVABILITY),
                probe.result().getOrDefault("affected", Row.NONE),
                probe.result().getOrDefault("divergent", Row.NONE),
                probe.result().getOrDefault("limitations", Row.NONE),
                probe.result().getOrDefault(REASON, "").replace("|", "/"));
    }

    /**
     * Writes the local detail of the probes (one line per team and month that changed, INEs
     * included) next to the matrix, readable by the owner only where the file system allows it. It
     * is for the person who runs the diagnostic and is never versioned or copied to {@code docs/}.
     *
     * @return the file written
     */
    static Path writeLocalDetail(DiagnosticMatrix matrix, Path directory) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# LOCAL: detalhe por equipe dos probes. Não versionar e não copiar para docs/.");
        for (ProbeRow probe : matrix.probes()) {
            String prefix = "%s %s %s %s: "
                    .formatted(
                            SiapsFormats.quadrimestre(probe.period()),
                            probe.pack(),
                            probe.referenceId(),
                            probe.result().get(PROBE_ID));
            probe.localDetail().forEach(line -> lines.add(prefix + line));
        }
        Path file = directory.resolve(LOCAL_DETAIL);
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } else {
            Files.createFile(file);
        }
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        return file;
    }

    private static String summary(DiagnosticMatrix matrix) {
        List<String> counts = new ArrayList<>();
        for (Cell cell : Cell.values()) {
            counts.add(cell + " " + matrix.count(cell));
        }
        return String.join(", ", counts);
    }

    private static List<String> months(PeriodExecutionPlan period) {
        return period.missingMonths().stream().map(Object::toString).toList();
    }

    private static String tableRow(String... cells) {
        return "| " + String.join(" | ", cells) + " |";
    }

    /** One line per team type compared, or one line of dashes for a cell with no figures. */
    private static List<String> rowLines(Row row) {
        String period = SiapsFormats.quadrimestre(row.period());
        String fingerprint = row.fingerprint().isEmpty() ? Row.NONE : "`" + row.fingerprint() + "`";
        if (row.figures().isEmpty()) {
            return List.of(tableRow(
                    period,
                    row.pack(),
                    row.referenceId(),
                    row.status().name(),
                    Row.NONE,
                    Row.NONE,
                    Row.NONE,
                    Row.NONE,
                    Row.NONE,
                    Row.NONE,
                    Row.NONE,
                    row.notInSiaps(),
                    fingerprint));
        }
        return row.figures().stream()
                .map(figures -> tableRow(
                        period,
                        row.pack(),
                        row.referenceId(),
                        row.status().name(),
                        figures.teamType(),
                        figures.siapsTeams(),
                        figures.localTeams(),
                        figures.withoutLocalClass(),
                        figures.distance(),
                        figures.threshold(),
                        figures.line(),
                        row.notInSiaps(),
                        fingerprint))
                .toList();
    }

    private static List<String> reasons(List<Row> rows) {
        List<String> lines = new ArrayList<>();
        for (Row row : rows) {
            if (!row.reason().isEmpty()) {
                lines.add("- %s %s %s, %s: %s"
                        .formatted(
                                SiapsFormats.quadrimestre(row.period()),
                                row.pack(),
                                row.referenceId(),
                                row.status(),
                                row.reason()));
            }
        }
        if (lines.isEmpty()) {
            return lines;
        }
        lines.addFirst("");
        lines.addFirst("## Motivos");
        lines.addFirst("");
        return lines;
    }

    static String json(DiagnosticMatrix matrix) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schema_version", "1");
        root.put("purpose", matrix.purpose().name());
        root.put("generated_at", matrix.generatedAt().toString());
        SourceIdentity source = matrix.source();
        ObjectNode identity = root.putObject("source");
        identity.put("pec_source_id", source.pecSourceId());
        identity.put("pec_version", source.pecVersion());
        identity.put("postgres_version", source.postgresVersion());
        identity.put("municipality_ibge", source.municipalityIbge());
        identity.put("read_model", source.readModel());
        ArrayNode periods = root.putArray("periods");
        for (PeriodExecutionPlan period : matrix.plan()) {
            ObjectNode entry = periods.addObject();
            entry.put("quadrimestre", SiapsFormats.quadrimestre(period.quadrimestre()));
            entry.put("decision", period.decision().name());
            ArrayNode missing = entry.putArray("missing_months");
            months(period).forEach(missing::add);
        }
        ArrayNode rows = root.putArray("rows");
        for (Row row : matrix.rows()) {
            rows.add(rowNode(row));
        }
        ArrayNode probes = root.putArray("probes");
        for (ProbeRow probe : matrix.probes()) {
            ObjectNode node = probes.addObject();
            node.put("quadrimestre", SiapsFormats.quadrimestre(probe.period()));
            node.put("pack", probe.pack());
            node.put("reference_id", probe.referenceId());
            probe.result().forEach(node::put);
        }
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n";
    }

    private static ObjectNode rowNode(Row row) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("quadrimestre", SiapsFormats.quadrimestre(row.period()));
        node.put("pack", row.pack());
        node.put("reference_id", row.referenceId());
        node.put("status", row.status().name());
        node.put("reason", row.reason());
        node.put("not_in_siaps", row.notInSiaps());
        node.put("local_source_fingerprint", row.fingerprint());
        ArrayNode types = node.putArray("team_types");
        for (Figures figures : row.figures()) {
            ObjectNode type = types.addObject();
            type.put("team_type", figures.teamType());
            type.put("n_s", figures.siapsTeams());
            type.put("n_l", figures.localTeams());
            type.put("without_local_class", figures.withoutLocalClass());
            type.put("d", figures.distance());
            type.put("t", figures.threshold());
            type.put("line", figures.line());
        }
        return node;
    }
}
