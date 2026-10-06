package esusdata.indicator;

import esusdata.indicator.model.GateCheck;
import esusdata.indicator.model.GateId;
import esusdata.indicator.model.GateStatus;
import java.util.Arrays;
import java.util.List;

/**
 * One release gate as the API shows it (ADR 0032): automated checks only, so there is a check id
 * and a date, never an approver.
 *
 * @param gate {@code A} to {@code D}
 * @param status {@code PENDING}, {@code PASSED} or {@code FAILED}
 * @param evidenceRefs repo-relative documents the check relied on
 */
public record GateResponse(
        String gate,
        String label,
        String status,
        String check,
        String checkedAt,
        List<String> evidenceRefs,
        String note) {

    public static List<GateResponse> of(GateStatus status) {
        return Arrays.stream(GateId.values())
                .map(id -> {
                    GateCheck check = status.check(id);
                    return new GateResponse(
                            id.name(),
                            id.label(),
                            check.state().name(),
                            check.check(),
                            check.checkedAt(),
                            check.evidence().stream()
                                    .map(GateCheck.Evidence::ref)
                                    .toList(),
                            check.note());
                })
                .toList();
    }
}
