package esusdata.indicator.model;

/** What one evidence row is about (ADR 0030). */
public enum EvidenceSubjectKind {
    /** A source record: an encounter, a visit, a dose (C1's only kind). */
    EVENT,
    /** A person in the denominator of a per-person indicator (C2, C4–C7). */
    PERSON,
    /** A care episode, e.g. one pregnancy of a person (C3). */
    EPISODE
}
