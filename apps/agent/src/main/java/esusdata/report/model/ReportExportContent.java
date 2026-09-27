package esusdata.report.model;

/** An export and its file, read back only while it has not expired. */
public final class ReportExportContent {

    private final ReportExport export;
    private final byte[] content;

    public ReportExportContent(ReportExport export, byte[] content) {
        this.export = export;
        this.content = content.clone();
    }

    public ReportExport export() {
        return export;
    }

    public byte[] content() {
        return content.clone();
    }
}
