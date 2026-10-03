package esusdata.result;

import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

/**
 * The JSON columns of a staged or published result (V10, ADR 0030): its practices or subgroups
 * ({@code components_json}) and its per-team results ({@code team_results_json}). Every number is a
 * canonical integer string — exact counts and exact values as reduced fractions — never a JSON
 * number and never a {@code double} (§1.7.1). A component keeps its own status, so a {@code
 * RULE_AMBIGUITY} or {@code NO_DENOMINATOR} practice survives with its exact counts and no value.
 */
public final class ResultJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BigInteger ONE_HUNDRED = BigInteger.valueOf(100);

    private ResultJson() {}

    /** One practice or subgroup as stored; {@code value*} is null without a value. */
    public record StoredComponent(
            String code,
            String kind,
            String weight,
            String numerator,
            String denominator,
            String valueNumerator,
            String valueDenominator,
            String status) {

        /** The exact value, or {@code null}. */
        public ExactRatio value() {
            return ratio(valueNumerator, valueDenominator);
        }
    }

    /** One team's result as stored; {@code ine} null groups the records without a team. */
    public record StoredTeam(
            String ine,
            String cnes,
            String status,
            String valueText,
            String valueNumerator,
            String valueDenominator,
            String numerator,
            String denominator,
            String denominatorKind,
            String classification,
            boolean consolidationEligible,
            List<StoredComponent> components,
            List<String> limitations) {

        public StoredTeam {
            components = components == null ? List.of() : List.copyOf(components);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
        }

        /** The exact value, or {@code null}. */
        public ExactRatio value() {
            return ratio(valueNumerator, valueDenominator);
        }
    }

    /**
     * The exact value a result is stored with: the rule's own, in lowest terms. C1 keeps its
     * original result shape, which carries only the value text; a {@code COMPUTED} percentage
     * without an exact value is stored as {@code 100 × numerator / denominator}, so no reader ever
     * recomputes it. Null whenever the result has no value.
     */
    public static ExactRatio exactValue(IndicatorResult result) {
        if (result.valueExact() != null) {
            return result.valueExact().reduced();
        }
        if (result.status() == IndicatorStatus.COMPUTED
                && result.valueKind() == ValueKind.PERCENTAGE
                && result.numerator() != null
                && result.denominator() != null
                && result.denominator().signum() > 0) {
            return new ExactRatio(result.numerator().multiply(ONE_HUNDRED), result.denominator()).reduced();
        }
        return null;
    }

    public static String writeComponents(List<ResultComponent> components) {
        return MAPPER.writeValueAsString(
                components.stream().map(ResultJson::stored).toList());
    }

    public static String writeTeams(List<TeamResult> teams) {
        return MAPPER.writeValueAsString(teams.stream()
                .map(team -> {
                    IndicatorResult result = team.result();
                    ExactRatio value = exactValue(result);
                    return new StoredTeam(
                            team.ine(),
                            team.cnes(),
                            result.status().name(),
                            result.valueText(),
                            value == null ? null : value.numerator().toString(),
                            value == null ? null : value.denominator().toString(),
                            text(result.numerator()),
                            text(result.denominator()),
                            result.denominatorKind(),
                            result.classification() == null
                                    ? null
                                    : result.classification().name(),
                            result.consolidationEligible(),
                            result.components().stream().map(ResultJson::stored).toList(),
                            result.limitations());
                })
                .toList());
    }

    public static List<StoredComponent> readComponents(String json) {
        return json == null || json.isBlank() ? List.of() : List.of(MAPPER.readValue(json, StoredComponent[].class));
    }

    public static List<StoredTeam> readTeams(String json) {
        return json == null || json.isBlank() ? List.of() : List.of(MAPPER.readValue(json, StoredTeam[].class));
    }

    private static StoredComponent stored(ResultComponent component) {
        ExactRatio value = component.value() == null ? null : component.value().reduced();
        return new StoredComponent(
                component.code(),
                component.kind().name(),
                component.weight().toString(),
                component.numerator().toString(),
                component.denominator().toString(),
                value == null ? null : value.numerator().toString(),
                value == null ? null : value.denominator().toString(),
                component.status().name());
    }

    private static String text(BigInteger value) {
        return value == null ? null : value.toString();
    }

    private static ExactRatio ratio(String numerator, String denominator) {
        return numerator == null || denominator == null
                ? null
                : new ExactRatio(new BigInteger(numerator), new BigInteger(denominator));
    }
}
