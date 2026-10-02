package esusdata.indicator.model;

import java.util.Locale;

/**
 * The record kinds of the canonical extract v2 (ADR 0030): one capability reads one kind, and the
 * kind's wire name is the {@code kind} of every extract line it writes. The fields of each record
 * are the snake_case columns its frozen query returns ("a SQL é o esquema").
 */
public enum RecordKind {
    PERSON(CanonicalPerson.class),
    REGISTRATION(CanonicalRegistration.class),
    TEAM(CanonicalTeam.class),
    CARE_EVENT(CanonicalCareEvent.class),
    PROCEDURE_EVENT(CanonicalProcedureEvent.class),
    HOME_VISIT(CanonicalHomeVisit.class),
    IMMUNIZATION(CanonicalImmunization.class),
    CONDITION(CanonicalCondition.class),
    MEASUREMENT(CanonicalMeasurement.class),
    PREGNANCY_OUTCOME(CanonicalPregnancyOutcome.class);

    private final Class<? extends Record> recordType;

    RecordKind(Class<? extends Record> recordType) {
        this.recordType = recordType;
    }

    public Class<? extends Record> recordType() {
        return recordType;
    }

    /** The name written in the extract and in capability descriptors, e.g. {@code care_event}. */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RecordKind fromWireName(String wireName) {
        for (RecordKind kind : values()) {
            if (kind.wireName().equals(wireName)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown canonical record kind: " + wireName);
    }
}
