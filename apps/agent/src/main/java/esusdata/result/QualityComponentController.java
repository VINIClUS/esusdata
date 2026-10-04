package esusdata.result;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.model.AuthenticatedSession;
import esusdata.auth.model.Permission;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.pack.componente3.ComponentIIIResult;
import esusdata.result.dto.ExactValue;
import esusdata.result.dto.QualityComponentResponse;
import java.time.YearMonth;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Nota Final do Componente III, computed on read from published monthly results (ADR 0030,
 * NT 8/2026) by {@link QualityComponentService}. Same scope as {@code GET /results}: municipality-
 * wide {@code READ_CLINICAL}. While the consolidation is not released every unit is {@code BLOCKED}
 * with the reason — never a score — and the response still names the results it read.
 */
@RestController
public class QualityComponentController {

    private static final int DISPLAY_SCALE = 4;

    private final ApiAuthorization authorization;
    private final QualityComponentService service;

    public QualityComponentController(ApiAuthorization authorization, QualityComponentService service) {
        this.authorization = authorization;
        this.service = service;
    }

    @GetMapping("/api/v1/quality-component")
    public QualityComponentResponse qualityComponent(
            @AuthenticationPrincipal AuthenticatedSession session,
            @RequestParam String municipalityIbge,
            @RequestParam String quadrimestre) {
        authorization.requireObjectScope(session, Permission.READ_CLINICAL, municipalityIbge);
        QualityComponentService.Consolidated consolidated =
                service.consolidate(municipalityIbge, Quadrimestre.parse(quadrimestre));
        return toResponse(municipalityIbge, consolidated.result(), consolidated.inputFingerprint());
    }

    static QualityComponentResponse toResponse(String municipalityIbge, ComponentIIIResult result, String fingerprint) {
        return new QualityComponentResponse(
                municipalityIbge,
                result.quadrimestre().toString(),
                result.quadrimestre().months().stream().map(YearMonth::toString).toList(),
                ComponentIII.RULE_VERSION,
                fingerprint,
                result.limitations(),
                result.units().stream().map(QualityComponentController::unit).toList());
    }

    private static QualityComponentResponse.Unit unit(ComponentIIIResult.UnitResult u) {
        return new QualityComponentResponse.Unit(
                u.ine(),
                u.cnes(),
                u.status().name(),
                decimal(u.score()),
                ExactValue.of(u.score()),
                name(u.methodologicalClassification()),
                name(u.financialTransferClassification()),
                u.limitations(),
                u.indicators().stream()
                        .map(i -> new QualityComponentResponse.Indicator(
                                i.indicatorPack(),
                                i.weight().toString(),
                                i.status().name(),
                                i.monthsUsed().stream().map(YearMonth::toString).toList(),
                                i.resultIds(),
                                decimal(i.mean()),
                                ExactValue.of(i.mean()),
                                name(i.classification()),
                                i.factor() == null
                                        ? null
                                        : i.factor().toScaledBigDecimal(2).toPlainString()))
                        .toList());
    }

    private static String decimal(ExactRatio value) {
        return value == null ? null : value.toScaledBigDecimal(DISPLAY_SCALE).toPlainString();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
