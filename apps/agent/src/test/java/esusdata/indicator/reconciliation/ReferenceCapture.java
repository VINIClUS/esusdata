package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.OfficialTeamExportCsvParser.ExpectedScope;
import esusdata.indicator.reconciliation.ReferenceDrift.DriftStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Captures the official team exports of a directory as reference revisions (spec 2026-10-08 §8.2
 * and §16.3): for each export and each of the eight packs (C1 to C7 and the Nota Final), the
 * subset is stored in the content-addressed {@link ReferenceArtifactStore} and its manifest is
 * written to the manifest directory as {@code <reference-id>.json}. It reads no PEC and writes no
 * registry and no policy.
 *
 * <p>What it does with a file: a CVAT report (its header has a {@code Dimensão} column, where the
 * team export has {@code Indicador}) is skipped, explicitly, because it is a different report; any
 * other file is parsed by {@link OfficialTeamExportCsvParser} and a file it refuses refuses the
 * whole capture, <em>before anything is written</em>. A pack some team has no row for is reported
 * {@link Action#INCOMPLETE} and not captured (nothing is filled in); the others are.
 *
 * <p>What it does with a reference that is already registered in the manifest directory: the same
 * normalized content is {@link Action#UNCHANGED} (the manifest is not touched; the artifact is put
 * back in the store if it lacks it, from the very raw bytes the manifest pins), and {@link
 * Action#ARTIFACT_MISSING} when the store lacks it and the file is another download of it: other raw
 * bytes stored under the registered manifest would make an artifact that manifest refuses to load,
 * so nothing is written. Different content is {@link Action#DRIFT} and becomes the next
 * revision, {@code r<k+1>}; no manifest and no revision is ever overwritten. Exports of one
 * quadrimestre are numbered in the order the SIAPS says it generated them, not the order of their
 * names.
 *
 * <p>The report names files by their position in the directory, and carries counts, reference ids
 * and hashes only: never an INE, a team or a row. A manifest does not name the file either: it calls
 * the download by its raw hash, whatever name the file was saved under.
 */
final class ReferenceCapture {

    static final String SOURCE_DESCRIPTION = "SIAPS / Avaliação do Quadrimestre";

    private static final String CSV = ".csv";
    private static final String JSON = ".json";
    private static final String CVAT_COLUMN = "DIMENSAO";
    private static final Pattern MANIFEST_FILE =
            Pattern.compile("[a-z]{2}-\\d{7}-\\d{4}q[1-3]-(?:c[1-7]|ciii)-team-r([1-9]\\d*)\\.json");
    private static final Pattern REVISION = Pattern.compile("-r([1-9]\\d*)");

    private final Path artifactDir;
    private final Path manifestOutput;
    private final String uf;
    private final Clock clock;

    /** What happened to one pack of one export. */
    enum Action {
        /** A reference the manifest directory did not have: revision 1. */
        CAPTURED,
        /** The manifest directory has these normalized contents; nothing was written. */
        UNCHANGED,
        /** The SIAPS changed the contents of a registered reference: a new revision, the old one kept. */
        DRIFT,
        /** Some team has no row for the pack; nothing about it was captured. */
        INCOMPLETE,
        /**
         * The manifest directory has these normalized contents but the store lacks their artifact, and
         * this file is not the one the manifest pins (another download, with other raw bytes): nothing
         * was written. The original file puts the artifact back.
         */
        ARTIFACT_MISSING,
        /**
         * The manifest directory has these normalized contents and the store has their directory, but
         * not the artifact the manifest pins: the same contents under the raw bytes of another
         * download, stored by another capture. Nothing was written; the registered revision loads
         * again once the store's directory is the one its own capture wrote.
         */
        ARTIFACT_MISMATCH
    }

    /**
     * The outcome for one pack.
     *
     * @param referenceId the reference id (the revision it was captured as, or the registered one it
     *     equals); {@code null} when incomplete
     * @param normalizedSha256 the hash of its normalized content; {@code null} when incomplete
     * @param detail why it is incomplete (counts only); empty otherwise
     */
    record PackReport(GatePack pack, Action action, String referenceId, String normalizedSha256, String detail) {

        String line() {
            String line = action == Action.INCOMPLETE
                    ? "  %s INCOMPLETE".formatted(pack.code())
                    : "  %s %s %s normalized=%s".formatted(pack.code(), action, referenceId, normalizedSha256);
            return detail.isEmpty() ? line : line + ": " + detail;
        }
    }

    /**
     * The outcome for one file of the directory.
     *
     * @param position its place in the sorted list of the {@code .csv} files; files are never named
     * @param cvat true when it was skipped as a CVAT report
     * @param quadrimestre the quadrimestre of a team export; {@code null} for a CVAT report
     * @param status its revision status; {@code null} for a CVAT report
     * @param packs the eight packs of a team export; empty for a CVAT report
     */
    record FileReport(int position, boolean cvat, String quadrimestre, OfficialStatus status, List<PackReport> packs) {

        FileReport {
            packs = List.copyOf(packs);
        }

        List<String> lines() {
            if (cvat) {
                return List.of(
                        "official export %d: skipped, a CVAT report (its header has a Dimensão column), not a team export"
                                .formatted(position));
            }
            List<String> lines = new ArrayList<>();
            lines.add("official export %d: %s %s".formatted(position, quadrimestre, status));
            packs.forEach(pack -> lines.add(pack.line()));
            return lines;
        }
    }

    /** A file read and parsed, ready to be stored. */
    private record Export(int position, Path file, String rawSha256, OfficialTeamReference reference) {

        boolean isCvat() {
            return reference == null;
        }
    }

    /**
     * @param artifactDir the content-addressed store
     * @param manifestOutput where the manifests are written; created when something is captured
     * @param uf the two-letter code of the state, which a reference id carries (the file does not say it)
     * @param clock the clock the capture moment is read from
     */
    ReferenceCapture(Path artifactDir, Path manifestOutput, String uf, Clock clock) {
        this.artifactDir = artifactDir;
        this.manifestOutput = manifestOutput;
        this.uf = uf;
        this.clock = clock;
    }

    /**
     * Captures every team export of {@code exportDir}.
     *
     * @param municipalityIbge the 7-digit IBGE code every export must be about
     * @return one report per {@code .csv} file, in the order of the files
     * @throws IllegalArgumentException if a file is neither a CVAT report nor a team export of that
     *     municipality; nothing has been written then
     * @throws IllegalStateException if the directory holds no team export at all, or the manifest
     *     directory holds a file the capture cannot take as one of its own registered manifests;
     *     nothing has been written then either
     */
    List<FileReport> capture(Path exportDir, String municipalityIbge) throws IOException {
        List<Export> exports = read(exportDir, ExpectedScope.ofMunicipality(municipalityIbge));
        ReferenceArtifactStore store = new ReferenceArtifactStore(artifactDir);
        List<SiapsReferenceManifest> registered = registered(municipalityIbge);
        Map<Integer, FileReport> reports = new TreeMap<>();
        for (Export export : exports) {
            if (export.isCvat()) {
                reports.put(export.position(), new FileReport(export.position(), true, null, null, List.of()));
            }
        }
        Comparator<Export> oldestFirst = Comparator.comparing(
                        (Export export) -> export.reference().quadrimestre())
                .thenComparing(export -> export.reference().officialGeneratedAt())
                .thenComparingInt(Export::position);
        for (Export export : exports.stream()
                .filter(each -> !each.isCvat())
                .sorted(oldestFirst)
                .toList()) {
            List<PackReport> packs = new ArrayList<>();
            for (GatePack pack : GatePack.allWithNotaFinal()) {
                // the Nota Final comes last, and names what this download made of each pack
                List<String> siblings = pack.isNotaFinal() ? siblingsOf(packs) : List.of();
                packs.add(capture(export, pack, store, registered, siblings));
            }
            reports.put(
                    export.position(),
                    new FileReport(
                            export.position(),
                            false,
                            export.reference().quadrimestre(),
                            export.reference().officialStatus(),
                            packs));
        }
        return List.copyOf(reports.values());
    }

    // ---- reading: nothing is written until every file is understood

    private static List<Export> read(Path exportDir, ExpectedScope scope) throws IOException {
        List<Path> files;
        try (Stream<Path> listed = Files.list(exportDir)) {
            files = listed.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(CSV))
                    .sorted()
                    .toList();
        }
        List<Export> exports = new ArrayList<>();
        for (int position = 0; position < files.size(); position++) {
            byte[] raw = Files.readAllBytes(files.get(position));
            if (isCvatReport(raw)) {
                exports.add(new Export(position, files.get(position), SummaryWriter.sha256(raw), null));
                continue;
            }
            try {
                exports.add(new Export(
                        position,
                        files.get(position),
                        SummaryWriter.sha256(raw),
                        OfficialTeamExportCsvParser.parse(raw, scope)));
            } catch (IllegalArgumentException refused) {
                throw new IllegalArgumentException(
                        "file %d of the export directory is neither a CVAT report nor a team export: %s"
                                .formatted(position, refused.getMessage()),
                        refused);
            }
        }
        if (exports.stream().allMatch(Export::isCvat)) {
            throw new IllegalStateException("the export directory holds no official team export to capture");
        }
        return exports;
    }

    /**
     * True when the first multi-column line of the file (its header) has a {@code Dimensão} column:
     * the CVAT report has one where the team export has {@code Indicador}.
     */
    static boolean isCvatReport(byte[] raw) {
        String text = new String(raw, StandardCharsets.UTF_8);
        for (List<String> record : SiapsCsv.records(text.startsWith("﻿") ? text.substring(1) : text)) {
            if (record.size() != 1) {
                return record.stream().map(SiapsCsv::normalize).anyMatch(CVAT_COLUMN::equals);
            }
        }
        return false;
    }

    /**
     * The revisions of C1 to C7 this download holds: captured from it, or registered before with the
     * same content (unchanged, or with its artifact missing). A pack it left incomplete has none.
     */
    private static List<String> siblingsOf(List<PackReport> packs) {
        return packs.stream()
                .map(PackReport::referenceId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    // ---- capturing one pack

    private PackReport capture(
            Export export,
            GatePack pack,
            ReferenceArtifactStore store,
            List<SiapsReferenceManifest> registered,
            List<String> siblings)
            throws IOException {
        byte[] raw = Files.readAllBytes(export.file());
        if (!SummaryWriter.sha256(raw).equals(export.rawSha256())) {
            throw new IllegalStateException("export " + export.position() + " changed while it was being captured");
        }
        NormalizedReference subset;
        try {
            subset = export.reference().subset(pack);
        } catch (IllegalStateException incomplete) {
            return new PackReport(pack, Action.INCOMPLETE, null, null, incomplete.getMessage());
        }
        List<SiapsReferenceManifest> sameReference = registered.stream()
                .filter(manifest -> isRevisionOf(manifest, subset))
                .toList();
        SiapsReferenceManifest captured = ReferenceArtifactStore.manifestOf(
                raw, subset, metadataOf(referenceId(pack, subset, nextRevision(sameReference)), export, siblings));
        Optional<SiapsReferenceManifest> unchanged = sameReference.stream()
                .filter(known -> ReferenceDrift.compare(known, captured) == DriftStatus.SAME_REVISION)
                .findFirst();
        if (unchanged.isPresent()) {
            return unchanged(pack, unchanged.get(), captured, raw, subset, store);
        }
        SiapsReferenceManifest stored = store.store(raw, subset, metadataOf(captured));
        Files.createDirectories(manifestOutput);
        Files.writeString(
                manifestOutput.resolve(stored.referenceId() + JSON),
                stored.toJson(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        registered.add(stored);
        return new PackReport(
                pack,
                sameReference.isEmpty() ? Action.CAPTURED : Action.DRIFT,
                stored.referenceId(),
                stored.normalizedSha256(),
                "");
    }

    /**
     * A pack whose normalized contents the manifest directory has: unchanged only when the store holds
     * the artifact the registered manifest pins, raw bytes included. A missing artifact is put back
     * from the file the manifest pins and from no other download; a store that holds the contents
     * with another download's bytes is reported, never taken as unchanged.
     */
    private static PackReport unchanged(
            GatePack pack,
            SiapsReferenceManifest known,
            SiapsReferenceManifest captured,
            byte[] raw,
            NormalizedReference subset,
            ReferenceArtifactStore store)
            throws IOException {
        boolean missing = !Files.exists(store.directoryOf(known));
        if (missing && !known.rawSha256().equals(captured.rawSha256())) {
            return new PackReport(
                    pack,
                    Action.ARTIFACT_MISSING,
                    known.referenceId(),
                    known.normalizedSha256(),
                    "the store lacks the registered revision and this file has other raw bytes: restore the"
                            + " original file to put it back");
        }
        if (missing) {
            store.store(raw, subset, metadataOf(known));
        }
        try {
            store.load(known);
        } catch (IllegalStateException otherBytes) {
            return new PackReport(
                    pack,
                    Action.ARTIFACT_MISMATCH,
                    known.referenceId(),
                    known.normalizedSha256(),
                    "the store holds these contents with the raw bytes of another download than the one the"
                            + " manifest pins: put back the store of the capture that registered it");
        }
        return new PackReport(pack, Action.UNCHANGED, known.referenceId(), known.normalizedSha256(), "");
    }

    private String referenceId(GatePack pack, NormalizedReference subset, int revision) {
        return SiapsReferenceManifest.referenceId(
                uf, subset.municipalityIbge(), subset.quadrimestre(), pack, subset.sourceKind(), revision);
    }

    private CaptureMetadata metadataOf(String referenceId, Export export, List<String> siblings) {
        return new CaptureMetadata(
                referenceId,
                OffsetDateTime.now(clock),
                export.reference().officialGeneratedAt(),
                SOURCE_DESCRIPTION,
                siblings);
    }

    private static CaptureMetadata metadataOf(SiapsReferenceManifest manifest) {
        return new CaptureMetadata(
                manifest.referenceId(),
                manifest.capturedAt(),
                manifest.officialGeneratedAt(),
                manifest.sourceDescription(),
                manifest.siblingReferenceIds());
    }

    /** The same municipality, quadrimestre, source and pack: a revision of the reference {@code subset} is. */
    private static boolean isRevisionOf(SiapsReferenceManifest manifest, NormalizedReference subset) {
        return manifest.municipalityIbge().equals(subset.municipalityIbge())
                && manifest.quadrimestre().equals(subset.quadrimestre())
                && manifest.sourceKind() == subset.sourceKind()
                && manifest.indicatorCodes().equals(List.of(subset.indicatorCode()));
    }

    private static int nextRevision(List<SiapsReferenceManifest> revisions) {
        return revisions.stream()
                        .mapToInt(manifest -> revisionOf(manifest.referenceId()))
                        .max()
                        .orElse(0)
                + 1;
    }

    private static int revisionOf(String referenceId) {
        Matcher revision = REVISION.matcher(referenceId.substring(referenceId.lastIndexOf("-r")));
        if (!revision.matches()) {
            throw new IllegalStateException("not a reference id with a revision: " + referenceId);
        }
        return Integer.parseInt(revision.group(1));
    }

    // ---- the manifests already registered

    /**
     * The team manifests in the manifest directory, if it exists. A {@code .json} file there that is
     * not named like one, a manifest not named by its id, or a manifest of another municipality is
     * refused before anything is written: a renamed manifest would otherwise be a revision the
     * capture does not see, and the diagnostic refuses the directory for either, since it reads the
     * references of one municipality.
     */
    private List<SiapsReferenceManifest> registered(String municipalityIbge) throws IOException {
        List<SiapsReferenceManifest> manifests = new ArrayList<>();
        if (!Files.isDirectory(manifestOutput)) {
            return manifests;
        }
        List<Path> files;
        try (Stream<Path> listed = Files.list(manifestOutput)) {
            files = listed.filter(file -> file.getFileName().toString().endsWith(JSON))
                    .sorted()
                    .toList();
        }
        for (Path file : files) {
            if (!MANIFEST_FILE.matcher(file.getFileName().toString()).matches()) {
                throw new IllegalStateException(
                        "a .json file of the manifest directory is not named like a team reference manifest");
            }
        }
        for (Path file : files) {
            SiapsReferenceManifest manifest =
                    SiapsReferenceManifest.fromJson(Files.readString(file, StandardCharsets.UTF_8));
            if (!file.getFileName().toString().equals(manifest.referenceId() + JSON)) {
                throw new IllegalStateException(
                        "a manifest in the manifest directory is not named by its reference id");
            }
            if (!manifest.municipalityIbge().equals(municipalityIbge)) {
                throw new IllegalStateException("a manifest in the manifest directory is about another municipality:"
                        + " one manifest directory holds the references of one municipality");
            }
            manifests.add(manifest);
        }
        return manifests;
    }
}
