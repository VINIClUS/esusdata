package esusdata.source.model;

/**
 * The stored result of a source's last municipal isolation check (ADR 0023), for the competência
 * it counted. The counts are null unless {@code outcome} is {@code CHECKED}. Like {@link
 * LastDiagnostic}, it applies only to the configuration version it ran against.
 *
 * @param registeredCount        atendimentos whose {@code co_ibge} is the source's municipality
 * @param otherMunicipalityCount atendimentos under any other 7-digit {@code co_ibge}
 * @param otherMunicipalityCodes how many distinct other codes those were
 * @param unidentifiedCount      atendimentos whose municipality row has no 7-digit {@code co_ibge}
 */
public record LastIsolationCheck(
        int sourceConfigurationVersion,
        String referencePeriod,
        String outcome,
        Long registeredCount,
        Long otherMunicipalityCount,
        Integer otherMunicipalityCodes,
        Long unidentifiedCount,
        String checkedAt) {

    public boolean appliesTo(SourceRecord source) {
        return source.sourceConfigurationVersion() == sourceConfigurationVersion;
    }
}
