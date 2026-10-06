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
 * @param readModel the read model the capability is validated for, {@code PEC_DW} unless its descriptor
 *     says {@code PEC_OLTP} (ADR 0031); the matrix entry is compared with this, not with the source's
 * @param recordKind the canonical record kind's wire name, e.g. {@code care_event}
 * @param scopeDateColumn the column checked against the part's window, or {@code null}
 * @param binds positional binds in the order the query's {@code ?} placeholders consume them
 */
public record CapabilityContract(
        String capability,
        String adapterVersion,
        String readModel,
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

    /** The model of every capability whose descriptor does not name one. */
    public static final String DEFAULT_READ_MODEL = "PEC_DW";

    public CapabilityContract {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(adapterVersion, "adapterVersion");
        Objects.requireNonNull(readModel, "readModel");
        Objects.requireNonNull(recordKind, "recordKind");
        binds = List.copyOf(binds);
        columns = List.copyOf(columns);
    }

    /** A DW capability: the descriptors that do not name a read model. */
    public CapabilityContract(
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
        this(
                capability,
                adapterVersion,
                DEFAULT_READ_MODEL,
                recordKind,
                entityTypeColumn,
                recordIdColumn,
                municipalityColumn,
                scopeDateColumn,
                queryPath,
                binds,
                columns,
                queryText,
                queryChecksum);
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
