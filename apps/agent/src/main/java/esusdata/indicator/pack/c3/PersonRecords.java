package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalPregnancyOutcome;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import java.util.List;

/**
 * Every canonical record of one person (opaque {@code personKey}), each source record once
 * (MET-32). {@code individualCare} is the MIAI ({@code INDIVIDUAL}), {@code dentalCare} the MIAOI.
 */
record PersonRecords(
        String personKey,
        List<CanonicalCareEvent> individualCare,
        List<CanonicalCareEvent> dentalCare,
        List<CanonicalProcedureEvent> procedures,
        List<CanonicalHomeVisit> visits,
        List<CanonicalMeasurement> measurements,
        List<CanonicalImmunization> immunizations,
        List<CanonicalPregnancyOutcome> outcomes,
        List<CanonicalRegistration> registrations,
        List<CanonicalPerson> persons) {
    PersonRecords {
        individualCare = List.copyOf(individualCare);
        dentalCare = List.copyOf(dentalCare);
        procedures = List.copyOf(procedures);
        visits = List.copyOf(visits);
        measurements = List.copyOf(measurements);
        immunizations = List.copyOf(immunizations);
        outcomes = List.copyOf(outcomes);
        registrations = List.copyOf(registrations);
        persons = List.copyOf(persons);
    }
}
