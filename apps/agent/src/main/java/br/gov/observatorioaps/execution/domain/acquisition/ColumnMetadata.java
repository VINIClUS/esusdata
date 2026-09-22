package br.gov.observatorioaps.execution.domain.acquisition;

/** Raw {@code information_schema.columns} row for one compatibility object's column. */
public record ColumnMetadata(String dataType, String udtName, String isNullable, int ordinalPosition) {
}
