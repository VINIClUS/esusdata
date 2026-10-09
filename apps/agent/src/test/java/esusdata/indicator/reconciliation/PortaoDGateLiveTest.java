package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.GateCheck;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.GateCheck.PackCheck;
import esusdata.indicator.reconciliation.PortaoDCompatibilityRun.GateCheck.Summary;
import esusdata.indicator.reconciliation.PortaoDRunnerConfiguration.Mode;
import esusdata.run.worker.ReferenceScopedExtracts;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The gate check of the Portão D (spec 2026-10-08 §7.2 stage C and §16.2). Opt-in with {@code
 * -Dobservatorio.gate.d.mode=gate}; skipped without it, and never run by the CI.
 *
 * <p>Inputs: {@code -Dobservatorio.gate.d.repo-root=<dir>} (a clean working tree whose {@code HEAD}
 * holds the pre-registration) and {@code -Dobservatorio.gate.d.artifact-dir=<dir>} (the campaign's
 * store and cache; the summary goes to {@code <dir>/gate/resumo.md}). There is no period, no PEC and
 * no registry: the references are the GATE declarations of {@code
 * contracts/indicators/siaps-reference-policy.json}, which must be exactly those of {@code HEAD}.
 * For each pack it computes {@code gate_set_sha256} and checks that every GATE reference has a
 * dossier whose verdict authorizes the gate and whose {@code local_source_fingerprint} is that of
 * the extracts in the cache.
 *
 * <p><b>It does not write the registry.</b> {@code release-gates.json} is untouched; the verdict of
 * the set and the update of D come with {@code ReferenceSetVerdict} (PR D). An empty gate set, which
 * is every set until the evidence PR, is reported as empty and is not a failure.
 */
class PortaoDGateLiveTest {

    private static final Logger log = LoggerFactory.getLogger(PortaoDGateLiveTest.class);

    @Test
    void checksTheGateReferencesOfThePolicyAgainstTheirDossiersAndTheCachedSource() throws IOException {
        Assumptions.assumeTrue(
                PortaoDRunnerConfiguration.modeOf(System::getProperty)
                        .filter(Mode.GATE::equals)
                        .isPresent(),
                "Skipping: opt in with -D" + PortaoDRunnerConfiguration.MODE + "=gate");
        PortaoDRunnerConfiguration.GateRun configuration =
                PortaoDRunnerConfiguration.GateRun.parse(System::getProperty);

        Summary summary = GateCheck.check(
                configuration.repoRoot(),
                new GitRepository(configuration.repoRoot()),
                configuration.artifactDir(),
                new ReferenceScopedExtracts(configuration.artifactDir().resolve("extratos"), Clock.systemUTC()),
                ReleaseGateRegistry.registeredPacks());

        for (PackCheck pack : summary.packs()) {
            log.info(
                    "gate: {} gate_set_sha256 {}: {} GATE references, {}",
                    pack.ruleVersion(),
                    pack.gateSetSha256(),
                    pack.references().size(),
                    pack.ok() ? "all checked" : "some failed");
        }
        log.info("summary in {}", configuration.artifactDir().resolve("gate"));

        assertThat(summary.packs().stream().filter(pack -> !pack.ok()).map(PackCheck::ruleVersion))
                .as("sets with a GATE reference that failed: see gate/resumo.md")
                .isEmpty();
    }

    /** Git, through the command line, on the working tree of {@code root}: read-only commands only. */
    private record GitRepository(Path root) implements GateCheck.Repository {

        @Override
        public boolean isClean() throws IOException {
            return output("status", "--porcelain").isBlank();
        }

        @Override
        public Optional<String> committed(String repositoryPath) {
            try {
                return Optional.of(output("show", "HEAD:" + repositoryPath));
            } catch (IOException notThere) {
                return Optional.empty();
            }
        }

        private String output(String... arguments) throws IOException {
            List<String> command = new java.util.ArrayList<>(List.of("git", "-C", root.toString()));
            command.addAll(List.of(arguments));
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(false).start();
            try (InputStream stdout = process.getInputStream()) {
                ByteArrayOutputStream text = new ByteArrayOutputStream();
                stdout.transferTo(text);
                if (process.waitFor() != 0) {
                    throw new IOException("git " + arguments[0] + " failed");
                }
                return text.toString(StandardCharsets.UTF_8);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for git", interrupted);
            }
        }
    }
}
