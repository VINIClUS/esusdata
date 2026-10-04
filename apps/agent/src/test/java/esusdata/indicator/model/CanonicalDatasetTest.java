package esusdata.indicator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalDatasetTest {

    private static final SourceRef REF = new SourceRef("pec-1", "tb_fat_vacinacao_vacina", "77");

    @Test
    void keepsEachKindApartWithItsWindow() {
        CanonicalImmunization dose = new CanonicalImmunization(
                REF, "3541307", "p-1", "2026-03-02", "33", "1", null, false, "223505", null, null);
        CanonicalPerson person = new CanonicalPerson(
                new SourceRef("pec-1", "tb_fat_cidadao_pec", "p-1"),
                "3541307",
                "p-1",
                "1950-01-01",
                "FEMININO",
                null,
                null);
        DateWindow window = DateWindow.lastCivilMonths(YearMonth.of(2026, 3), 12);
        CanonicalDataset data = CanonicalDataset.builder()
                .window("immunization_history", window)
                .add(dose)
                .add(RecordKind.PERSON, person)
                .build();

        assertThat(data.immunizations()).containsExactly(dose);
        assertThat(data.persons()).containsExactly(person);
        assertThat(data.careEvents()).isEmpty();
        assertThat(data.encounters()).isEmpty();
        assertThat(data.windowOf("immunization_history")).contains(window);
        assertThat(data.windowOf("home_visit")).isEmpty();
    }

    @Test
    void refusesARecordUnderTheWrongKind() {
        CanonicalPerson person = new CanonicalPerson(REF, "3541307", "p-1", "1950-01-01", null, null, null);
        assertThatThrownBy(() -> CanonicalDataset.builder().add(RecordKind.CARE_EVENT, person))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalDataset.builder().add(REF)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aV1DatasetIsItsEncountersUnderOneCapability() {
        CanonicalEncounter encounter =
                new CanonicalEncounter(REF, "3541307", "2026-03-05", CanonicalModality.PROGRAMADO, null, null, null);
        DateWindow march = new DateWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));
        CanonicalDataset data =
                CanonicalDataset.ofEncounters("individual_encounter_modality", march, List.of(encounter));
        assertThat(data.encounters()).containsExactly(encounter);
        assertThat(data.windows()).containsOnlyKeys("individual_encounter_modality");
    }

    @Test
    void listFieldsAreNeverNull() {
        CanonicalCareEvent event = new CanonicalCareEvent(
                REF,
                "3541307",
                "p-1",
                "2026-03-05",
                "INDIVIDUAL",
                "225142",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        assertThat(event.ciapCodes()).isEmpty();
        assertThat(event.proceduresPerformed()).isEmpty();
        assertThat(new CanonicalHomeVisit(REF, "3541307", "p-1", "2026-03-05", null, null, null, null, null, null, null)
                        .reasonCodes())
                .isEmpty();
        assertThat(new CanonicalPregnancyOutcome(REF, "3541307", "p-1", "2026-03-05", null, null).codes())
                .isEmpty();
    }
}
