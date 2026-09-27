package esusdata.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.model.AuthAuditWriter;
import esusdata.auth.model.AuthenticatedSession;
import esusdata.auth.model.Permission;
import esusdata.auth.model.ScopeDeniedException;
import esusdata.report.dto.CreateExportRequest;
import esusdata.report.dto.ExportResponse;
import esusdata.report.model.ReportExport;
import esusdata.report.model.ReportExportContent;
import esusdata.web.ApiNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ReportExportControllerTest {

    private static final String IBGE = "3541307";
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final AuthenticatedSession SESSION =
            new AuthenticatedSession("session-1", "user-1", NOW, NOW, NOW.plusSeconds(3600), 1, null);
    private static final ReportExport EXPORT = new ReportExport(
            "exp-1",
            IBGE,
            null,
            "2026-01",
            "2026-03",
            "CSV",
            2,
            "user-1",
            "2026-09-27T12:00:00Z",
            "2026-10-04T12:00:00Z");

    private ReportExportService service;
    private ApiAuthorization authorization;
    private AuthAuditWriter audit;
    private ReportExportController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReportExportService.class);
        authorization = mock(ApiAuthorization.class);
        audit = mock(AuthAuditWriter.class);
        controller = new ReportExportController(service, authorization, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createChecksTheMunicipalScopeThenAuditsWithoutContent() {
        when(service.create("user-1", IBGE, null, "2026-01", "2026-03")).thenReturn(EXPORT);

        ResponseEntity<ExportResponse> response =
                controller.create(SESSION, new CreateExportRequest(IBGE, "2026-01", "2026-03", null));

        verify(authorization).requireObjectScope(SESSION, Permission.READ_CLINICAL, IBGE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody())
                .isEqualTo(new ExportResponse(
                        "exp-1",
                        "esusdata-3541307-todos-2026-01_2026-03.csv",
                        IBGE,
                        null,
                        "2026-01",
                        "2026-03",
                        "CSV",
                        2,
                        "2026-09-27T12:00:00Z",
                        "2026-10-04T12:00:00Z"));
        verify(audit)
                .record(
                        NOW,
                        "user-1",
                        "EXPORT_CREATED",
                        "exp-1",
                        "SUCCESS",
                        "{\"municipalityIbge\":\"3541307\",\"fromPeriod\":\"2026-01\",\"toPeriod\":\"2026-03\","
                                + "\"rowCount\":2}");
    }

    @Test
    void createOutOfScopeNeverGenerates() {
        doThrow(new ScopeDeniedException("denied"))
                .when(authorization)
                .requireObjectScope(SESSION, Permission.READ_CLINICAL, IBGE);

        assertThatThrownBy(() -> controller.create(SESSION, new CreateExportRequest(IBGE, "2026-01", "2026-03", null)))
                .isInstanceOf(ScopeDeniedException.class);
        verifyNoInteractions(service, audit);
    }

    @Test
    void createWithoutABodyIsABadRequest() {
        assertThatThrownBy(() -> controller.create(SESSION, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }

    @Test
    void listChecksTheScope() {
        when(service.recent(IBGE)).thenReturn(List.of(EXPORT));

        assertThat(controller.list(SESSION, IBGE))
                .extracting(ExportResponse::id)
                .containsExactly("exp-1");
        verify(authorization).requireObjectScope(SESSION, Permission.READ_CLINICAL, IBGE);
    }

    @Test
    void downloadIsAnUncachedAttachmentAndIsAudited() {
        byte[] csv = "﻿x".getBytes(StandardCharsets.UTF_8);
        when(service.content("exp-1", IBGE)).thenReturn(Optional.of(new ReportExportContent(EXPORT, csv)));

        ResponseEntity<byte[]> response = controller.content(SESSION, "exp-1", IBGE);

        verify(authorization).requireObjectScope(SESSION, Permission.READ_CLINICAL, IBGE);
        assertThat(response.getBody()).isEqualTo(csv);
        assertThat(response.getHeaders().getContentType()).hasToString("text/csv;charset=UTF-8");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"esusdata-3541307-todos-2026-01_2026-03.csv\"");
        verify(audit)
                .record(NOW, "user-1", "EXPORT_DOWNLOADED", "exp-1", "SUCCESS", "{\"municipalityIbge\":\"3541307\"}");
    }

    @Test
    void anUnknownOrExpiredExportIsNotFoundAndNotAudited() {
        when(service.content("exp-1", IBGE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.content(SESSION, "exp-1", IBGE)).isInstanceOf(ApiNotFoundException.class);
        verify(audit, never())
                .record(any(), anyString(), eq("EXPORT_DOWNLOADED"), anyString(), anyString(), anyString());
    }
}
