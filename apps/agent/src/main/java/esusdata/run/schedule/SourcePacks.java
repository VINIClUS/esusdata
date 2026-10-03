package esusdata.run.schedule;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.IndicatorRule;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.CapabilityEligibility;
import esusdata.source.pec.PecSourceIdentity;
import java.util.Collection;
import java.util.List;

/**
 * Which runnable packs a PEC source can compute (ADR 0030), in the release's order — C1 first. A
 * pack is available when every capability its descriptor reads has a {@code VALIDATED} entry for
 * the source's PEC version, read model and installation role. The scheduler, the Execução screen
 * and the Painel ask the same question here, so what one shows as available is what the other
 * enqueues.
 */
public final class SourcePacks {

    private final CapabilityEligibility eligibility;

    public SourcePacks(CapabilityEligibility eligibility) {
        this.eligibility = eligibility;
    }

    /** One runnable pack and the capabilities the source lacks for it (empty: available). */
    public record Availability(IndicatorRule rule, List<String> missingCapabilities) {
        public Availability {
            missingCapabilities = List.copyOf(missingCapabilities);
        }

        public String indicatorPack() {
            return rule.descriptor().id();
        }

        public boolean available() {
            return missingCapabilities.isEmpty();
        }
    }

    /** Every runnable pack of the release for {@code source}, in registry order. */
    public List<Availability> of(SourceRecord source) {
        PecSourceIdentity identity = identity(source);
        return IndicatorRuleRegistry.all().stream()
                .map(rule -> new Availability(
                        rule, eligibility.missing(rule.descriptor().requiredCapabilities(), identity)))
                .toList();
    }

    /** The packs {@code source} can compute, in registry order (C1 first). */
    public List<IndicatorRule> eligible(SourceRecord source) {
        return of(source).stream()
                .filter(Availability::available)
                .map(Availability::rule)
                .toList();
    }

    /** The capabilities of {@code required} that {@code source} lacks, in the order given. */
    public List<String> missing(Collection<String> required, SourceRecord source) {
        return eligibility.missing(required, identity(source));
    }

    private static PecSourceIdentity identity(SourceRecord source) {
        return CapabilityEligibility.identityOf(
                        source.id(), source.pecVersion(), source.readModel(), source.pecInstallationRole())
                .orElse(null);
    }
}
