package esusdata.result;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.model.AuthenticatedSession;
import esusdata.auth.model.Permission;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.pack.componente3.ComponentIIIInput;
import esusdata.indicator.pack.componente3.ComponentIIIResult;
import esusdata.result.dto.QualityComponentResponse;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Nota Final do Componente III, computed on read from published monthly results (ADR 0030,
 * NT 8/2026). Same scope as {@code GET /results}: municipality-wide {@code READ_CLINICAL}. Until the
 * consolidation and the reading of published results land, every unit is {@code BLOCKED} with the
 * reason — never a score.
 */
@RestController
public class QualityComponentController {

    private static final int DISPLAY_SCALE = 4;

    private final ApiAuthorization authorization;

    public QualityComponentController(ApiAuthorization authorization) {
        this.authorization = authorization;
    }

    @GetMapping("/api/v1/quality-component")
    public QualityComponentResponse qualityComponent(
            @AuthenticationPrincipal AuthenticatedSession session,
            @RequestParam String municipalityIbge,
            @RequestParam String quadrimestre) {
        authorization.requireObjectScope(session, Permission.READ_CLINICAL, municipalityIbge);
        Quadrimestre q = Quadrimestre.parse(quadrimestre);
        ComponentIIIInput input =
                new ComponentIIIInput(municipalityIbge, q, List.of(new ComponentIIIInput.Unit(null, null, List.of())));
        ComponentIIIResult result = ComponentIII.consolidation().consolidate(input, Map.of());
        return toResponse(municipalityIbge, result, "");
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
                exact(u.score()),
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
                                exact(i.mean()),
                                name(i.classification()),
                                i.factor() == null
                                        ? null
                                        : i.factor().toScaledBigDecimal(2).toPlainString()))
                        .toList());
    }

    private static String decimal(ExactRatio value) {
        return value == null ? null : value.toScaledBigDecimal(DISPLAY_SCALE).toPlainString();
    }

    private static QualityComponentResponse.Exact exact(ExactRatio value) {
        return value == null
                ? null
                : new QualityComponentResponse.Exact(
                        value.numerator().toString(), value.denominator().toString());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
