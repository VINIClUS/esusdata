package esusdata.indicator.reconciliation;

import esusdata.indicator.ReleaseGateRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The offline checks of the evidence of the Portão D (ADR 0034), each a function over the registry,
 * the policy and the repository root that returns its problems, one sentence each, never an
 * identifier. {@code ReleaseGatesConsistencyTest}, {@code PortaoDEvidenceConsistencyTest} and {@code
 * PortaoDEvidencePrivacyTest} run them on the real repository, where today nothing is decided and
 * they hold vacuously, and on fixtures built by the real writers ({@link PortaoDEvidenceFixtures}),
 * where each check is shown to refuse what it is for.
 *
 * <p>They read committed bytes only. Regenerating a dossier from the raw artifacts is not here:
 * that is {@code PortaoDEvidenceReplayLiveTest}.
 */
public final class PortaoDEvidenceChecks {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String JSON = ".json";
    private static final String MARKDOWN = ".md";
    private static final String SCHEMA_VERSION = "schema_version";
    private static final String REFERENCE_ID = "reference_id";
    private static final String RULE_VERSION = "rule_version";
    private static final String REFERENCES = "references";
    private static final String STATUS = "status";
    private static final String CHECK = "check";
    private static final String PASSED = "PASSED";
    private static final String FAILED = "FAILED";
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MUNICIPALITY = Pattern.compile("(\"municipality_ibge\":\\s*\")\\d{7}\"");
    private static final Pattern IPV4 = Pattern.compile("(?<![\\d.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\d.])");
    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    private static final Pattern SECRET_KEY =
            Pattern.compile("(?i)\"[a-z0-9_]*(?:senha|password|token)[a-z0-9_]*\"\\s*:");

    /** The fields of a dossier (spec §8.4): none may be missing. */
    private static final List<String> DOSSIER_FIELDS = List.of(
            SCHEMA_VERSION,
            REFERENCE_ID,
            "pack",
            RULE_VERSION,
            "quadrimestre",
            "reference_manifest_sha256",
            "local_source_fingerprint",
            "official_methodology_sources",
            "normative_deltas",
            "probe_results",
            "official_field_comparison",
            "coverage",
            "verdict",
            "reason",
            "declared_limitations",
            "declared_conventions");

    private PortaoDEvidenceChecks() {}

    /** The policy of {@code repoRoot} for the rules compiled into this release. */
    public static ReferencePolicy policyOf(Path repoRoot) throws IOException {
        return ReferencePolicy.load(
                repoRoot.resolve(PortaoDCompatibilityRun.GateCheck.POLICY_FILE), ReleaseGateRegistry.registeredPacks());
    }

    // ---- D of the registry

    /**
     * What is wrong with every {@code D} that is {@code PASSED} or {@code FAILED}: its check is not
     * the pack's {@code @2} one (an {@code @1} is never accepted for D), it does not cite the set
     * summary, or the summary is not the set the policy now declares. The summary must carry the
     * {@code gate_set_sha256} recomputed from the current policy, list exactly the GATE references
     * of the set, each with the manifest and the dossier the declaration pins (existing, with their
     * hashes), a declaration {@code ACTIVE} whose compatibility is the dossier's verdict, and a
     * status that follows from theirs. A pending {@code D} has nothing to check.
     */
    public static List<String> registryProblems(JsonNode registry, ReferencePolicy policy, Path repoRoot)
            throws IOException {
        List<String> problems = new ArrayList<>();
        for (JsonNode entry : registry.path("packs")) {
            JsonNode gate = entry.path("gates").path("D");
            String status = gate.path(STATUS).asString("");
            if (PASSED.equals(status) || FAILED.equals(status)) {
                problems.addAll(decidedProblems(entry, gate, policy, repoRoot));
            }
        }
        return problems;
    }

    private static List<String> decidedProblems(JsonNode entry, JsonNode gate, ReferencePolicy policy, Path repoRoot)
            throws IOException {
        String pack = entry.path("pack").asString("");
        String ruleVersion = entry.path(RULE_VERSION).asString("");
        String who = "D of " + ruleVersion + ": ";
        ReferenceSet set;
        try {
            set = policy.referenceSet(pack, ruleVersion);
        } catch (IllegalArgumentException unknown) {
            return List.of(who + "the policy has no reference set for it");
        }
        List<String> problems = new ArrayList<>();
        String check = gate.path(CHECK).asString("");
        if (!ReferencePolicy.checkFor(pack).equals(check)) {
            problems.add(who + "the check is " + check + ", not " + ReferencePolicy.checkFor(pack));
        }
        Optional<JsonNode> item = summaryItem(gate);
        String expected = SummaryWriter.SET_SUMMARY_DIR + SummaryWriter.summaryFileName(ruleVersion, JSON);
        if (item.isEmpty() || !expected.equals(item.get().path("ref").asString(""))) {
            problems.add(who + "it does not cite the set summary " + expected);
            return problems;
        }
        Path file = repoRoot.resolve(expected);
        if (!Files.isRegularFile(file)
                || !hashOf(file).equals(item.get().path("sha256").asString(""))) {
            problems.add(who + "the set summary is missing or is not the one cited");
            return problems;
        }
        problems.addAll(
                summaryProblems(who, gate.path(STATUS).asString(""), MAPPER.readTree(file.toFile()), set, repoRoot));
        return problems;
    }

    private static Optional<JsonNode> summaryItem(JsonNode gate) {
        for (JsonNode item : gate.path("evidence")) {
            if (RegistryUpdater.EVIDENCE_KIND.equals(item.path("kind").asString(""))) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private static List<String> summaryProblems(
            String who, String status, JsonNode summary, ReferenceSet set, Path repoRoot) throws IOException {
        List<String> problems = new ArrayList<>();
        if (!set.gateSetSha256().equals(summary.path("gate_set_sha256").asString(""))) {
            problems.add(who + "gate_set_sha256 of the summary is not the one of the current policy");
        }
        if (!set.pack().equals(summary.path("pack").asString(""))
                || !set.ruleVersion().equals(summary.path(RULE_VERSION).asString(""))
                || !set.check().equals(summary.path(CHECK).asString(""))) {
            problems.add(who + "the summary is of another pack, rule version or check");
        }
        if (!status.equals(summary.path(STATUS).asString(""))) {
            problems.add(who + "the status of the summary is not the one recorded");
        }
        List<String> declared = set.references().stream()
                .filter(declaration -> declaration.purpose() == ReferencePurpose.GATE)
                .map(ReferenceDeclaration::referenceId)
                .sorted()
                .toList();
        List<String> listed = new ArrayList<>();
        summary.path(REFERENCES)
                .forEach(reference -> listed.add(reference.path(REFERENCE_ID).asString("")));
        listed.sort(Comparator.naturalOrder());
        if (!declared.equals(listed)) {
            problems.add(who + "the summary does not list exactly the GATE references of the set");
        }
        for (JsonNode reference : summary.path(REFERENCES)) {
            problems.addAll(referenceProblems(who, reference, set, repoRoot));
        }
        problems.addAll(statusProblems(who, status, summary.path(REFERENCES)));
        return problems;
    }

    private static List<String> referenceProblems(String who, JsonNode reference, ReferenceSet set, Path repoRoot)
            throws IOException {
        String id = reference.path(REFERENCE_ID).asString("");
        Optional<ReferenceDeclaration> found = set.references().stream()
                .filter(declaration ->
                        declaration.referenceId().equals(id) && declaration.purpose() == ReferencePurpose.GATE)
                .findFirst();
        if (found.isEmpty()) {
            return List.of();
        }
        ReferenceDeclaration declaration = found.get();
        List<String> problems = new ArrayList<>(rowProblems(who + id, reference));
        if (declaration.status() != ReferenceStatus.ACTIVE) {
            problems.add(who + id + " is not ACTIVE");
        }
        problems.addAll(pinProblems(
                who + id + " manifest",
                repoRoot,
                reference.path("reference_manifest_ref").asString(""),
                reference.path("reference_manifest_sha256").asString(""),
                ReferencePolicy.manifestPath(id),
                declaration.referenceManifestSha256()));
        problems.addAll(pinProblems(
                who + id + " dossier",
                repoRoot,
                reference.path("compatibility_evidence_ref").asString(""),
                reference.path("compatibility_evidence_sha256").asString(""),
                String.valueOf(declaration.compatibilityEvidenceRef()),
                String.valueOf(declaration.compatibilityEvidenceSha256())));
        problems.addAll(compatibilityProblems(who + id, reference, set, declaration, repoRoot));
        return problems;
    }

    /**
     * The status of a reference follows from its rows: each row's t is the threshold its n_s gives
     * ({@link Comparison#threshold}), each verdict is the one its d and t give (within the threshold
     * passes, beyond it fails, a negative figure is no verdict, no figures is not evaluated), and a row
     * is not evaluated only when neither side has a team to show (production leaves a row unevaluated
     * only with no team on either side, so both counts are below the mask); a row that fails makes the reference
     * FAILED, and PASSED needs an evaluated row and every evaluated one passing. A PENDING reference
     * has no failing row: a failure is never softened.
     */
    private static List<String> rowProblems(String what, JsonNode reference) {
        List<String> problems = new ArrayList<>();
        boolean fails = false;
        boolean passes = false;
        for (JsonNode row : reference.path("rows")) {
            String verdict = row.path("row_verdict").asString("");
            problems.addAll(figureProblems(what, row, verdict));
            fails |= SummaryWriter.ROW_FAILS.equals(verdict);
            passes |= SummaryWriter.ROW_PASSES.equals(verdict);
        }
        String status = reference.path(STATUS).asString("");
        String follows = fails ? FAILED : passes ? PASSED : "PENDING";
        boolean consistent = status.equals(follows) || ("PENDING".equals(status) && !fails);
        if (!consistent) {
            problems.add(what + ": the status " + status + " does not follow from its rows");
        }
        return problems;
    }

    /** What is wrong with the figures of one row against its verdict. */
    private static List<String> figureProblems(String what, JsonNode row, String verdict) {
        List<String> problems = new ArrayList<>();
        String t = row.path("t").asString("");
        if (!SummaryWriter.DASH.equals(t)
                && !t.equals(thresholdOf(row.path("n_s").asString("")))) {
            problems.add(what + ": a row's t is not the threshold its n_s gives");
        }
        if (!verdict.equals(verdictOf(row.path("d").asString(""), t))) {
            problems.add(what + ": a row's verdict is not the one its d and t give");
        }
        boolean bothBelowMask = SummaryWriter.MASKED.equals(row.path("n_s").asString(""))
                && SummaryWriter.MASKED.equals(row.path("n_l").asString(""));
        if (SummaryWriter.ROW_NOT_EVALUATED.equals(verdict) && !bothBelowMask) {
            problems.add(what + ": a row with teams is not evaluated");
        }
        return problems;
    }

    /**
     * The threshold of a row from its n_s. Below the mask the formula gives the floor for every count
     * ({@code max(2, ceil(0.15 × 9)) = 2}), so a masked n_s still fixes it.
     */
    static String thresholdOf(String nS) {
        if (SummaryWriter.MASKED.equals(nS)) {
            return Integer.toString(Comparison.threshold(SummaryWriter.MASK_BELOW - 1));
        }
        try {
            return Integer.toString(Comparison.threshold(Integer.parseInt(nS)));
        } catch (NumberFormatException notACount) {
            return "";
        }
    }

    private static String verdictOf(String d, String t) {
        if (SummaryWriter.DASH.equals(d) && SummaryWriter.DASH.equals(t)) {
            return SummaryWriter.ROW_NOT_EVALUATED;
        }
        try {
            int distance = Integer.parseInt(d);
            int threshold = Integer.parseInt(t);
            if (distance < 0 || threshold < 0) {
                return "";
            }
            return distance <= threshold ? SummaryWriter.ROW_PASSES : SummaryWriter.ROW_FAILS;
        } catch (NumberFormatException notFigures) {
            return "";
        }
    }

    /** The summary cites the very file the declaration pins, and that file is there with that hash. */
    private static List<String> pinProblems(
            String what, Path repoRoot, String ref, String sha256, String pinnedRef, String pinnedSha256)
            throws IOException {
        if (!pinnedRef.equals(ref) || !pinnedSha256.equals(sha256)) {
            return List.of(what + " in the summary is not the one the declaration pins");
        }
        Path file = repoRoot.resolve(ref);
        if (!Files.isRegularFile(file) || !hashOf(file).equals(pinnedSha256)) {
            return List.of(what + " is missing or has changed since it was pinned");
        }
        return List.of();
    }

    /**
     * The summary agrees with the dossier it cites: the dossier stands for the declaration in the set
     * as the gate requires ({@link DossierEvidence#problems}: same reference, rule version and
     * manifest, the declared verdict, one that authorizes the gate), the summary states that
     * compatibility, and the local source the summary was decided on is the one the dossier was
     * decided on (another source leaves the reference PENDING, spec §14).
     */
    private static List<String> compatibilityProblems(
            String what, JsonNode reference, ReferenceSet set, ReferenceDeclaration declaration, Path repoRoot)
            throws IOException {
        Path file = repoRoot.resolve(String.valueOf(declaration.compatibilityEvidenceRef()));
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        Optional<DossierEvidence> dossier =
                DossierEvidence.read(declaration.compatibilityEvidenceRef(), Files.readAllBytes(file));
        if (dossier.isEmpty()) {
            return List.of(what + ": the dossier is not a JSON object");
        }
        List<String> problems = new ArrayList<>();
        dossier.get().problems(set, declaration).forEach(problem -> problems.add(what + ": " + problem));
        if (!dossier.get()
                .localSourceFingerprint()
                .equals(reference.path("local_source_fingerprint").asString(""))) {
            problems.add(what + ": the local source in the summary is not the one the dossier was decided on");
        }
        if (!declaration
                .compatibility()
                .name()
                .equals(reference.path("compatibility").asString(""))) {
            problems.add(what + ": the compatibility in the summary is not the declared one");
        }
        return problems;
    }

    /** {@code PASSED} needs every reference to pass, {@code FAILED} at least one to fail. */
    private static List<String> statusProblems(String who, String status, JsonNode references) {
        List<String> statuses = new ArrayList<>();
        references.forEach(reference -> statuses.add(reference.path(STATUS).asString("")));
        boolean consistent = PASSED.equals(status)
                ? !statuses.isEmpty() && statuses.stream().allMatch(PASSED::equals)
                : statuses.contains(FAILED);
        return consistent ? List.of() : List.of(who + "the status does not follow from the statuses of the references");
    }

    // ---- the files under docs/indicadores/portoes

    /**
     * What is wrong with the manifests, the dossiers and the set summaries committed: a declaration
     * cites a file that is missing or has another hash; a manifest or a dossier is cited by no
     * declaration, or with another hash; a dossier lacks a field of the envelope or is not {@code
     * schema_version} 1; a {@code .md} is not byte for byte the rendering of its {@code .json}.
     */
    public static List<String> fileProblems(ReferencePolicy policy, Path repoRoot) throws IOException {
        Map<String, String> manifests = new TreeMap<>();
        Map<String, String> dossiers = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (ReferenceSet set : policy.referenceSets()) {
            for (ReferenceDeclaration declaration : set.references()) {
                manifests.put(
                        ReferencePolicy.manifestPath(declaration.referenceId()), declaration.referenceManifestSha256());
                if (declaration.compatibilityEvidenceRef() != null) {
                    dossiers.put(declaration.compatibilityEvidenceRef(), declaration.compatibilityEvidenceSha256());
                }
            }
        }
        problems.addAll(citedProblems(repoRoot, manifests));
        problems.addAll(citedProblems(repoRoot, dossiers));
        problems.addAll(uncitedProblems(repoRoot, ReferencePolicy.MANIFEST_DIR, manifests));
        problems.addAll(uncitedProblems(repoRoot, ReferencePolicy.DOSSIER_DIR, dossiers));
        problems.addAll(dossierEnvelopeProblems(repoRoot));
        problems.addAll(
                markdownProblems(repoRoot, ReferencePolicy.DOSSIER_DIR, CompatibilityDossierWriter::renderMarkdown));
        problems.addAll(summaryEnvelopeProblems(repoRoot));
        problems.addAll(markdownProblems(repoRoot, SummaryWriter.SET_SUMMARY_DIR, SummaryWriter::renderSetMarkdown));
        return problems;
    }

    private static List<String> citedProblems(Path repoRoot, Map<String, String> pinned) throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> pin : pinned.entrySet()) {
            Path file = repoRoot.resolve(pin.getKey());
            if (Files.isRegularFile(file)) {
                if (!hashOf(file).equals(pin.getValue())) {
                    problems.add(pin.getKey() + " is not the file its declaration pins");
                }
            } else {
                problems.add(pin.getKey() + " is cited by a declaration and does not exist");
            }
        }
        return problems;
    }

    private static List<String> uncitedProblems(Path repoRoot, String directory, Map<String, String> pinned)
            throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : filesOf(repoRoot.resolve(directory), JSON)) {
            String ref = directory + file.getFileName();
            if (!pinned.containsKey(ref)) {
                problems.add(ref + " is cited by no declaration");
            }
        }
        return problems;
    }

    private static List<String> dossierEnvelopeProblems(Path repoRoot) throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : filesOf(repoRoot.resolve(ReferencePolicy.DOSSIER_DIR), JSON)) {
            Optional<JsonNode> tree = parse(file);
            String name = file.getFileName().toString();
            if (tree.isEmpty() || !tree.get().isObject()) {
                problems.add(name + " is not a JSON object");
                continue;
            }
            for (String field : DOSSIER_FIELDS) {
                if (!tree.get().has(field)) {
                    problems.add(name + " lacks " + field);
                }
            }
            if (!ReferencePolicy.DOSSIER_SCHEMA_VERSION.equals(
                    tree.get().path(SCHEMA_VERSION).asString(""))) {
                problems.add(name + " is not schema_version " + ReferencePolicy.DOSSIER_SCHEMA_VERSION);
            }
        }
        return problems;
    }

    private static List<String> summaryEnvelopeProblems(Path repoRoot) throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : filesOf(repoRoot.resolve(SummaryWriter.SET_SUMMARY_DIR), JSON)) {
            Optional<JsonNode> tree = parse(file);
            if (tree.isEmpty()
                    || !SummaryWriter.SET_SUMMARY_SCHEMA_VERSION.equals(
                            tree.get().path(SCHEMA_VERSION).asString(""))) {
                problems.add(file.getFileName() + " is not a set summary of schema_version "
                        + SummaryWriter.SET_SUMMARY_SCHEMA_VERSION);
            }
        }
        return problems;
    }

    /** Every {@code .md} of the directory is the rendering of the {@code .json} beside it, and has one. */
    private static List<String> markdownProblems(Path repoRoot, String directory, Function<byte[], String> render)
            throws IOException {
        List<String> problems = new ArrayList<>();
        Path dir = repoRoot.resolve(directory);
        Set<String> markdowns = new HashSet<>();
        for (Path file : filesOf(dir, MARKDOWN)) {
            markdowns.add(file.getFileName().toString());
        }
        for (Path json : filesOf(dir, JSON)) {
            String name = json.getFileName().toString();
            String markdown = name.substring(0, name.length() - JSON.length()) + MARKDOWN;
            if (!markdowns.remove(markdown)) {
                problems.add(markdown + " is missing: the rendering of " + name);
                continue;
            }
            if (!renders(render, json, dir.resolve(markdown))) {
                problems.add(markdown + " is not the rendering of " + name);
            }
        }
        markdowns.forEach(extra -> problems.add(extra + " has no .json beside it"));
        return problems;
    }

    /** Whatever keeps a JSON from rendering means its {@code .md} is not the rendering of it. */
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private static boolean renders(Function<byte[], String> render, Path json, Path markdown) throws IOException {
        try {
            return render.apply(Files.readAllBytes(json)).equals(Files.readString(markdown, StandardCharsets.UTF_8));
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    // ---- privacy

    /**
     * Where the manifests, the dossiers and the set summaries would expose an identifier. A dossier
     * is held to the very guard the dossier writer applies before it writes (so a long digit run in a
     * source URL, which the writer allows, is allowed here); a manifest and a set summary, which carry
     * hashes and the municipality code of the reference, are scanned as text once those are set
     * aside. (The capture moment of a manifest is cut to the second, so it has no long digit run.) All of them are refused if they hold an IPv4 address or an e-mail, and a JSON one a key
     * named for a password, a token or a {@code senha}.
     */
    public static List<String> privacyProblems(Path repoRoot) throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : filesOf(repoRoot.resolve(ReferencePolicy.DOSSIER_DIR), JSON)) {
            Optional<JsonNode> tree = parse(file);
            tree.ifPresent(node -> CompatibilityDossierWriter.identifiersIn(node)
                    .forEach(path -> problems.add(file.getFileName() + " exposes an identifier at " + path)));
            problems.addAll(commonPrivacyProblems(file));
        }
        for (Path file : filesOf(repoRoot.resolve(ReferencePolicy.DOSSIER_DIR), MARKDOWN)) {
            problems.addAll(addressProblems(file));
        }
        for (String directory : List.of(ReferencePolicy.MANIFEST_DIR, SummaryWriter.SET_SUMMARY_DIR)) {
            for (Path file : filesOf(repoRoot.resolve(directory), JSON, MARKDOWN)) {
                problems.addAll(textProblems(file));
                problems.addAll(commonPrivacyProblems(file));
            }
        }
        return problems;
    }

    private static List<String> commonPrivacyProblems(Path file) throws IOException {
        List<String> problems = new ArrayList<>(addressProblems(file));
        if (file.getFileName().toString().endsWith(JSON)
                && SECRET_KEY
                        .matcher(Files.readString(file, StandardCharsets.UTF_8))
                        .find()) {
            problems.add(file.getFileName() + " has a key named for a password or a token");
        }
        return problems;
    }

    private static List<String> addressProblems(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        List<String> problems = new ArrayList<>();
        if (IPV4.matcher(text).find()) {
            problems.add(file.getFileName() + " has an IP address");
        }
        if (EMAIL.matcher(text).find()) {
            problems.add(file.getFileName() + " has an e-mail address");
        }
        return problems;
    }

    private static List<String> textProblems(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String rest =
                HASH.matcher(MUNICIPALITY.matcher(text).replaceAll("$1\"")).replaceAll("");
        return CompatibilityDossierWriter.looksLikeIdentifier(rest)
                ? List.of(file.getFileName() + " has an INE, a CNES, a UUID or a digest")
                : List.of();
    }

    // ---- files

    private static List<Path> filesOf(Path directory, String... extensions) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> Arrays.stream(extensions)
                            .anyMatch(extension -> file.getFileName().toString().endsWith(extension)))
                    .sorted()
                    .toList();
        }
    }

    private static Optional<JsonNode> parse(Path file) throws IOException {
        try {
            return Optional.of(MAPPER.readTree(Files.readAllBytes(file)));
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
    }

    private static String hashOf(Path file) throws IOException {
        return SummaryWriter.sha256(file);
    }
}
