package esusdata.indicator.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalFixturesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @Test
    void buildsRecordsOfEveryKindInTheSyntheticMunicipality() {
        CanonicalDataset data = CanonicalDataset.builder()
                .add(CanonicalFixtures.person("p", LocalDate.of(1950, 1, 1), "FEMININO"))
                .add(CanonicalFixtures.person("q", LocalDate.of(1990, 1, 1), "MASCULINO", "HOMEM_TRANSEXUAL"))
                .add(CanonicalFixtures.registration("p", DAY, "1234567", "0000000001"))
                .add(CanonicalFixtures.encounter("p", DAY, "225142", true))
                .add(CanonicalFixtures.encounterWithProblems("p", DAY, "225142", List.of("T90"), List.of("E11")))
                .add(CanonicalFixtures.encounterWithMeasures("p", DAY, "223505", "70.5", "160", "120", "80"))
                .add(CanonicalFixtures.procedure("p", DAY, "0301100039", "PERFORMED", "322205"))
                .add(CanonicalFixtures.visit("p", DAY, "515105", "1"))
                .add(CanonicalFixtures.dose("p", DAY, "33", "1"))
                .add(CanonicalFixtures.condition("p", "CIAP2", "T90", DAY, "ATIVO"))
                .add(CanonicalFixtures.measurement("p", DAY, "70", "160", "MIAC"))
                .build();
        assertThat(data.persons())
                .hasSize(2)
                .allSatisfy(p -> assertThat(p.municipalityIbge()).isEqualTo(CanonicalFixtures.IBGE));
        assertThat(data.careEvents()).hasSize(3);
        assertThat(data.careEvents().get(0).remote()).isTrue();
        assertThat(data.registrations())
                .singleElement()
                .satisfies(r -> assertThat(r.ine()).isEqualTo("0000000001"));
        assertThat(data.procedureEvents()).hasSize(1);
        assertThat(data.homeVisits()).hasSize(1);
        assertThat(data.immunizations()).hasSize(1);
        assertThat(data.conditions()).hasSize(1);
        assertThat(data.measurements()).hasSize(1);
        assertThat(CanonicalFixtures.ref("x").recordId())
                .isNotEqualTo(CanonicalFixtures.ref("x").recordId());
    }
}
