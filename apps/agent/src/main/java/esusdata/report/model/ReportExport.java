package esusdata.report.model;

/**
 * One stored aggregate export (ADR 0024), without its content. {@code indicatorPack} is null when
 * it covers every pack; the periods are {@code yyyy-MM}, inclusive; the instants are ISO-8601 UTC.
 */
public record ReportExport(
        String exportId,
        String municipalityIbge,
        String indicatorPack,
        String fromPeriod,
        String toPeriod,
        String format,
        int rowCount,
        String createdBy,
        String createdAt,
        String expiresAt) {}
