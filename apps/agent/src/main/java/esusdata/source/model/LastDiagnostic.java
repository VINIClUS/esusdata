package esusdata.source.model;

/**
 * The stored result of a source's last diagnostic. {@code detail} is a connection-class message,
 * never the secret. It applies only to the configuration version it ran against: callers compare it
 * with the {@link SourceRecord} they already read, so a re-registration between two reads never
 * pairs one configuration with another's result.
 */
public record LastDiagnostic(int sourceConfigurationVersion, String outcome, String detail, String testedAt) {

    public boolean appliesTo(SourceRecord source) {
        return source.sourceConfigurationVersion() == sourceConfigurationVersion;
    }
}
