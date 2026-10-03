package esusdata.result.dto;

import java.util.List;

/**
 * The same result computed for one team (INE), the granularity of the fichas (ADR 0030). {@code
 * ine} null groups the records without a team — never folded into another team.
 */
public record TeamResultResponse(
        String ine,
        String cnes,
        String status,
        String value,
        ExactValue valueExact,
        String numerator,
        String denominator,
        String classification,
        boolean consolidationEligible,
        List<ResultComponentResponse> components,
        List<String> limitations) {}
