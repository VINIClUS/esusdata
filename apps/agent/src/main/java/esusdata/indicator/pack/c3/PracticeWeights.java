package esusdata.indicator.pack.c3;

import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.Scores;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The descriptor's practice specs by {@link Practice}, and the points of a scored subject (Quadro 01). */
final class PracticeWeights {

    private final Map<Practice, ComponentSpec> specs = new EnumMap<>(Practice.class);

    PracticeWeights(List<ComponentSpec> components) {
        for (ComponentSpec spec : components) {
            specs.put(Practice.valueOf(spec.code()), spec);
        }
        if (specs.size() != Practice.values().length) {
            throw new IllegalArgumentException("the C3 descriptor must declare the practices A..K");
        }
    }

    ComponentSpec spec(Practice practice) {
        return specs.get(practice);
    }

    /** The sum of the weights met or exempt; {@code null} for a subject that was not scored. */
    BigInteger points(Subject subject) {
        if (!subject.eligible()) {
            return null;
        }
        List<ComponentSpec> satisfied = new ArrayList<>();
        for (Practice practice : Practice.values()) {
            if (subject.practice(practice).scores()) {
                satisfied.add(specs.get(practice));
            }
        }
        return Scores.points(satisfied);
    }
}
