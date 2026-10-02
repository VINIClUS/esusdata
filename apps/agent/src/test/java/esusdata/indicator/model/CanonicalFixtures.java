package esusdata.indicator.model;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builders for synthetic canonical records in rule tests (ADR 0030). Every record gets a fresh
 * source id, the municipality {@link #IBGE} and a person key given by the test; fields a test does
 * not care about stay {@code null}, exactly as an absent source value would.
 */
public final class CanonicalFixtures {

    public static final String IBGE = "3541307";
    public static final String SOURCE = "pec-sintetico";

    private static final AtomicLong IDS = new AtomicLong();

    private CanonicalFixtures() {}

    public static SourceRef ref(String entityType) {
        return new SourceRef(SOURCE, entityType, String.valueOf(IDS.incrementAndGet()));
    }

    public static CanonicalPerson person(String key, LocalDate birth, String sex) {
        return new CanonicalPerson(ref("tb_fat_cad_individual"), IBGE, key, birth.toString(), sex, null, null);
    }

    public static CanonicalPerson person(String key, LocalDate birth, String sex, String genderIdentity) {
        return new CanonicalPerson(
                ref("tb_fat_cad_individual"), IBGE, key, birth.toString(), sex, genderIdentity, null);
    }

    /** A registration version linking {@code key} to team {@code ine} on {@code date}. */
    public static CanonicalRegistration registration(String key, LocalDate date, String cnes, String ine) {
        return new CanonicalRegistration(
                ref("tb_fat_cad_individual"),
                IBGE,
                key,
                date.toString(),
                cnes,
                ine,
                false,
                false,
                false,
                null,
                null,
                null,
                null);
    }

    /** An individual encounter by {@code cbo} on {@code date}, presential unless {@code remote}. */
    public static CanonicalCareEvent encounter(String key, LocalDate date, String cbo, boolean remote) {
        return careEvent(key, date, cbo, remote, List.of(), List.of(), null, null, null, null);
    }

    /** An individual encounter that evaluated CIAP-2/CID-10 codes. */
    public static CanonicalCareEvent encounterWithProblems(
            String key, LocalDate date, String cbo, List<String> ciap, List<String> cid) {
        return careEvent(key, date, cbo, false, ciap, cid, null, null, null, null);
    }

    /** An individual encounter that wrote weight, height and blood pressure in the PEC's own fields. */
    public static CanonicalCareEvent encounterWithMeasures(
            String key,
            LocalDate date,
            String cbo,
            String weightKg,
            String heightCm,
            String systolic,
            String diastolic) {
        return careEvent(key, date, cbo, false, List.of(), List.of(), weightKg, heightCm, systolic, diastolic);
    }

    private static CanonicalCareEvent careEvent(
            String key,
            LocalDate date,
            String cbo,
            boolean remote,
            List<String> ciap,
            List<String> cid,
            String weightKg,
            String heightCm,
            String systolic,
            String diastolic) {
        return new CanonicalCareEvent(
                ref("tb_fat_atendimento_individual"),
                IBGE,
                key,
                date.toString(),
                "INDIVIDUAL",
                cbo,
                null,
                null,
                null,
                null,
                remote,
                ciap,
                cid,
                List.of(),
                List.of(),
                List.of(),
                weightKg,
                heightCm,
                systolic,
                diastolic,
                null,
                null,
                null,
                null);
    }

    /** A procedure, exam request or exam evaluation ({@code stage}) of SIGTAP {@code code}. */
    public static CanonicalProcedureEvent procedure(String key, LocalDate date, String code, String stage, String cbo) {
        return new CanonicalProcedureEvent(
                ref("tb_fat_proced_atend_proced"), IBGE, key, date.toString(), code, stage, cbo, null, null, "MIP");
    }

    /** A home visit by {@code cbo} with outcome {@code outcome}. */
    public static CanonicalHomeVisit visit(String key, LocalDate date, String cbo, String outcome) {
        return new CanonicalHomeVisit(
                ref("tb_fat_visita_domiciliar"),
                IBGE,
                key,
                date.toString(),
                cbo,
                null,
                null,
                outcome,
                List.of(),
                null,
                null);
    }

    /** A vaccine dose of {@code immunobiological} and {@code dose} applied on {@code date}. */
    public static CanonicalImmunization dose(String key, LocalDate date, String immunobiological, String dose) {
        return new CanonicalImmunization(
                ref("tb_fat_vacinacao_vacina"),
                IBGE,
                key,
                date.toString(),
                immunobiological,
                dose,
                null,
                false,
                null,
                null,
                null);
    }

    /** A condition {@code code} of {@code system} (CIAP2/CID10) recorded on {@code date} with {@code status}. */
    public static CanonicalCondition condition(String key, String system, String code, LocalDate date, String status) {
        return new CanonicalCondition(
                ref("tb_fat_atd_ind_problemas"),
                IBGE,
                key,
                system,
                code,
                date.toString(),
                status,
                null,
                "PROFESSIONAL");
    }

    /** Weight and height written outside an encounter (MIP or MIAC). */
    public static CanonicalMeasurement measurement(
            String key, LocalDate date, String weightKg, String heightCm, String origin) {
        return new CanonicalMeasurement(
                ref("tb_fat_proced_atend"), IBGE, key, date.toString(), weightKg, heightCm, null, null, null, origin);
    }
}
