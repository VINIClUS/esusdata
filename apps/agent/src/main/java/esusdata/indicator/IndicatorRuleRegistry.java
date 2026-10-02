package esusdata.indicator;

import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.c3.C3Pack;
import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.pack.c5.C5Pack;
import esusdata.indicator.pack.c6.C6Pack;
import esusdata.indicator.pack.c7.C7Pack;
import java.util.List;
import java.util.Optional;

/**
 * The compiled indicator rules of this release (ADR 0030, §1.5): no plugin, no upload, no remote
 * loading. A run names a pack and a rule version; anything else is refused before any I/O, the
 * way {@code RunExecutor.requireC1} refused anything but C1.
 */
public final class IndicatorRuleRegistry {

    private static final List<IndicatorRule> RULES =
            List.of(new C1Pack(), new C2Pack(), new C3Pack(), new C4Pack(), new C5Pack(), new C6Pack(), new C7Pack());

    private IndicatorRuleRegistry() {}

    public static List<IndicatorRule> all() {
        return RULES;
    }

    public static Optional<IndicatorRule> find(String pack) {
        return RULES.stream().filter(r -> r.descriptor().id().equals(pack)).findFirst();
    }

    /** The rule a job asks for, or {@link IllegalArgumentException} (→ {@code INVALID_REQUEST}). */
    public static IndicatorRule require(String pack, String ruleVersion) {
        IndicatorRule rule =
                find(pack).orElseThrow(() -> new IllegalArgumentException("unknown indicator pack " + pack));
        if (!rule.descriptor().ruleVersion().equals(ruleVersion)) {
            throw new IllegalArgumentException("job requests " + pack + "@" + ruleVersion
                    + " but this release computes " + rule.descriptor().ruleVersion());
        }
        return rule;
    }
}
