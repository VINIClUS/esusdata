package esusdata.indicator.model;

import java.time.YearMonth;
import java.util.Optional;

/**
 * One indicator pack's rule (ADR 0030), pure Java (ENG-35): it never reads the source, a file or a
 * clock. The run pipeline asks it what to read, reads it through the execution plane, and hands
 * the canonical records back.
 *
 * <p>{@link #evaluate} always returns the exact counts it computed, ungated: a rule never
 * decides whether its value may be released. The release gates (ADR 0032) are the executor's job,
 * applied in one place through {@link RuleOutcomes#gate} — while any has not passed, the value and
 * classification stay unavailable ({@code BLOCKED}) and the reasons go into the limitations. A
 * caller other than the executor must not publish or expose what {@link #evaluate} returns.
 */
public interface IndicatorRule {

    PackDescriptor descriptor();

    /** The capabilities, windows and parameters one competência needs. */
    DataRequirements requirements(YearMonth competencia);

    /** Computes the competência from records already validated against {@link #requirements}. */
    RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context);

    /**
     * The ficha's band for an exact value on this pack's scale, or empty when the value lies
     * outside every band — a value the ficha does not classify is never forced into one.
     */
    Optional<Classification> classify(ExactRatio value);
}
