package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.Profiles;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.ReplayResult;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Mode;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Replay;
import esusdata.run.worker.ReferenceScopedExtracts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The replay of the compatibility evidence (spec 2026-10-08 §9.2, plan Task 9 step 5): every dossier
 * of a directory regenerated from the cached artifacts and compared byte for byte. Opt-in with
 * {@code -Dobservatorio.gate.d.mode=replay}; skipped without it. Local only: its inputs never enter
 * Git, so the CI cannot and must not run it.
 *
 * <p>Inputs: {@code -Dobservatorio.gate.d.manifests-dir}, {@code -Dobservatorio.gate.d.artifact-dir}
 * (the campaign's store and cache), {@code -Dobservatorio.gate.d.dossier-dir} (the dossiers to
 * reproduce) and optionally {@code -Dobservatorio.gate.d.profiles}. It takes no PEC, no secret file
 * and no execution plane, and it cannot acquire: a partition the cache lacks is a failure, not a
 * read. Run it with the tunnel closed. The regenerated files are written to a temporary directory,
 * outside {@code docs/}, and removed afterwards.
 */
class PortaoDEvidenceReplayLiveTest {

    private static final Logger log = LoggerFactory.getLogger(PortaoDEvidenceReplayLiveTest.class);

    @Test
    void everyDossierIsReproducedByteForByteFromTheCachedArtifacts() throws IOException {
        Assumptions.assumeTrue(
                PortaoDRunnerConfiguration.modeOf(System::getProperty)
                        .filter(Mode.REPLAY::equals)
                        .isPresent(),
                "Skipping: opt in with -D" + PortaoDRunnerConfiguration.MODE + "=replay");
        Replay configuration = Replay.parse(System::getProperty);
        Path scratch = Files.createTempDirectory("portao-d-replay");
        try {
            ReplayResult result = PortaoDCompatibilityRun.replay(
                    configuration.manifestsDir(),
                    configuration.artifactDir(),
                    new ReferenceScopedExtracts(configuration.artifactDir().resolve("extratos"), Clock.systemUTC()),
                    Profiles.of(MethodologyProfileRegistry.load(configuration.profiles())),
                    MethodologyProbeCatalog::forPack,
                    configuration.dossierDir(),
                    scratch);

            log.info(
                    "replay: {} files regenerated, {} missing, {} extra, {} different, {} references failed, {} gaps",
                    result.regenerated().size(),
                    result.missing().size(),
                    result.extra().size(),
                    result.different().size(),
                    result.errors().size(),
                    result.gaps().size());
            result.different().forEach(name -> log.warn("replay: {} differs", name));
            result.errors()
                    .forEach(error -> log.warn("replay: {} {}: {}", error.pack(), error.referenceId(), error.detail()));
            result.gaps()
                    .forEach(gap -> log.warn("replay: gap {} {}: {}", gap.pack(), gap.referenceId(), gap.detail()));

            assertThat(result.identical())
                    .as("every dossier reproduced byte for byte: see the lines above")
                    .isTrue();
        } finally {
            deleteTree(scratch);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
