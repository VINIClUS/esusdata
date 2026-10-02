package esusdata.indicator.model;

import java.util.Objects;

/**
 * The same result computed for one team (INE), the granularity of the fichas (item 21) and of the
 * financial classification of the Componente III (NT 8/2026). {@code ine} is {@code null} for
 * events or people without a team, kept apart rather than folded into another team.
 */
public record TeamResult(String ine, String cnes, IndicatorResult result) {
    public TeamResult {
        Objects.requireNonNull(result, "result");
    }
}
