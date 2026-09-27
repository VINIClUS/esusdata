package esusdata.report.dto;

public record ExportResponse(
        String id,
        String fileName,
        String municipalityIbge,
        String indicatorPack,
        String fromPeriod,
        String toPeriod,
        String format,
        int rowCount,
        String createdAt,
        String expiresAt) {}
