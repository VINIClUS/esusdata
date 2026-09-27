package esusdata.source;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.SourceRequirementsService.Code;
import esusdata.source.SourceRequirementsService.Requirement;
import esusdata.source.model.LastDiagnostic;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.PecCompatibilityMatrix;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SourceRequirementsServiceTest {

    private final SourceRequirementsService service =
            new SourceRequirementsService(PecCompatibilityMatrix.fromClasspathResource());

    @Test
    void aValidatedRegistrationWithAConnectedDiagnosticMeetsEveryRequirement() {
        var requirements = service.requirements(
                source("PEC_POSTGRESQL", "5.4.37", "PEC_DW", "3541307"),
                Optional.of(new LastDiagnostic(1, "CONNECTED", null, Instant.EPOCH.toString())));

        assertThat(requirements)
                .containsExactly(
                        new Requirement(Code.READ_CONNECTION, true),
                        new Requirement(Code.PEC_POSTGRESQL_FAMILY, true),
                        new Requirement(Code.PEC_VERSION_IN_MATRIX, true),
                        new Requirement(Code.MUNICIPAL_SCOPE, true));
    }

    @Test
    void withoutADiagnosticTheReadConnectionIsNotConfirmed() {
        var requirements =
                service.requirements(source("PEC_POSTGRESQL", "5.4.37", "PEC_DW", "3541307"), Optional.empty());

        assertThat(requirements).contains(new Requirement(Code.READ_CONNECTION, false));
    }

    @Test
    void aFailedDiagnosticDoesNotConfirmTheReadConnection() {
        var requirements = service.requirements(
                source("PEC_POSTGRESQL", "5.4.37", "PEC_DW", "3541307"),
                Optional.of(new LastDiagnostic(
                        1, "SOURCE_AUTHENTICATION_FAILED", "source authentication failed", Instant.EPOCH.toString())));

        assertThat(requirements).contains(new Requirement(Code.READ_CONNECTION, false));
    }

    @Test
    void aConnectedDiagnosticOfAnotherConfigurationVersionConfirmsNothing() {
        var requirements = service.requirements(
                source("PEC_POSTGRESQL", "5.4.37", "PEC_DW", "3541307"),
                Optional.of(new LastDiagnostic(2, "CONNECTED", null, Instant.EPOCH.toString())));

        assertThat(requirements).contains(new Requirement(Code.READ_CONNECTION, false));
    }

    @Test
    void anUnlistedOrIncompleteRegistrationFailsOnlyItsOwnRequirements() {
        assertThat(service.requirements(source("PEC_POSTGRESQL", "5.4.38", "PEC_DW", "3541307"), Optional.empty()))
                .contains(new Requirement(Code.PEC_VERSION_IN_MATRIX, false))
                .contains(new Requirement(Code.PEC_POSTGRESQL_FAMILY, true));
        assertThat(service.requirements(source("EXTERNAL_DATASET", null, null, "35413"), Optional.empty()))
                .contains(
                        new Requirement(Code.PEC_POSTGRESQL_FAMILY, false),
                        new Requirement(Code.PEC_VERSION_IN_MATRIX, false),
                        new Requirement(Code.MUNICIPAL_SCOPE, false));
    }

    private static SourceRecord source(String family, String pecVersion, String readModel, String municipalityIbge) {
        return new SourceRecord(
                "src-requirements",
                1,
                family,
                "PRONTUARIO",
                "PRIMARY",
                "127.0.0.1", // NOPMD - AvoidUsingHardCodedIP: never connected to
                5432,
                "esus",
                "esus_leitura",
                "PEC_DB_PASSWORD",
                municipalityIbge,
                pecVersion,
                readModel,
                Instant.EPOCH.toString());
    }
}
