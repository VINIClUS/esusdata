package esusdata.source.pec;

import java.util.List;
import java.util.Objects;

/**
 * A canonical v2 capability as packaged in {@code contracts/compatibility/capabilities/<id>@<v>.json}
 * (ADR 0030): one frozen query, one record kind, its positional binds and the columns its rows must
 * carry. "A SQL é o esquema" — the column aliases are the canonical record's snake_case fields; the
 * Rust execution plane and the Java reader both read this same descriptor, so the shape never
 * drifts between them. The query text and its SHA-256 come from the packaged SQL file.
 *
 * @param recordKind the canonical record kind's wire name, e.g. {@code care_event}
 * @param scopeDateColumn the column checked against the part's window, or {@code null}
 * @param binds positional binds in the order the query's {@code ?} placeholders consume them
 */
public record CapabilityContract(
        String capability,
        String adapterVersion,
        String recordKind,
        String entityTypeColumn,
        String recordIdColumn,
        String municipalityColumn,
        String scopeDateColumn,
        String queryPath,
        List<Bind> binds,
        List<Column> columns,
        String queryText,
        String queryChecksum) {

    public CapabilityContract {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(adapterVersion, "adapterVersion");
        Objects.requireNonNull(recordKind, "recordKind");
        binds = List.copyOf(binds);
        columns = List.copyOf(columns);
    }

    /** One positional bind: {@code MUNICIPALITY_IBGE}, {@code PERIOD_START}, {@code PERIOD_END_EXCLUSIVE}, {@code DATE} or {@code TEXT_ARRAY}. */
    public record Bind(String name, String type) {}

    /** One output column: {@code text}, {@code date}, {@code integer}, {@code decimal}, {@code bool} or {@code text[]}. */
    public record Column(String name, String type, boolean required) {}

    /** The column names the query must return, in descriptor order. */
    public List<String> columnNames() {
        return columns.stream().map(Column::name).toList();
    }
}
