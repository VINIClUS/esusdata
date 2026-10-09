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
 * The properties of the Portão D runners, parsed into a record each so that what a runner
 * <em>can</em> be told is a fact of its type and not a habit of its body (spec 2026-10-08 §18):
 *
 * <ul>
 *   <li>{@link Capture} reads the official exports of a directory and writes reference manifests
 *       and artifacts. It has no PEC, no execution plane and no registry, and it is refused when
 *       told of one;
 *   <li>{@link Diagnostic} reads captured manifests and the PEC and writes one masked matrix under
 *       the artifact directory. It has no registry, no policy and no output beside the artifact
 *       directory, none of it under {@code docs/} nor in a Git working tree; {@code periods} only chooses which of the
 *       captured periods it runs, and the purpose of everything it computes is {@link
 *       ReferencePurpose#DIAGNOSTIC}.
 *   <li>{@link Compatibility} reads the same manifests and the PEC and writes one compatibility
 *       dossier per reference to {@code dossier-dir}, which may be the versioned directory; its
 *       per-team detail and log go under the artifact directory. No registry, no policy;
 *   <li>{@link Replay} regenerates the dossiers of {@code dossier-dir} from the cached artifacts and
 *       compares bytes. It has no PEC, no execution plane and no period: it refuses to acquire;
 *   <li>{@link GateRun} checks the GATE declarations of the policy of the repository, which it reads
 *       from the working tree and from {@code HEAD}. It has no period, no PEC, no manifest or
 *       dossier directory of its own and no registry (yet).
 * </ul>
 *
 * A property of another runner, or of the registry, is a refusal rather than something ignored: a
 * command line that mixes them would otherwise do less than it says without saying so. {@code
 * observatorio.gate.d.mode} chooses the runner; unset, none runs (the live tests skip), and
 * anything but {@code capture}, {@code diagnostic}, {@code compatibility}, {@code replay} or {@code
 * gate} is refused.
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
    static final String DOSSIER_DIR = "observatorio.gate.d.dossier-dir";
    static final String PROFILES = "observatorio.gate.d.profiles";

    /** The methodology profiles, relative to the repository root. */
    static final String DEFAULT_PROFILES = "contracts/indicators/siaps-methodology-profiles.json";

    /** The one directory name under which a runner never writes: where the versioned evidence lives. */
    static final String DOCS = "docs";

    /** What marks the top of a Git working tree: a directory, or a file in a linked worktree. */
    static final String GIT = ".git";

    private static final List<String> NOT_FOR_CAPTURE =
            List.of(ENV_FILE, BINARY, REGISTRY, REPO_ROOT, POLICY, MANIFESTS_DIR, PERIODS, DOSSIER_DIR, PROFILES);
    private static final List<String> NOT_FOR_DIAGNOSTIC = List.of(
            REGISTRY,
            REPO_ROOT,
            POLICY,
            OFFICIAL_EXPORT_DIR,
            OFFICIAL_EXPORT_IBGE,
            MANIFEST_OUTPUT,
            DOSSIER_DIR,
            PROFILES);

    private static final List<String> NOT_FOR_COMPATIBILITY =
            List.of(REGISTRY, REPO_ROOT, POLICY, OFFICIAL_EXPORT_DIR, OFFICIAL_EXPORT_IBGE, MANIFEST_OUTPUT);
    private static final List<String> NOT_FOR_REPLAY = List.of(
            REGISTRY,
            REPO_ROOT,
            POLICY,
            ENV_FILE,
            BINARY,
            PERIODS,
            OFFICIAL_EXPORT_DIR,
            OFFICIAL_EXPORT_IBGE,
            MANIFEST_OUTPUT);
    private static final List<String> NOT_FOR_GATE = List.of(
            REGISTRY,
            POLICY,
            ENV_FILE,
            BINARY,
            PERIODS,
            MANIFESTS_DIR,
            DOSSIER_DIR,
            PROFILES,
            OFFICIAL_EXPORT_DIR,
            OFFICIAL_EXPORT_IBGE,
            MANIFEST_OUTPUT);

    /** Which runner the properties ask for. */
    enum Mode {
        CAPTURE,
        DIAGNOSTIC,
        COMPATIBILITY,
        REPLAY,
        GATE;

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
         * docs/} nor anywhere in a Git working tree, not even through a link; the manifests, which
         * carry none, may be.
         */
        Capture {
            if (leadsUnder(artifactDir, DOCS)) {
                throw new IllegalArgumentException(
                        "the capture never writes the raw exports under " + DOCS + "/: " + ARTIFACT_DIR);
            }
            if (isInAGitWorkingTree(artifactDir)) {
                throw new IllegalArgumentException("the capture never writes the raw exports in a Git working tree,"
                        + " where an add would stage them: " + ARTIFACT_DIR);
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
            if (isInAGitWorkingTree(artifactDir)) {
                throw new IllegalArgumentException("the diagnostic never writes the extracts in a Git working tree,"
                        + " where an add would stage them: " + ARTIFACT_DIR);
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

    /**
     * The compatibility runner: captured manifests and the PEC in, one dossier per reference out.
     *
     * @param manifestsDir the directory of the reference manifests the capture wrote
     * @param artifactDir the store the manifests' artifacts are in and the extracts are cached under
     *     ({@code <artifactDir>/extratos}); the per-team detail and the log go to {@code
     *     <artifactDir>/compatibilidade}
     * @param dossierDir where the dossiers go; may be {@code docs/indicadores/portoes/compatibilidade},
     *     the versioned directory, because a dossier carries masked counts and no INE
     * @param profiles the methodology profiles
     * @param envFile the PEC secret file
     * @param binary the execution plane binary
     * @param periods the captured periods to run; empty runs every captured period
     */
    record Compatibility(
            Path manifestsDir,
            Path artifactDir,
            Path dossierDir,
            Path profiles,
            Path envFile,
            String binary,
            Set<Quadrimestre> periods) {

        Compatibility {
            periods = Set.copyOf(periods);
            requireLocal(artifactDir, "the compatibility run", "the per-team detail");
        }

        static Compatibility parse(Function<String, String> properties) {
            refuse(properties, NOT_FOR_COMPATIBILITY, Mode.COMPATIBILITY);
            String envFile = properties.apply(ENV_FILE);
            return new Compatibility(
                    Path.of(required(properties, MANIFESTS_DIR)),
                    Path.of(required(properties, ARTIFACT_DIR)),
                    Path.of(required(properties, DOSSIER_DIR)),
                    profilesOf(properties),
                    envFile == null || envFile.isBlank() ? LivePecAssumptions.ENV_FILE : Path.of(envFile),
                    required(properties, BINARY),
                    periodsOf(properties.apply(PERIODS)));
        }
    }

    /**
     * The replay: the dossiers of a directory regenerated from the cached artifacts, byte for byte.
     *
     * @param manifestsDir the directory of the reference manifests
     * @param artifactDir the store and cache of the campaign
     * @param dossierDir the dossiers to reproduce
     * @param profiles the methodology profiles
     */
    record Replay(Path manifestsDir, Path artifactDir, Path dossierDir, Path profiles) {

        Replay {
            requireLocal(artifactDir, "the replay", "the regenerated detail");
        }

        static Replay parse(Function<String, String> properties) {
            refuse(properties, NOT_FOR_REPLAY, Mode.REPLAY);
            return new Replay(
                    Path.of(required(properties, MANIFESTS_DIR)),
                    Path.of(required(properties, ARTIFACT_DIR)),
                    Path.of(required(properties, DOSSIER_DIR)),
                    profilesOf(properties));
        }
    }

    /**
     * The gate check: the GATE declarations of the policy of a repository, and nothing to choose.
     *
     * @param repoRoot the root of the repository, whose policy, manifests and dossiers it reads
     * @param artifactDir the cache of the campaign; the summary goes to {@code <artifactDir>/gate}
     */
    record GateRun(Path repoRoot, Path artifactDir) {

        GateRun {
            requireLocal(artifactDir, "the gate check", "the summary");
        }

        static GateRun parse(Function<String, String> properties) {
            refuse(properties, NOT_FOR_GATE, Mode.GATE);
            return new GateRun(Path.of(required(properties, REPO_ROOT)), Path.of(required(properties, ARTIFACT_DIR)));
        }
    }

    private PortaoDRunnerConfiguration() {}

    /**
     * The runner the properties ask for.
     *
     * @return empty when {@value #MODE} is not set
     * @throws IllegalArgumentException when it is set to anything but a runner of {@link Mode}
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
        throw new IllegalArgumentException(
                "unknown " + MODE + ": use capture, diagnostic, compatibility, replay or gate");
    }

    /** The methodology profiles: the property, or the contract file of the repository. */
    private static Path profilesOf(Function<String, String> properties) {
        String given = properties.apply(PROFILES);
        if (given != null && !given.isBlank()) {
            return Path.of(given);
        }
        Path fromTheRoot = Path.of(DEFAULT_PROFILES);
        Path fromTheModule = Path.of("..", "..").resolve(DEFAULT_PROFILES);
        return !Files.isRegularFile(fromTheRoot) && Files.isRegularFile(fromTheModule) ? fromTheModule : fromTheRoot;
    }

    /** Where nothing carrying a team's INE is written: not under {@code docs/}, not in a Git working tree. */
    private static void requireLocal(Path artifactDir, String runner, String what) {
        if (leadsUnder(artifactDir, DOCS)) {
            throw new IllegalArgumentException(
                    runner + " never writes " + what + " under " + DOCS + "/: " + ARTIFACT_DIR);
        }
        if (isInAGitWorkingTree(artifactDir)) {
            throw new IllegalArgumentException(runner + " never writes " + what
                    + " in a Git working tree, where an add would stage it: " + ARTIFACT_DIR);
        }
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
     * artifacts -> docs/private}) is refused like the directory itself.
     */
    private static boolean leadsUnder(Path path, String name) {
        Path absolute = path.toAbsolutePath();
        return hasSegment(absolute.normalize(), name) || hasSegment(resolved(absolute), name);
    }

    /**
     * Whether {@code path}, as the filesystem resolves it, is or would be created in a Git working
     * tree: some directory from it up holds a {@code .git}. Ignored or not, the raw exports and the
     * extracts never go there.
     */
    private static boolean isInAGitWorkingTree(Path path) {
        for (Path directory = resolved(path.toAbsolutePath()); directory != null; directory = directory.getParent()) {
            if (Files.exists(directory.resolve(GIT), LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where the writes to {@code absolute} will land: the real path of its nearest existing ancestor,
     * with the part that does not exist yet, which has no link to follow, appended as written. The
     * links are followed before any {@code ..} is applied, as the writes follow them; a link that
     * leads nowhere is refused rather than guessed.
     */
    private static Path resolved(Path absolute) {
        Path existing = absolute;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute.normalize();
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
        } catch (IOException unresolvable) {
            throw new IllegalArgumentException("cannot resolve the path a runner would write to", unresolvable);
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
