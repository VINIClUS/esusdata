package esusdata.indicator.pack;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalPregnancyOutcome;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** What the chronic-care packs (C4, C5, C6) share: codes, scope and read-coverage checks, and the unsupported result. */
public final class PackSupport {

    /** Practice A: médicos {@code 2251, 2252, 2253, 2231} and enfermeiros {@code 2235}. */
    public static final CboGroups CONSULTATION_CBO = CboGroups.of("2251", "2252", "2253", "2231", "2235");

    /** SIGTAP «01.01.04.002-4» — «Avaliação antropométrica» (weight and height at once). */
    public static final String SIGTAP_ANTHROPOMETRY = "0101040024";

    /** SIGTAP «01.01.04.008-3» — «Medição de peso». */
    public static final String SIGTAP_WEIGHT = "0101040083";

    /** SIGTAP «01.01.04.007-5» — «Medição de altura». */
    public static final String SIGTAP_HEIGHT = "0101040075";

    private PackSupport() {}

    /**
     * The municipal scope check (§1.12) over the eight kinds of record every pack reads: a record of
     * another municipality is refused, never counted.
     */
    public static void requireCoreMunicipality(CanonicalDataset data, String municipalityIbge) {
        check(data.persons(), CanonicalPerson::municipalityIbge, municipalityIbge);
        check(data.registrations(), CanonicalRegistration::municipalityIbge, municipalityIbge);
        check(data.teams(), CanonicalTeam::municipalityIbge, municipalityIbge);
        check(data.careEvents(), CanonicalCareEvent::municipalityIbge, municipalityIbge);
        check(data.procedureEvents(), CanonicalProcedureEvent::municipalityIbge, municipalityIbge);
        check(data.homeVisits(), CanonicalHomeVisit::municipalityIbge, municipalityIbge);
        check(data.conditions(), CanonicalCondition::municipalityIbge, municipalityIbge);
        check(data.measurements(), CanonicalMeasurement::municipalityIbge, municipalityIbge);
    }

    /** The municipal scope check over every kind of record, for the packs that also read the other three. */
    public static void requireMunicipality(CanonicalDataset data, String municipalityIbge) {
        requireCoreMunicipality(data, municipalityIbge);
        check(data.encounters(), CanonicalEncounter::municipalityIbge, municipalityIbge);
        check(data.immunizations(), CanonicalImmunization::municipalityIbge, municipalityIbge);
        check(data.pregnancyOutcomes(), CanonicalPregnancyOutcome::municipalityIbge, municipalityIbge);
    }

    private static <T extends Record> void check(
            List<T> records, Function<T, String> municipality, String municipalityIbge) {
        for (T r : records) {
            String found = municipality.apply(r);
            if (!municipalityIbge.equals(found)) {
                throw new IllegalArgumentException("a " + r.getClass().getSimpleName() + " of municipality " + found
                        + " is outside the authorized municipality " + municipalityIbge);
            }
        }
    }

    /**
     * Parts the extract did not read, or read for a shorter window. An extract
     * that declares no windows (a synthetic one) is taken as complete.
     */
    public static List<PartRequirement> uncoveredParts(CanonicalDataset data, List<PartRequirement> required) {
        List<PartRequirement> missing = new ArrayList<>();
        if (data.windows().isEmpty()) {
            return missing;
        }
        for (PartRequirement part : required) {
            Optional<DateWindow> read = data.windowOf(part.capability());
            boolean covered = read.isPresent()
                    && !read.get().start().isAfter(part.periodStart())
                    && !read.get().endExclusive().isBefore(part.periodEndExclusive());
            if (!covered) {
                missing.add(part);
            }
        }
        return missing;
    }

    /** A measured value is present when it is a positive decimal; blank, zero or garbage is absent. */
    public static boolean positive(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return new BigDecimal(value.strip()).signum() > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** {@code UNSUPPORTED_SOURCE}: no value and no counts — a part not read is never a zero. */
    public static IndicatorResult unsupportedSource(
            PackDescriptor descriptor, EvaluationContext context, List<String> limitations) {
        return new IndicatorResult(
                IndicatorStatus.UNSUPPORTED_SOURCE,
                null,
                null,
                null,
                descriptor.denominatorKind(),
                null,
                context.referencePeriod(),
                descriptor.ruleVersion(),
                context.dataCutoff().toString(),
                context.municipalityIbge(),
                limitations,
                descriptor.calculationPolicyVersion(),
                descriptor.valueKind(),
                null,
                List.of(),
                false);
    }
}
