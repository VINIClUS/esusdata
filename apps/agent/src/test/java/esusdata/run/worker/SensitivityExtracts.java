package esusdata.run.worker;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorRule;
import esusdata.run.acquisition.AcquisitionCommand;
import esusdata.run.acquisition.AcquisitionListener;
import esusdata.run.acquisition.ExecPlaneAcquisition;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.FileExtractStore;
import esusdata.run.job.CancellationToken;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.CapabilityCatalog;
import esusdata.source.pec.EnvFileSecretResolver;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The input side of the sensitivity harness, in this package because {@link ReadPlan} is
 * package-private: it reads finalized extracts the way {@code RunExecutor.runFromExtract} does —
 * a pack only ever sees an extract its own plan accepts ({@code requireCovers}: source,
 * municipality, period and every part's window, binds and query checksum) — and, in live mode,
 * acquires one extract per pack with the very command {@code runLive} builds from the same plan.
 */
public final class SensitivityExtracts {

    private static final String MANIFEST_SUFFIX = ".manifest.json";
    private static final String ZONE = "America/Sao_Paulo";
    private static final String PASSWORD_KEY = "PEC_DB_PASSWORD";
    private static final String LABEL = "sensibilidade";

    private SensitivityExtracts() {}

    /** A pack, the dataset its own plan accepted and the evaluation context of the competência. */
    public record PackInput(IndicatorRule rule, CanonicalDataset data, EvaluationContext context) {}

    /**
     * Every pack that finds an extract it accepts in {@code directory}. {@code competencia} is the
     * month asked for; when {@code null} it is the month before each manifest's exclusive end.
     */
    public static List<PackInput> fromDirectory(Path directory, YearMonth competencia) throws IOException {
        FileExtractStore store = new FileExtractStore(directory);
        List<ExtractionManifest> manifests = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(MANIFEST_SUFFIX)) {
                    manifests.add(store.readManifest(name.substring(0, name.length() - MANIFEST_SUFFIX.length())));
                }
            }
        }
        CapabilityCatalog catalog = CapabilityCatalog.packaged();
        List<PackInput> inputs = new ArrayList<>();
        for (IndicatorRule rule : IndicatorRuleRegistry.all()) {
            Optional<PackInput> accepted = firstAccepted(rule, manifests, store, catalog, competencia);
            accepted.ifPresent(inputs::add);
        }
        return inputs;
    }

    /** The dataset of the first extract whose manifest the pack's own read plan accepts, if any. */
    private static Optional<PackInput> firstAccepted(
            IndicatorRule rule,
            List<ExtractionManifest> manifests,
            FileExtractStore store,
            CapabilityCatalog catalog,
            YearMonth competencia)
            throws IOException {
        for (ExtractionManifest manifest : manifests) {
            YearMonth month = competencia != null
                    ? competencia
                    : YearMonth.from(
                            LocalDate.parse(manifest.periodEndExclusive()).minusDays(1));
            ReadPlan plan = ReadPlan.of(rule, month, catalog);
            if (accepts(plan, rule, manifest, month)) {
                return Optional.of(new PackInput(
                        rule,
                        plan.read(store, manifest),
                        EvaluationContext.endOfMonth(manifest.municipalityIbge(), month)));
            }
        }
        return Optional.empty();
    }

    private static boolean accepts(ReadPlan plan, IndicatorRule rule, ExtractionManifest manifest, YearMonth month) {
        try {
            plan.requireCovers(manifest, context(rule, manifest, month));
            return true;
        } catch (IllegalStateException notThisPack) {
            return false;
        }
    }

    /**
     * Acquires one extract per pack of {@code packs} for {@code competencia} into {@code
     * extractsDir} — the same plan and command {@code runLive} uses. Reads the production PEC:
     * only call it with the explicit opt-in of the live test.
     *
     * @param environment the PEC secret file's {@code PEC_*} entries
     */
    public static List<String> acquire(
            Path extractsDir,
            YearMonth competencia,
            Set<String> packs,
            Map<String, String> environment,
            Path envFile,
            String binary)
            throws IOException {
        String host = environment.get("PEC_DB_HOST");
        int port = Integer.parseInt(environment.get("PEC_DB_PORT"));
        PecSourceIdentity identity = new PecSourceIdentity(
                environment.get("PEC_SOURCE_ID"), environment.get("PEC_VERSION"), "PEC_DW", "PRONTUARIO");
        PecConnectionProperties connection = new PecConnectionProperties(
                identity.sourceId(),
                host,
                port,
                environment.get("PEC_DB_NAME"),
                environment.get("PEC_DB_USER"),
                PASSWORD_KEY,
                environment.get("PEC_MUNICIPALITY_IBGE"));
        ExecPlaneAcquisition adapter = new ExecPlaneAcquisition(
                List.of(binary),
                new EnvFileSecretResolver(envFile),
                new AllowedDestinations(Set.of(new AllowedDestinations.HostPort(host, port))),
                extractsDir,
                Clock.systemUTC(),
                Duration.ofSeconds(10));
        Files.createDirectories(extractsDir);
        List<String> acquired = new ArrayList<>();
        for (IndicatorRule rule : IndicatorRuleRegistry.all()) {
            if (!packs.isEmpty() && !packs.contains(rule.descriptor().id())) {
                continue;
            }
            ReadPlan plan = ReadPlan.of(rule, competencia, CapabilityCatalog.packaged());
            String extractionId = LABEL + "-" + rule.descriptor().code().toLowerCase(Locale.ROOT) + "-" + competencia;
            AcquisitionCommand command = plan.command(connection, identity, extractionId, ZONE);
            acquired.add(adapter.acquire(command, new CancellationToken(), new Silent())
                    .extractionId());
        }
        return acquired;
    }

    private static RunExecutor.RunContext context(IndicatorRule rule, ExtractionManifest manifest, YearMonth month) {
        return new RunExecutor.RunContext(
                LABEL,
                LABEL,
                manifest.sourceId(),
                1,
                LABEL,
                manifest.extractionId(),
                manifest.municipalityIbge(),
                month.toString(),
                rule.descriptor().id(),
                rule.descriptor().ruleVersion(),
                LABEL);
    }

    /** Nothing is logged: the acquisition's progress and uncertainty carry no data and need no echo. */
    private static final class Silent implements AcquisitionListener {

        @Override
        public void onProgress() {
            // not reported
        }

        @Override
        public void onUncertainOutcome(String reason) {
            // the harness reads, it does not guard the source; the live test stops on the exception
        }
    }
}
