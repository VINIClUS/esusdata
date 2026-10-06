package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Classification;

/**
 * How many teams fell in each class, ordered REGULAR &lt; SUFICIENTE &lt; BOM &lt; ÓTIMO. The
 * SIAPS publishes exactly this per indicator and team type; the product builds it from the class
 * of each team.
 */
public record ClassCounts(int regular, int suficiente, int bom, int otimo) {

    public static final ClassCounts EMPTY = new ClassCounts(0, 0, 0, 0);

    /** The classes in ascending order. */
    private static final int CLASSES = 4;

    public ClassCounts {
        if (regular < 0 || suficiente < 0 || bom < 0 || otimo < 0) {
            throw new IllegalArgumentException("a class count cannot be negative");
        }
    }

    public int total() {
        return regular + suficiente + bom + otimo;
    }

    public int count(Classification classification) {
        return switch (classification) {
            case REGULAR -> regular;
            case SUFICIENTE -> suficiente;
            case BOM -> bom;
            case OTIMO -> otimo;
        };
    }

    /** This count with one more team in {@code classification}. */
    public ClassCounts plus(Classification classification) {
        return new ClassCounts(
                regular + (classification == Classification.REGULAR ? 1 : 0),
                suficiente + (classification == Classification.SUFICIENTE ? 1 : 0),
                bom + (classification == Classification.BOM ? 1 : 0),
                otimo + (classification == Classification.OTIMO ? 1 : 0));
    }

    /** Teams in the {@code k} lowest classes, {@code k} = 1..4 (4 is the total). */
    public int cumulative(int k) {
        if (k < 1 || k > CLASSES) {
            throw new IllegalArgumentException("k must be 1..4: " + k);
        }
        int[] ascending = {regular, suficiente, bom, otimo};
        int sum = 0;
        for (int i = 0; i < k; i++) {
            sum += ascending[i];
        }
        return sum;
    }
}
