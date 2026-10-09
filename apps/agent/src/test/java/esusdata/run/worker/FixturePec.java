package esusdata.run.worker;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.IndicatorRule;
import esusdata.run.acquisition.Acquisition;
import esusdata.run.acquisition.AcquisitionCommand;
import esusdata.run.extract.ExtractFixtures;
import esusdata.run.extract.ExtractFixturesV2;
import esusdata.run.extract.ExtractionManifest;
import esusdata.source.pec.IndividualEncounterModalityContract;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A PEC that exists only as a fixture, behind the seam {@link AcquisitionInputs#of} gives {@link
 * ReferenceScopedExtracts}: asked for an extract, it writes the synthetic one ({@link
 * ExtractFixtures}, {@link ExtractFixturesV2}) of the pack and month named by the id of the command,
 * the way the execution plane does: C1's v1 extract with the adapter version and query checksum of
 * its frozen contract, after the {@code -team} supplement it is read with (ADR 0033), and a v2
 * extract for every other pack. It records every command it is asked, so a test can say what was
 * read and, as importantly, what was not.
 *
 * <p>It is honest by default. The {@code writing…} methods make it write what it was not asked for
 * (another municipality, source, month or adapter version), to see the cache refuse an acquisition
 * that does not give it what the expected context says.
 */
public final class FixturePec {

    public static final String SOURCE_ID = "pec-fixture";

    public static final String PEC_VERSION = "5.5.28";

    /** The encounters of the C1 extract: the numerator and denominator minus it are the golden counts. */
    public static final int PROGRAMADO = 6;

    public static final int ESPONTANEO = 2;

    private static final String POSTGRES_VERSION = "PostgreSQL 14.12";
    private static final String DATABASE = "pec";
    private static final String ROLE_USER = "esus_readonly";
    private static final String HOST = "pec.invalid";
    private static final int PORT = 5433;
    private static final String TEAM_INE = "0000346268";
    private static final Pattern EXTRACTION_ID = Pattern.compile("portao-d-(c\\d)-(\\d{4}-\\d{2})(-team)?");

    private final String municipalityIbge;
    private final List<AcquisitionCommand> requests = new ArrayList<>();
    private int programado = PROGRAMADO;
    private int espontaneo = ESPONTANEO;
    private String teamIne = TEAM_INE;
    private String writtenMunicipality;
    private String writtenSource = SOURCE_ID;
    private int writtenMonthOffset;
    private String writtenV1AdapterVersion = IndividualEncounterModalityContract.ADAPTER_VERSION;

    /** A PEC authorized for {@code municipalityIbge}, whose extracts are that municipality's. */
    public FixturePec(String municipalityIbge) {
        this.municipalityIbge = municipalityIbge;
        this.writtenMunicipality = municipalityIbge;
    }

    /** The C1 extract carries these many PROGRAMADO and ESPONTANEO encounters. */
    public FixturePec encounters(int programadoCount, int espontaneoCount) {
        this.programado = programadoCount;
        this.espontaneo = espontaneoCount;
        return this;
    }

    /** The team of the C1 supplement has this INE; the encounters' is {@code 0000346268}. */
    public FixturePec teamIne(String ine) {
        this.teamIne = ine;
        return this;
    }

    /** Writes extracts of another municipality than the source is authorized for. */
    public FixturePec writingMunicipality(String ibge) {
        this.writtenMunicipality = ibge;
        return this;
    }

    /** Writes extracts of another source id than the one it is registered as. */
    public FixturePec writingSource(String sourceId) {
        this.writtenSource = sourceId;
        return this;
    }

    /** Writes the extract of the month {@code months} away from the one asked for. */
    public FixturePec writingMonthsOffBy(int months) {
        this.writtenMonthOffset = months;
        return this;
    }

    /** Writes C1's v1 extract with this adapter version instead of its contract's. */
    public FixturePec writingV1AdapterVersion(String adapterVersion) {
        this.writtenV1AdapterVersion = adapterVersion;
        return this;
    }

    /** The identity the cache keys its partitions by. */
    public SourceIdentity sourceIdentity() {
        return sourceIdentity(POSTGRES_VERSION);
    }

    /** The same PEC as seen by a preflight that read another PostgreSQL version. */
    public SourceIdentity sourceIdentity(String postgresVersion) {
        return new SourceIdentity(
                SOURCE_ID, PEC_VERSION, postgresVersion, municipalityIbge, AcquisitionInputs.READ_MODEL);
    }

    /** The identity the execution plane checks the compatibility matrix with. */
    public PecSourceIdentity pecIdentity() {
        return new PecSourceIdentity(SOURCE_ID, PEC_VERSION, AcquisitionInputs.READ_MODEL, AcquisitionInputs.ROLE);
    }

    public PecConnectionProperties connection() {
        return new PecConnectionProperties(
                SOURCE_ID, HOST, PORT, DATABASE, ROLE_USER, AcquisitionInputs.PASSWORD_KEY, municipalityIbge);
    }

    /** Live acquisition from this fixture. */
    public AcquisitionInputs inputs() {
        return AcquisitionInputs.of(connection(), pecIdentity(), this::acquisitionInto);
    }

    /** The acquisition that writes into {@code directory}: what the execution plane is built to do per directory. */
    public Acquisition acquisitionInto(Path directory) {
        return (command, cancellation, listener) -> write(directory, command);
    }

    /** Every command it was asked, in order. */
    public List<AcquisitionCommand> requests() {
        return List.copyOf(requests);
    }

    private ExtractionManifest write(Path directory, AcquisitionCommand command) {
        requests.add(command);
        Matcher id = EXTRACTION_ID.matcher(command.extractionId());
        if (!id.matches()) {
            throw new IllegalArgumentException("not the id of an extract of the Portão D: " + command.extractionId());
        }
        IndicatorRule rule = ruleOf(id.group(1));
        YearMonth month = YearMonth.parse(id.group(2)).plusMonths(writtenMonthOffset);
        try {
            if (id.group(3) != null) {
                return ExtractFixtures.writeTeams(
                        directory, command.extractionId(), writtenSource, writtenMunicipality, month, teamIne);
            }
            if (command.isCanonicalV2()) {
                return ExtractFixturesV2.forRule(rule, month)
                        .municipality(writtenMunicipality)
                        .write(directory, command.extractionId(), writtenSource);
            }
            return ExtractFixtures.write(
                    directory,
                    command.extractionId(),
                    writtenSource,
                    writtenMunicipality,
                    month.toString(),
                    programado,
                    espontaneo,
                    0,
                    IndividualEncounterModalityContract.QUERY_CHECKSUM,
                    writtenV1AdapterVersion);
        } catch (IOException e) {
            throw new UncheckedIOException("the fixture could not write " + command.extractionId(), e);
        }
    }

    private static IndicatorRule ruleOf(String code) {
        return IndicatorRuleRegistry.all().stream()
                .filter(rule -> rule.descriptor().code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no pack " + code));
    }
}
