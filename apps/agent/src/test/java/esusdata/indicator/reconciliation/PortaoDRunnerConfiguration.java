package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import esusdata.testsupport.LivePecAssumptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The properties of the two Portão D runners, parsed into a record each so that what a runner
 * <em>can</em> be told is a fact of its type and not a habit of its body (spec 2026-10-08 §18):
 *
 * <ul>
 *   <li>{@link Capture} reads the official exports of a directory and writes reference manifests
 *       and artifacts. It has no PEC, no execution plane and no registry, and it is refused when
 *       told of one;
 *   <li>{@link Diagnostic} reads captured manifests and the PEC and writes one masked matrix under
 *       the artifact directory. It has no registry, no policy and no output beside the artifact
 *       directory, none of it under {@code docs/}; {@code periods} only chooses which of the
 *       captured periods it runs, and the purpose of everything it computes is {@link
 *       ReferencePurpose#DIAGNOSTIC}.
 * </ul>
 *
 * A property of the other runner, or of the registry, is a refusal rather than something ignored:
 * a command line that mixes the two would otherwise do less than it says without saying so.
 * {@code observatorio.gate.d.mode} chooses the runner; unset, neither runs (the live tests skip),
 * and anything but {@code capture} or {@code diagnostic} is refused.
 */
final class PortaoDRunnerConfiguration {

    static final String MODE = "observatorio.gate.d.mode";
    static final String OFFICIAL_EXPORT_DIR = "observatorio.gate.d.official-export-dir";
    static final String OFFICIAL_EXPORT_IBGE = "observatorio.gate.d.official-export-ibge";
    static final String ARTIFACT_DIR = "observatorio.gate.d.artifact-dir";
    static final String MANIFEST_OUTPUT = "observatorio.gate.d.manifest-output";
    static final String MANIFESTS_DIR = "observatorio.gate.d.manifests-dir";
    static final String PERIODS = "observatorio.gate.d.periods";
    static final String ENV_FILE = "observatorio.execution-plane.live-pec.env-file";
    static final String BINARY = "observatorio.execution-plane.binary";
    static final String REGISTRY = "observatorio.gate.d.registry";
    static final String REPO_ROOT = "observatorio.gate.d.repo-root";
    static final String POLICY = "observatorio.gate.d.policy";

    /** The one directory name under which a runner never writes: where the versioned evidence lives. */
    static final String DOCS = "docs";

    private static final List<String> NOT_FOR_CAPTURE =
            List.of(ENV_FILE, BINARY, REGISTRY, REPO_ROOT, POLICY, MANIFESTS_DIR, PERIODS);
    private static final List<String> NOT_FOR_DIAGNOSTIC =
            List.of(REGISTRY, REPO_ROOT, POLICY, OFFICIAL_EXPORT_DIR, OFFICIAL_EXPORT_IBGE, MANIFEST_OUTPUT);

    /** Which runner the properties ask for. */
    enum Mode {
        CAPTURE,
        DIAGNOSTIC;

        /** The value of {@value PortaoDRunnerConfiguration#MODE}. */
        String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The capture runner: official exports in, reference manifests and artifacts out.
     *
     * @param exportDir the directory of the downloaded {@code .csv} files
     * @param municipalityIbge the 7-digit IBGE code every export must be about
     * @param artifactDir the content-addressed store the raw and normalized files go to
     * @param manifestOutput the directory the manifests are written to
     */
    record Capture(Path exportDir, String municipalityIbge, Path artifactDir, Path manifestOutput) {

        /**
         * The store holds the raw exports, INEs and team names included, so it is never under {@code
         * docs/}, not even through a link; the manifests, which carry none, may be.
         */
        Capture {
            if (leadsUnder(artifactDir, DOCS)) {
                throw new IllegalArgumentException(
                        "the capture never writes the raw exports under " + DOCS + "/: " + ARTIFACT_DIR);
            }
        }

        static Capture parse(Function<String, String> properties) {
            refuse(properties, NOT_FOR_CAPTURE, Mode.CAPTURE);
            return new Capture(
                    Path.of(required(properties, OFFICIAL_EXPORT_DIR)),
                    ReferenceFormats.ibge7(required(properties, OFFICIAL_EXPORT_IBGE)),
                    Path.of(required(properties, ARTIFACT_DIR)),
                    Path.of(required(properties, MANIFEST_OUTPUT)));
        }
    }

    /**
     * The diagnostic runner: captured manifests and the PEC in, one masked matrix out.
     *
     * @param manifestsDir the directory of the reference manifests the capture wrote
     * @param artifactDir the store the manifests' artifacts are in; the matrix goes to {@code
     *     <artifactDir>/diagnostico/<timestamp>}
     * @param envFile the PEC secret file
     * @param binary the execution plane binary
     * @param periods the captured periods to run; empty runs every captured period. It selects, it
     *     never decides: it is no authority for the gate
     */
    record Diagnostic(Path manifestsDir, Path artifactDir, Path envFile, String binary, Set<Quadrimestre> periods) {

        Diagnostic {
            periods = Set.copyOf(periods);
            if (leadsUnder(artifactDir, DOCS)) {
                throw new IllegalArgumentException("the diagnostic never writes under " + DOCS + "/: " + ARTIFACT_DIR);
            }
        }

        static Diagnostic parse(Function<String, String> properties) {
            refuse(properties, NOT_FOR_DIAGNOSTIC, Mode.DIAGNOSTIC);
            String envFile = properties.apply(ENV_FILE);
            return new Diagnostic(
                    Path.of(required(properties, MANIFESTS_DIR)),
                    Path.of(required(properties, ARTIFACT_DIR)),
                    envFile == null || envFile.isBlank() ? LivePecAssumptions.ENV_FILE : Path.of(envFile),
                    required(properties, BINARY),
                    periodsOf(properties.apply(PERIODS)));
        }

        /** Always {@link ReferencePurpose#DIAGNOSTIC}: nothing a diagnostic run is told changes it. */
        ReferencePurpose purpose() {
            return ReferencePurpose.DIAGNOSTIC;
        }
    }

    private PortaoDRunnerConfiguration() {}

    /**
     * The runner the properties ask for.
     *
     * @return empty when {@value #MODE} is not set
     * @throws IllegalArgumentException when it is set to anything but {@code capture} or {@code
     *     diagnostic}
     */
    static Optional<Mode> modeOf(Function<String, String> properties) {
        String value = properties.apply(MODE);
        if (value == null) {
            return Optional.empty();
        }
        for (Mode mode : Mode.values()) {
            if (mode.value().equals(value)) {
                return Optional.of(mode);
            }
        }
        throw new IllegalArgumentException("unknown " + MODE + ": use capture or diagnostic");
    }

    private static void refuse(Function<String, String> properties, List<String> forbidden, Mode mode) {
        List<String> given = new ArrayList<>();
        for (String property : forbidden) {
            if (properties.apply(property) != null) {
                given.add(property);
            }
        }
        if (!given.isEmpty()) {
            throw new IllegalArgumentException("the " + mode.value() + " runner takes no " + given);
        }
    }

    private static String required(Function<String, String> properties, String property) {
        String value = properties.apply(property);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("set -D" + property + "=<value>");
        }
        return value;
    }

    private static Set<Quadrimestre> periodsOf(String text) {
        Set<Quadrimestre> periods = new TreeSet<>();
        if (text == null || text.isBlank()) {
            return periods;
        }
        for (String period : text.split(",", -1)) {
            if (!periods.add(SiapsFormats.quadrimestre(period))) {
                throw new IllegalArgumentException("a period is listed twice in " + PERIODS);
            }
        }
        return periods;
    }

    /**
     * Whether {@code path} is, or would be created, under a directory called {@code name}: as it is
     * written, and as the filesystem resolves it, so that a link into such a directory (say {@code
     * artifacts -> docs/private}) is refused like the directory itself. The links are followed before
     * any {@code ..} is applied, as the writes will follow them; the part of the path that does not
     * exist yet has no link to follow and is taken as written, below the real path of its nearest
     * existing ancestor. A link that leads nowhere is refused rather than guessed.
     */
    private static boolean leadsUnder(Path path, String name) {
        Path absolute = path.toAbsolutePath();
        if (hasSegment(absolute.normalize(), name)) {
            return true;
        }
        Path existing = absolute;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return false;
        }
        try {
            return hasSegment(
                    existing.toRealPath().resolve(existing.relativize(absolute)).normalize(), name);
        } catch (IOException unresolvable) {
            throw new IllegalArgumentException(
                    "cannot resolve the path to tell whether it leads under " + name + "/", unresolvable);
        }
    }

    private static boolean hasSegment(Path path, String name) {
        for (Path segment : path) {
            if (segment.toString().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
