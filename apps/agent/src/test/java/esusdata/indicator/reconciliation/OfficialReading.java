package esusdata.indicator.reconciliation;

/**
 * What an official edition of the SIAPS methodology says about one dimension of a local rule,
 * against the reading the profile records for the local rule (spec §8.3). It is a statement about
 * the normative text; whether a reference is compatible is never read off it alone, the probes
 * decide that (spec §9.5).
 */
public enum OfficialReading {
    /** The edition reads the dimension as the local rule does. */
    SAME,
    /** The edition reads it another way; the profile quotes how ({@code official_reading_text}). */
    DIFFERENT,
    /** The sources do not say, or were not found. It is never taken as {@link #SAME}. */
    UNKNOWN
}
