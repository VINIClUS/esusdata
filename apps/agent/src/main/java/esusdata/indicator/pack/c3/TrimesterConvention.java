package esusdata.indicator.pack.c3;

/**
 * The trimester limits the ficha does not define (AMB-C3-02), in days since the DUM, both
 * inclusive: the 1º trimestre is {@code [DUM, DUM + firstTrimesterLastDay]} (truncated at D) and
 * the 3º trimestre is {@code [DUM + thirdTrimesterFirstDay, D]}. Production uses {@code (97,
 * 196)}: up to IG 13s6d and from IG 28s0d (CAB 32; PCDT de transmissão vertical).
 */
record TrimesterConvention(int firstTrimesterLastDay, int thirdTrimesterFirstDay) {

    /** The convention of the production rule. */
    static final TrimesterConvention PRODUCTION = new TrimesterConvention(97, 196);

    TrimesterConvention {
        if (firstTrimesterLastDay < 0 || thirdTrimesterFirstDay <= firstTrimesterLastDay) {
            throw new IllegalArgumentException("trimester limits must satisfy 0 <= first < third: "
                    + firstTrimesterLastDay + ", " + thirdTrimesterFirstDay);
        }
    }
}
