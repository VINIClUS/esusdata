package esusdata.report.dto;

/**
 * {@code fromPeriod}/{@code toPeriod} are ISO year-months ({@code yyyy-MM}), inclusive; a null or
 * blank {@code indicatorPack} exports every pack.
 */
public record CreateExportRequest(String municipalityIbge, String fromPeriod, String toPeriod, String indicatorPack) {}
