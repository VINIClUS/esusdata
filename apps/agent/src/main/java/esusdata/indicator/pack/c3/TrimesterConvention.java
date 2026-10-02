package esusdata.indicator.pack.c3;

/**
 * The trimester limits the ficha does not define (AMB-C3-02), in days since the DUM, both
 * inclusive: the 1º trimestre is {@code [DUM, DUM + firstTrimesterLastDay]} and the 3º trimestre
 * starts on {@code DUM + thirdTrimesterFirstDay} and ends the day before the end of the pregnancy.
 * Production runs without one (G and H stay {@code RULE_AMBIGUITY}); it is set only once the
 * convention is documented at Portão B.
 */
record TrimesterConvention(int firstTrimesterLastDay, int thirdTrimesterFirstDay) {
    TrimesterConvention {
        if (firstTrimesterLastDay < 0 || thirdTrimesterFirstDay <= firstTrimesterLastDay) {
            throw new IllegalArgumentException("trimester limits must satisfy 0 <= first < third: "
                    + firstTrimesterLastDay + ", " + thirdTrimesterFirstDay);
        }
    }
}
