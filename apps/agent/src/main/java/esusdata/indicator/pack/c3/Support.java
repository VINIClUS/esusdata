package esusdata.indicator.pack.c3;

/** An event behind a practice decision; {@code ambiguity} is {@code null} when it counts for sure. */
record Support(EventRef event, Ambiguity ambiguity) {}
