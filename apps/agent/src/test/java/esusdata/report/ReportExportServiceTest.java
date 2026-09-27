package esusdata.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import esusdata.report.model.ExportQuotaExceededException;
import esusdata.report.model.ReportExport;
import esusdata.report.model.ReportExportRepository;
import esusdata.result.model.ResultRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportExportServiceTest {

    private static final String IBGE = "3541307";
    private static final String PACK = "c1-mais-acesso";
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00.750Z");

    private ReportExportRepository exports;
    private ResultRepository results;
    private ReportExportService service;

    @BeforeEach
    void setUp() {
        exports = mock(ReportExportRepository.class);
        results = mock(ResultRepository.class);
        service = new ReportExportService(exports, results, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void storesTheNewestResultsOfTheRangeWithWholeSecondInstants() {
        when(results.findLatestPublishedInRange(IBGE, PACK, "2026-01", "2026-03"))
                .thenReturn(List.of(ReportCsvTest.result("60.0000", "BOM")));
        when(exports.insertWithinQuota(any(), any(), any(), anyInt())).thenReturn(true);

        ReportExport export = service.create("user-1", IBGE, PACK, "2026-01", "2026-03");

        ArgumentCaptor<byte[]> content = ArgumentCaptor.forClass(byte[].class);
        verify(exports)
                .insertWithinQuota(
                        eq(export),
                        content.capture(),
                        eq(Instant.parse("2026-09-27T11:00:00Z")),
                        eq(ReportExportService.QUOTA));
        assertThat(new String(content.getValue(), StandardCharsets.UTF_8)).contains("\"60,0000\"");
        assertThat(export.rowCount()).isEqualTo(1);
        assertThat(export.format()).isEqualTo("CSV");
        assertThat(export.createdBy()).isEqualTo("user-1");
        assertThat(export.createdAt()).isEqualTo("2026-09-27T12:00:00Z");
        assertThat(export.expiresAt()).isEqualTo("2026-10-04T12:00:00Z");
        verify(exports).purgeExpired(Instant.parse("2026-09-27T12:00:00Z"));
    }

    @Test
    void aBlankPackMeansEveryPack() {
        when(exports.insertWithinQuota(any(), any(), any(), anyInt())).thenReturn(true);

        ReportExport export = service.create("user-1", IBGE, " ", "2026-03", "2026-03");

        verify(results).findLatestPublishedInRange(IBGE, null, "2026-03", "2026-03");
        assertThat(export.indicatorPack()).isNull();
        assertThat(ReportExportService.fileName(export)).isEqualTo("esusdata-3541307-todos-2026-03_2026-03.csv");
    }

    @Test
    void refusesWhenTheQuotaIsReached() {
        when(exports.insertWithinQuota(any(), any(), any(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.create("user-1", IBGE, PACK, "2026-01", "2026-03"))
                .isInstanceOf(ExportQuotaExceededException.class);
    }

    @Test
    void acceptsTwentyFourCompetenciasButNotTwentyFive() {
        when(exports.insertWithinQuota(any(), any(), any(), anyInt())).thenReturn(true);

        assertThat(service.create("user-1", IBGE, PACK, "2025-01", "2026-12").toPeriod())
                .isEqualTo("2026-12");
        assertThatThrownBy(() -> service.create("user-1", IBGE, PACK, "2025-01", "2027-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24");
    }

    @Test
    void rejectsMalformedOrReversedRangesAndUnknownPacksBeforeReading() {
        assertThatThrownBy(() -> service.create("user-1", IBGE, PACK, null, "2026-03"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromPeriod is required");
        assertThatThrownBy(() -> service.create("user-1", IBGE, PACK, "2026-01", "03/2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toPeriod must be");
        assertThatThrownBy(() -> service.create("user-1", IBGE, PACK, "2026-04", "2026-03"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after");
        assertThatThrownBy(() -> service.create("user-1", IBGE, "../etc", "2026-01", "2026-03"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown indicator pack");

        verify(exports, never()).insertWithinQuota(any(), any(), any(), anyInt());
    }

    @Test
    void listingPurgesAndReadsTheUnexpiredExports() {
        Instant now = Instant.parse("2026-09-27T12:00:00Z");

        service.recent(IBGE);
        service.content("exp-1", IBGE);

        verify(exports).purgeExpired(now);
        verify(exports).listRecent(IBGE, now, ReportExportService.RECENT_LIMIT);
        verify(exports).findInScope("exp-1", IBGE, now);
    }
}
