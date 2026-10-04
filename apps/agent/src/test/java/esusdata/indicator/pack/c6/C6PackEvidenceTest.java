package esusdata.indicator.pack.c6;

import static esusdata.indicator.pack.c6.C6Scenario.BORN_70;
import static esusdata.indicator.pack.c6.C6Scenario.CBO_ACS;
import static esusdata.indicator.pack.c6.C6Scenario.CBO_NUTRICIONISTA;
import static esusdata.indicator.pack.c6.C6Scenario.EXIT_MUDANCA_TERRITORIO;
import static esusdata.indicator.pack.c6.C6Scenario.EXIT_OBITO;
import static esusdata.indicator.pack.c6.C6Scenario.FLU_TETRAVALENTE;
import static esusdata.indicator.pack.c6.C6Scenario.INE_A;
import static esusdata.indicator.pack.c6.C6Scenario.INE_B;
import static esusdata.indicator.pack.c6.C6Scenario.LINKED_ON;
import static esusdata.indicator.pack.c6.C6Scenario.VISIT_REASON;
import static esusdata.indicator.pack.c6.C6Scenario.exclusionReason;
import static esusdata.indicator.pack.c6.C6Scenario.homeVisit;
import static esusdata.indicator.pack.c6.C6Scenario.immunization;
import static esusdata.indicator.pack.c6.C6Scenario.personWithDeath;
import static esusdata.indicator.pack.c6.C6Scenario.practiceRow;
import static esusdata.indicator.pack.c6.C6Scenario.pts;
import static esusdata.indicator.pack.c6.C6Scenario.registration;
import static esusdata.indicator.pack.c6.C6Scenario.scenario;
import static esusdata.indicator.pack.c6.C6Scenario.subjectRow;
import static esusdata.indicator.pack.c6.C6Scenario.supportingRows;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * ENG-36 for C6: the evidence alone rebuilds the population (who is in, who is out and why) and
 * every practice decision, with the events behind each practice met, and carries nothing that
 * identifies a person.
 */
class C6PackEvidenceTest {

    private static final LocalDate AFTER_LINK = LocalDate.of(2025, 6, 1);
    private static final Pattern CPF_OR_CNS = Pattern.compile("\\d{11,}");
    private static final List<String> PRACTICES = List.of("A", "B", "C", "D");

    @Test
    void eng36_evidenceRebuildsThePopulationWithAReasonForEveryExclusion() {
        Map<String, String> expectedExclusions = new LinkedHashMap<>();
        C6Scenario population = scenario()
                .elder("eleg-1")
                .allPractices("eleg-1")
                .elder("eleg-2", INE_B)
                .practiceD("eleg-2")
                .elder("eleg-3");

        // a person known only from a registration has no birth date
        population.add(registration("sem-nascimento", LINKED_ON, INE_A));
        expectedExclusions.put("sem-nascimento", "EXCLUIDO_SEM_DATA_NASCIMENTO");

        population.person("nasc-divergente", BORN_70).person("nasc-divergente", BORN_70.plusDays(1));
        population.linked("nasc-divergente", INE_A);
        expectedExclusions.put("nasc-divergente", "EXCLUIDO_DATA_NASCIMENTO_DIVERGENTE");

        // under 60 and without a link: age is checked first
        population.person("menor-60", LocalDate.of(1980, 1, 1));
        expectedExclusions.put("menor-60", "EXCLUIDO_IDADE_MENOR_60");

        population.add(personWithDeath("obito-pessoa", BORN_70, LocalDate.of(2026, 3, 31)));
        population.linked("obito-pessoa", INE_A);
        expectedExclusions.put("obito-pessoa", "INTERROMPIDO_OBITO");

        population.person("sem-vinculo", BORN_70);
        expectedExclusions.put("sem-vinculo", "EXCLUIDO_SEM_VINCULO");

        population.person("so-simplificado", BORN_70);
        population.add(registration("so-simplificado", LINKED_ON, INE_A, true, false, false, null));
        expectedExclusions.put("so-simplificado", "EXCLUIDO_SEM_VINCULO");

        population.elder("conflito");
        population.add(registration("conflito", AFTER_LINK, INE_A));
        population.add(registration("conflito", AFTER_LINK, INE_B));
        expectedExclusions.put("conflito", "EXCLUIDO_VINCULO_CONFLITANTE");

        population.elder("recusa");
        population.add(registration("recusa", AFTER_LINK, INE_A, false, false, true, null));
        expectedExclusions.put("recusa", "EXCLUIDO_RECUSA_CADASTRO");

        population.elder("inativo");
        population.add(registration("inativo", AFTER_LINK, INE_A, false, true, false, null));
        expectedExclusions.put("inativo", "EXCLUIDO_CADASTRO_INATIVO");

        population.elder("mudou");
        population.add(registration("mudou", AFTER_LINK, INE_A, false, false, false, EXIT_MUDANCA_TERRITORIO));
        expectedExclusions.put("mudou", "INTERROMPIDO_MUDANCA_TERRITORIO");

        population.elder("obito-saida");
        population.add(registration("obito-saida", AFTER_LINK, INE_A, false, false, false, EXIT_OBITO));
        expectedExclusions.put("obito-saida", "INTERROMPIDO_OBITO");

        RuleOutcome outcome = population.compute();

        List<EvidenceItem> eligible = rows(outcome, EvidenceDecision.ELIGIBLE);
        assertThat(eligible).hasSize(outcome.result().denominator().intValueExact());
        assertThat(eligible)
                .extracting(EvidenceItem::subjectKey)
                .containsExactlyInAnyOrder("eleg-1", "eleg-2", "eleg-3");
        assertThat(eligible).allMatch(e -> "ELEGIVEL_60_ANOS_VINCULADO".equals(e.reasonCode()));
        assertThat(eligible.stream().map(EvidenceItem::points).reduce(pts(0), BigInteger::add))
                .isEqualTo(outcome.result().numerator());

        assertThat(rows(outcome, EvidenceDecision.EXCLUDED)).hasSize(expectedExclusions.size());
        expectedExclusions.forEach((key, reason) ->
                assertThat(exclusionReason(outcome, key)).as(key).isEqualTo(reason));

        for (EvidenceItem person : eligible) {
            for (String code : PRACTICES) {
                practiceRow(outcome, person.subjectKey(), code);
            }
        }
        assertThat(practiceRows(outcome)).hasSize(eligible.size() * PRACTICES.size());
        assertThat(practiceRows(outcome)).noneMatch(e -> expectedExclusions.containsKey(e.subjectKey()));
        assertThat(outcome.evidence()).allMatch(e -> e.subjectKind() == EvidenceSubjectKind.PERSON);
    }

    @Test
    void eng36_deathOrRegistrationAfterTheCutoffDoesNotInterrupt() {
        RuleOutcome outcome = scenario()
                .add(personWithDeath("morre-depois", BORN_70, LocalDate.of(2026, 4, 5)))
                .linked("morre-depois", INE_A)
                .elder("muda-depois")
                .add(registration(
                        "muda-depois", LocalDate.of(2026, 4, 2), INE_A, false, false, false, EXIT_MUDANCA_TERRITORIO))
                .elder("simplificado-depois")
                .add(registration(
                        "simplificado-depois", AFTER_LINK, INE_A, true, false, false, EXIT_MUDANCA_TERRITORIO))
                .compute();

        assertThat(rows(outcome, EvidenceDecision.ELIGIBLE))
                .extracting(EvidenceItem::subjectKey)
                .containsExactlyInAnyOrder("morre-depois", "muda-depois", "simplificado-depois");
    }

    @Test
    void eng36_noEvidenceFieldCarriesAnIdentifierOtherThanTheOpaqueKey() {
        RuleOutcome outcome = scenario()
                .elder("chave-opaca-1")
                .allPractices("chave-opaca-1")
                .elder("chave-opaca-2")
                .person("chave-opaca-3", LocalDate.of(1990, 5, 5))
                .compute();

        assertThat(outcome.evidence()).isNotEmpty();
        assertThat(outcome.evidence()).extracting(EvidenceItem::subjectKey).allMatch(k -> k.startsWith("chave-opaca-"));
        for (EvidenceItem row : outcome.evidence()) {
            assertThat(textFields(row))
                    .noneMatch(f -> f != null && CPF_OR_CNS.matcher(f).find());
        }
    }

    @Test
    void eng36_practiceRowsCarryPointsAndReasonCodes() {
        RuleOutcome outcome =
                scenario().elder("x").practiceA("x").practiceD("x").compute();

        assertThat(subjectRow(outcome, "x").points()).isEqualTo(pts(50));
        assertPractice(outcome, "A", EvidenceDecision.PRACTICE_MET, "A_CONSULTA_MEDICA_ENFERMAGEM", 25);
        assertPractice(outcome, "B", EvidenceDecision.PRACTICE_NOT_MET, "B_SEM_PESO_ALTURA_MESMO_DIA", 0);
        assertPractice(outcome, "C", EvidenceDecision.PRACTICE_NOT_MET, "C_SEM_DUAS_VISITAS_30_DIAS", 0);
        assertPractice(outcome, "D", EvidenceDecision.PRACTICE_MET, "D_DOSE_INFLUENZA", 25);
    }

    @Test
    void supportingEvents_aPointsToTheMostRecentConsult() {
        CanonicalCareEvent older = CanonicalFixtures.encounter("x", LocalDate.of(2025, 7, 1), "225142", false);
        CanonicalCareEvent latest = CanonicalFixtures.encounter("x", LocalDate.of(2026, 2, 1), "225142", false);
        RuleOutcome outcome = scenario().elder("x").add(older).add(latest).compute();

        assertThat(supportingRefs(outcome)).containsExactly(latest.sourceRef());
        assertThat(supportingRows(outcome, "x")).allMatch(e -> "MIAI".equals(e.modality()));
    }

    @Test
    void supportingEvents_bPointsToTheRecordsOfTheLatestQualifyingDay() {
        CanonicalCareEvent olderDay = CanonicalFixtures.encounterWithMeasures(
                "x", LocalDate.of(2025, 8, 1), CBO_NUTRICIONISTA, "70.5", "165.0", null, null);
        LocalDate latestDay = LocalDate.of(2026, 1, 20);
        CanonicalHomeVisit weight = homeVisit("x", latestDay, CBO_ACS, List.of(VISIT_REASON), "71.0", null);
        CanonicalHomeVisit height = homeVisit("x", latestDay, CBO_ACS, List.of(VISIT_REASON), null, "165.0");
        RuleOutcome outcome =
                scenario().elder("x").add(olderDay).add(weight).add(height).compute();

        assertThat(supportingRefs(outcome)).containsExactlyInAnyOrder(weight.sourceRef(), height.sourceRef());
        assertThat(supportingRows(outcome, "x")).allMatch(e -> "MIVDT".equals(e.modality()));
    }

    @Test
    void supportingEvents_cPointsToTheFirstAndLastVisit() {
        CanonicalHomeVisit first = visit(LocalDate.of(2025, 5, 1));
        CanonicalHomeVisit middle = visit(LocalDate.of(2025, 9, 1));
        CanonicalHomeVisit last = visit(LocalDate.of(2026, 3, 1));
        RuleOutcome outcome =
                scenario().elder("x").add(first).add(middle).add(last).compute();

        assertThat(supportingRefs(outcome)).containsExactlyInAnyOrder(first.sourceRef(), last.sourceRef());
        assertThat(supportingRows(outcome, "x")).allMatch(e -> "MIVDT".equals(e.modality()));
    }

    @Test
    void supportingEvents_dHasOneRowPerDistinctDose() {
        LocalDate applied = LocalDate.of(2025, 5, 10);
        CanonicalImmunization miv = immunization("x", applied, FLU_TETRAVALENTE, false, null);
        CanonicalImmunization repeated = immunization("x", applied, FLU_TETRAVALENTE, false, null);
        CanonicalImmunization other = immunization("x", LocalDate.of(2026, 3, 2), FLU_TETRAVALENTE, false, null);
        RuleOutcome outcome =
                scenario().elder("x").add(miv).add(repeated).add(other).compute();

        List<EvidenceItem> supporting = supportingRows(outcome, "x");
        assertThat(supporting).hasSize(2);
        assertThat(supporting).allMatch(e -> e.sourceRef() != null && "MIV".equals(e.modality()));
        assertThat(supporting)
                .extracting(EvidenceItem::eventDate)
                .containsExactlyInAnyOrder("2025-05-10", "2026-03-02");
    }

    @Test
    void supportingEvents_practicesNotMetHaveNone() {
        RuleOutcome outcome = scenario()
                .elder("x")
                .consult("x", LocalDate.of(2025, 1, 10))
                .visit("x", LocalDate.of(2026, 1, 1))
                .compute();

        assertThat(supportingRows(outcome, "x")).isEmpty();
    }

    private static void assertPractice(
            RuleOutcome outcome, String code, EvidenceDecision decision, String reason, long points) {
        EvidenceItem row = practiceRow(outcome, "x", code);
        assertThat(row.decision()).as(code).isEqualTo(decision);
        assertThat(row.reasonCode()).as(code).isEqualTo(reason);
        assertThat(row.points()).as(code).isEqualTo(pts(points));
        assertThat(row.subjectKind()).isEqualTo(EvidenceSubjectKind.PERSON);
    }

    private static CanonicalHomeVisit visit(LocalDate date) {
        return homeVisit("x", date, CBO_ACS, List.of(VISIT_REASON), null, null);
    }

    private static List<SourceRef> supportingRefs(RuleOutcome outcome) {
        return supportingRows(outcome, "x").stream()
                .map(EvidenceItem::sourceRef)
                .toList();
    }

    private static List<EvidenceItem> rows(RuleOutcome outcome, EvidenceDecision decision) {
        return outcome.evidence().stream().filter(e -> e.decision() == decision).toList();
    }

    private static List<EvidenceItem> practiceRows(RuleOutcome outcome) {
        return outcome.evidence().stream()
                .filter(e -> e.decision() == EvidenceDecision.PRACTICE_MET
                        || e.decision() == EvidenceDecision.PRACTICE_NOT_MET)
                .toList();
    }

    private static List<String> textFields(EvidenceItem row) {
        List<String> fields = new ArrayList<>(Stream.of(
                        row.subjectKey(),
                        row.eventDate(),
                        row.component(),
                        row.reasonCode(),
                        row.cnes(),
                        row.ine(),
                        row.cbo(),
                        row.modality())
                .toList());
        if (row.sourceRef() != null) {
            fields.add(row.sourceRef().sourceId());
            fields.add(row.sourceRef().entityType());
            fields.add(row.sourceRef().recordId());
        }
        return fields;
    }
}
