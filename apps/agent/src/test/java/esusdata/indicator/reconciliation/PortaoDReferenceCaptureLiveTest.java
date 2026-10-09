package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Capture;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Mode;
import esusdata.indicator.reconciliation.ReferenceCapture.Action;
import esusdata.indicator.reconciliation.ReferenceCapture.FileReport;
import esusdata.indicator.reconciliation.ReferenceCapture.PackReport;
import java.io.IOException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The capture runner of the Portão D (spec 2026-10-08 §18): the official exports of a directory,
 * stored as reference revisions. Opt-in with {@code -Dobservatorio.gate.d.mode=capture}; skipped
 * without it, and never run by the CI.
 *
 * <p>Inputs: {@code -Dobservatorio.gate.d.official-export-dir=<dir>} (the {@code .csv} files; a CVAT
 * report is skipped with its reason, any other file the parser does not know is refused),
 * {@code -Dobservatorio.gate.d.official-export-ibge=<7 digits>}, {@code
 * -Dobservatorio.gate.d.artifact-dir=<dir>} (the content-addressed store) and {@code
 * -Dobservatorio.gate.d.manifest-output=<dir>} (where the manifests go; point it at {@code
 * docs/indicadores/portoes/references} to version them). It reads no PEC, no registry and no policy
 * and writes none; a command line that names one is refused ({@link PortaoDRunnerConfiguration}).
 *
 * <p>Re-running it over the same files touches nothing; a file whose content changed becomes the
 * next revision, reported as drift, and no manifest or revision is ever overwritten. The log carries
 * counts, reference ids and hashes, files by their position in the directory, and never an INE.
 */
class PortaoDReferenceCaptureLiveTest {

    private static final Logger log = LoggerFactory.getLogger(PortaoDReferenceCaptureLiveTest.class);

    @Test
    void capturesTheOfficialTeamExportsOfTheDirectoryAsReferenceRevisions() throws IOException {
        Assumptions.assumeTrue(
                PortaoDRunnerConfiguration.modeOf(System::getProperty)
                        .filter(Mode.CAPTURE::equals)
                        .isPresent(),
                "Skipping: opt in with -D" + PortaoDRunnerConfiguration.MODE + "=capture");
        Capture capture = Capture.parse(System::getProperty);
        ReferenceCapture runner = new ReferenceCapture(
                capture.artifactDir(),
                capture.manifestOutput(),
                SiapsFormats.uf(capture.municipalityIbge()),
                Clock.systemUTC());

        List<FileReport> reports = runner.capture(capture.exportDir(), capture.municipalityIbge());

        reports.stream().flatMap(report -> report.lines().stream()).forEach(line -> log.info("{}", line));
        Map<Action, Integer> actions = new EnumMap<>(Action.class);
        reports.stream()
                .flatMap(report -> report.packs().stream())
                .map(PackReport::action)
                .forEach(action -> actions.merge(action, 1, Integer::sum));
        long skipped = reports.stream().filter(FileReport::cvat).count();
        log.info(
                "capture: {} official exports read, {} CVAT reports skipped, packs by outcome {}",
                reports.size() - skipped,
                skipped,
                actions);
        if (actions.containsKey(Action.DRIFT) || actions.containsKey(Action.INCOMPLETE)) {
            log.warn("capture: some packs drifted from a registered revision or are incomplete: see the lines above");
        }

        assertThat(reports).as("official exports read").anyMatch(report -> !report.cvat());
        assertThat(actions)
                .as("registered revisions whose artifact is missing and cannot be put back from this download")
                .doesNotContainKey(Action.ARTIFACT_MISSING);
    }
}
