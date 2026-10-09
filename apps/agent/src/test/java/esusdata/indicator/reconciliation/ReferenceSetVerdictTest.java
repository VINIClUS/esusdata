package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.pack.c4.C4Pack;
import esusdata.indicator.reconciliation.Comparison.RowResult;
import esusdata.indicator.reconciliation.PackVerdict.Status;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * The verdict of a pre-registered set under {@code ALL_REQUIRED}: invalidators come first and make a
 * reference pending whatever its figures say, and once a reference is fit nothing softens its
 * verdict. Both directions are tested.
 */
class ReferenceSetVerdictTest {

    private static final PackDescriptor DESCRIPTOR = new C4Pack().descriptor();
    private static final GatePack PACK = GatePack.byPackId(DESCRIPTOR.id()).orElseThrow();
    private static final String RULE = DESCRIPTOR.ruleVersion();
    private static final String FIRST = "zz-9999990-2026q1-c4-team-r1";
    private static final String SECOND = "zz-9999990-2026q1-c4-team-r2";
    private static final String DIAGNOSTIC = "zz-9999990-2025q3-c4-team-r1";
    private static final String MANIFEST_SHA = "a".repeat(64);
    private static final String DOSSIER_SHA = "b".repeat(64);
    private static final String FINGERPRINT = "sha256:" + "c".repeat(64);
    private static final String OTHER_FINGERPRINT = "sha256:" + "d".repeat(64);
    private static final String QUADRIMESTRE = "2026Q1";
    private static final String NO_GATE = "sem referência GATE ativa e obrigatória";

    private static ReferenceDeclaration gate(String id, ReferenceCompatibility compatibility) {
        return new ReferenceDeclaration(
                id,
                QUADRIMESTRE,
                "9999990",
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                ReferencePurpose.GATE,
                true,
                ReferenceStatus.ACTIVE,
                compatibility,
                MANIFEST_SHA,
                ReferencePolicy.dossierPath(id, DESCRIPTOR.id()),
                DOSSIER_SHA,
                null);
    }

    private static ReferenceDeclaration diagnostic() {
        return new ReferenceDeclaration(
                DIAGNOSTIC,
                "2025Q3",
                "9999990",
                SourceKind.OFFICIAL_TEAM_EXPORT_CSV,
                ReferencePurpose.DIAGNOSTIC,
                false,
                ReferenceStatus.ACTIVE,
                ReferenceCompatibility.INCOMPATIBLE,
                MANIFEST_SHA,
                ReferencePolicy.dossierPath(DIAGNOSTIC, DESCRIPTOR.id()),
                DOSSIER_SHA,
                null);
    }

    private static ReferenceSet set(ReferenceDeclaration... declarations) {
        return new ReferenceSet(
                DESCRIPTOR.id(),
                RULE,
                ReferencePolicy.CHECK_DISTRIBUTION,
                SelectionPolicy.ALL_REQUIRED,
                List.of(declarations));
    }

    /** The committed dossier that stands for the declaration. */
    private static DossierEvidence dossier(ReferenceDeclaration declaration) {
        return dossier(declaration, FINGERPRINT);
    }

    private static DossierEvidence dossier(ReferenceDeclaration declaration, String fingerprint) {
        return new DossierEvidence(
                declaration.referenceId(),
                RULE,
                MANIFEST_SHA,
                declaration.compatibility(),
                fingerprint,
                declaration.compatibilityEvidenceRef(),
                DOSSIER_SHA,
                new ObjectMapper().createObjectNode().put("teams_compared", "<10"));
    }

    private static List<RowResult> rows(boolean agree) {
        ClassCounts official = new ClassCounts(10, 0, 0, 0);
        ClassCounts local = agree ? official : new ClassCounts(0, 0, 0, 10);
        return List.of(
                Comparison.row(SiapsParser.ESF, official, local, 0),
                Comparison.row(SiapsParser.EAP, official, local, 0));
    }

    private static PackVerdict local(Status status) {
        return local(status, ReferencePurpose.GATE, FINGERPRINT);
    }

    private static PackVerdict local(Status status, ReferencePurpose purpose, String fingerprint) {
        return new PackVerdict(
                PACK,
                RULE,
                purpose,
                status,
                status == Status.PENDING ? "referência incompleta" : "",
                status == Status.PENDING ? null : QUADRIMESTRE,
                status == Status.PENDING ? List.of() : rows(status == Status.PASSED),
                0,
                List.of(),
                fingerprint);
    }

    private static Map<String, PackVerdict> verdicts(Object... idsAndVerdicts) {
        Map<String, PackVerdict> map = new LinkedHashMap<>();
        for (int i = 0; i < idsAndVerdicts.length; i += 2) {
            map.put((String) idsAndVerdicts[i], (PackVerdict) idsAndVerdicts[i + 1]);
        }
        return map;
    }

    private static Map<String, DossierEvidence> dossiers(ReferenceDeclaration... declarations) {
        Map<String, DossierEvidence> map = new LinkedHashMap<>();
        for (ReferenceDeclaration declaration : declarations) {
            map.put(declaration.referenceId(), dossier(declaration));
        }
        return map;
    }

    // ---- the set rule

    @Test
    void aSetWithoutAGateReferenceIsPendingAndDiagnosticsAreIgnored() {
        ReferenceSetVerdict empty = ReferenceSetVerdict.aggregate(set(), Map.of(), Map.of());
        ReferenceDeclaration diagnostic = diagnostic();
        ReferenceSetVerdict onlyDiagnostic = ReferenceSetVerdict.aggregate(
                set(diagnostic), verdicts(DIAGNOSTIC, local(Status.PASSED)), Map.of(DIAGNOSTIC, dossier(diagnostic)));

        for (ReferenceSetVerdict verdict : List.of(empty, onlyDiagnostic)) {
            assertThat(verdict.status()).isEqualTo(Status.PENDING);
            assertThat(verdict.reason()).isEqualTo(NO_GATE);
            assertThat(verdict.references()).isEmpty();
            assertThat(verdict.check()).isEqualTo("siaps-distribuicao-por-classe@2");
            assertThat(verdict.gateSetSha256()).matches("[0-9a-f]{64}");
        }
    }

    @Test
    void aDiagnosticReferenceNeverChangesTheVerdictOfTheGateReferences() {
        ReferenceDeclaration first = gate(FIRST, ReferenceCompatibility.EXACT);
        Map<String, PackVerdict> passed = verdicts(FIRST, local(Status.PASSED), DIAGNOSTIC, local(Status.FAILED));

        ReferenceSetVerdict verdict = ReferenceSetVerdict.aggregate(set(first, diagnostic()), passed, dossiers(first));

        assertThat(verdict.status()).isEqualTo(Status.PASSED);
        assertThat(verdict.references()).extracting("referenceId").containsExactly(FIRST);
        assertThat(verdict.gateSetSha256())
                .as("a diagnostic declaration does not move the hash D cites")
                .isEqualTo(set(first).gateSetSha256());
    }

    @Test
    void allRequiredEveryReferenceMustPassOneFailureFailsAndAPendingOneKeepsThePending() {
        ReferenceDeclaration first = gate(FIRST, ReferenceCompatibility.EXACT);
        ReferenceDeclaration second = gate(SECOND, ReferenceCompatibility.EQUIVALENT_FOR_REFERENCE);
        ReferenceSet both = set(first, second);

        assertThat(aggregate(both, Status.PASSED, Status.PASSED).status()).isEqualTo(Status.PASSED);
        assertThat(aggregate(both, Status.PASSED, Status.PASSED).reason()).isEmpty();
        assertThat(aggregate(both, Status.PASSED, Status.FAILED).status()).isEqualTo(Status.FAILED);
        assertThat(aggregate(both, Status.PASSED, Status.FAILED).reason()).contains(SECOND);
        assertThat(aggregate(both, Status.PASSED, Status.PENDING).status()).isEqualTo(Status.PENDING);
        assertThat(aggregate(both, Status.PASSED, Status.PENDING).reason()).contains(SECOND);
        assertThat(aggregate(both, Status.FAILED, Status.PENDING).status())
                .as("a failure is not softened by a reference that is pending")
                .isEqualTo(Status.FAILED);
    }

    private static ReferenceSetVerdict aggregate(ReferenceSet set, Status first, Status second) {
        return ReferenceSetVerdict.aggregate(
                set,
                verdicts(FIRST, local(first), SECOND, local(second)),
                dossiers(set.references().toArray(ReferenceDeclaration[]::new)));
    }

    // ---- the invalidators come first

    @Test
    void aFailedVerdictOnAFitReferenceIsNeverSoftenedWhateverTheCompatibility() {
        for (ReferenceCompatibility compatibility :
                List.of(ReferenceCompatibility.EXACT, ReferenceCompatibility.EQUIVALENT_FOR_REFERENCE)) {
            ReferenceDeclaration declaration = gate(FIRST, compatibility);

            ReferenceSetVerdict verdict = ReferenceSetVerdict.aggregate(
                    set(declaration), verdicts(FIRST, local(Status.FAILED)), dossiers(declaration));

            assertThat(verdict.status()).as(compatibility.name()).isEqualTo(Status.FAILED);
            assertThat(verdict.references().getFirst().rows()).hasSize(2);
        }
    }

    @Test
    void aFailedVerdictFromAnotherLocalSourceThanTheDossierWasDecidedOnIsPendingAndDoesNotFailTheSet() {
        ReferenceDeclaration declaration = gate(FIRST, ReferenceCompatibility.EXACT);

        ReferenceSetVerdict verdict = ReferenceSetVerdict.aggregate(
                set(declaration),
                verdicts(FIRST, local(Status.FAILED, ReferencePurpose.GATE, OTHER_FINGERPRINT)),
                dossiers(declaration));

        assertThat(verdict.status()).isEqualTo(Status.PENDING);
        assertThat(verdict.references().getFirst().status()).isEqualTo(Status.PENDING);
        assertThat(verdict.references().getFirst().reason()).contains("not the one the dossier was decided on");
        assertThat(verdict.references().getFirst().rows()).isEmpty();
    }

    @Test
    void aPassedVerdictFromAnotherLocalSourceIsPendingToo() {
        ReferenceDeclaration declaration = gate(FIRST, ReferenceCompatibility.EXACT);

        ReferenceSetVerdict verdict = ReferenceSetVerdict.aggregate(
                set(declaration),
                verdicts(FIRST, local(Status.PASSED, ReferencePurpose.GATE, OTHER_FINGERPRINT)),
                dossiers(declaration));

        assertThat(verdict.status()).isEqualTo(Status.PENDING);
    }

    @Test
    void aFailedVerdictWithoutAValidDossierIsPendingAndDoesNotFailTheSet() {
        ReferenceDeclaration declaration = gate(FIRST, ReferenceCompatibility.EXACT);
        PackVerdict failed = local(Status.FAILED);

        assertThat(only(declaration, failed, Map.of()).references().getFirst().reason())
                .contains("there is no dossier");
        assertThat(only(
                                declaration,
                                failed,
                                Map.of(
                                        FIRST,
                                        dossierWith(
                                                declaration,
                                                "other-reference",
                                                RULE,
                                                MANIFEST_SHA,
                                                DOSSIER_SHA,
                                                declaration.compatibility())))
                        .references()
                        .getFirst()
                        .reason())
                .contains("another reference");
        assertThat(only(
                                declaration,
                                failed,
                                Map.of(
                                        FIRST,
                                        dossierWith(
                                                declaration,
                                                FIRST,
                                                "x@1",
                                                MANIFEST_SHA,
                                                DOSSIER_SHA,
                                                declaration.compatibility())))
                        .references()
                        .getFirst()
                        .reason())
                .contains("another rule version");
        assertThat(only(
                                declaration,
                                failed,
                                Map.of(
                                        FIRST,
                                        dossierWith(
                                                declaration,
                                                FIRST,
                                                RULE,
                                                "e".repeat(64),
                                                DOSSIER_SHA,
                                                declaration.compatibility())))
                        .references()
                        .getFirst()
                        .reason())
                .contains("another manifest");
        assertThat(only(
                                declaration,
                                failed,
                                Map.of(
                                        FIRST,
                                        dossierWith(
                                                declaration,
                                                FIRST,
                                                RULE,
                                                MANIFEST_SHA,
                                                "e".repeat(64),
                                                declaration.compatibility())))
                        .references()
                        .getFirst()
                        .reason())
                .contains("not the one the declaration pins");
        assertThat(only(
                                declaration,
                                failed,
                                Map.of(
                                        FIRST,
                                        dossierWith(
                                                declaration,
                                                FIRST,
                                                RULE,
                                                MANIFEST_SHA,
                                                DOSSIER_SHA,
                                                ReferenceCompatibility.EQUIVALENT_FOR_REFERENCE)))
                        .references()
                        .getFirst()
                        .reason())
                .contains("not the declared compatibility");
        ReferenceSetVerdict inconclusive = only(
                declaration,
                failed,
                Map.of(
                        FIRST,
                        dossierWith(
                                declaration,
                                FIRST,
                                RULE,
                                MANIFEST_SHA,
                                DOSSIER_SHA,
                                ReferenceCompatibility.INCONCLUSIVE)));
        assertThat(inconclusive.references().getFirst().reason()).contains("does not authorize the gate");
        assertThat(inconclusive.status()).isEqualTo(Status.PENDING);
    }

    private static DossierEvidence dossierWith(
            ReferenceDeclaration declaration,
            String referenceId,
            String ruleVersion,
            String manifestSha,
            String sha,
            ReferenceCompatibility verdict) {
        return new DossierEvidence(
                referenceId,
                ruleVersion,
                manifestSha,
                verdict,
                FINGERPRINT,
                declaration.compatibilityEvidenceRef(),
                sha,
                new ObjectMapper().createObjectNode());
    }

    private static ReferenceSetVerdict only(
            ReferenceDeclaration declaration, PackVerdict verdict, Map<String, DossierEvidence> dossiers) {
        return ReferenceSetVerdict.aggregate(set(declaration), verdicts(FIRST, verdict), dossiers);
    }

    @Test
    void aLocalVerdictThatIsMissingDiagnosticOrOfAnotherPackRuleOrQuadrimestreIsPending() {
        ReferenceDeclaration declaration = gate(FIRST, ReferenceCompatibility.EXACT);
        Map<String, DossierEvidence> dossiers = dossiers(declaration);

        assertThat(ReferenceSetVerdict.aggregate(set(declaration), Map.of(), dossiers)
                        .references()
                        .getFirst()
                        .reason())
                .contains("no local verdict");
        assertThat(only(declaration, local(Status.PASSED, ReferencePurpose.DIAGNOSTIC, FINGERPRINT), dossiers)
                        .references()
                        .getFirst()
                        .reason())
                .contains("not a gate one");
        PackVerdict otherPack = new PackVerdict(
                GatePack.all().getFirst(),
                RULE,
                ReferencePurpose.GATE,
                Status.PASSED,
                "",
                QUADRIMESTRE,
                rows(true),
                0,
                List.of(),
                FINGERPRINT);
        assertThat(only(declaration, otherPack, dossiers)
                        .references()
                        .getFirst()
                        .reason())
                .contains("another pack");
        PackVerdict otherRule = new PackVerdict(
                PACK,
                "x@1",
                ReferencePurpose.GATE,
                Status.PASSED,
                "",
                QUADRIMESTRE,
                rows(true),
                0,
                List.of(),
                FINGERPRINT);
        assertThat(only(declaration, otherRule, dossiers)
                        .references()
                        .getFirst()
                        .reason())
                .contains("another rule version");
        PackVerdict otherPeriod = new PackVerdict(
                PACK, RULE, ReferencePurpose.GATE, Status.PASSED, "", "2025Q3", rows(true), 0, List.of(), FINGERPRINT);
        assertThat(only(declaration, otherPeriod, dossiers)
                        .references()
                        .getFirst()
                        .reason())
                .contains("another quadrimestre");
        for (PackVerdict invalid : List.of(otherPack, otherRule, otherPeriod)) {
            assertThat(only(declaration, invalid, dossiers).status()).isEqualTo(Status.PENDING);
        }
    }

    @Test
    void aVerdictThatComparedNothingKeepsItsOwnPendingReason() {
        ReferenceDeclaration declaration = gate(FIRST, ReferenceCompatibility.EXACT);

        ReferenceSetVerdict verdict = only(declaration, local(Status.PENDING), dossiers(declaration));

        assertThat(verdict.status()).isEqualTo(Status.PENDING);
        assertThat(verdict.references().getFirst().reason()).isEqualTo("referência incompleta");
    }

    @Test
    void theOutcomeCarriesThePinsTheFiguresAndTheDossiersFieldComparison() {
        ReferenceDeclaration declaration = gate(FIRST, ReferenceCompatibility.EXACT);

        ReferenceSetVerdict.ReferenceOutcome outcome = only(declaration, local(Status.PASSED), dossiers(declaration))
                .references()
                .getFirst();

        assertThat(outcome.referenceManifestSha256()).isEqualTo(MANIFEST_SHA);
        assertThat(outcome.dossierRef()).isEqualTo(ReferencePolicy.dossierPath(FIRST, DESCRIPTOR.id()));
        assertThat(outcome.dossierSha256()).isEqualTo(DOSSIER_SHA);
        assertThat(outcome.compatibility()).isEqualTo(ReferenceCompatibility.EXACT);
        assertThat(outcome.localSourceFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(outcome.rows()).extracting(RowResult::teamType).containsExactly(SiapsParser.ESF, SiapsParser.EAP);
        assertThat(outcome.officialFieldComparison().path("teams_compared").asString())
                .isEqualTo("<10");
    }

    // ---- superseded and retracted never get here

    @Test
    void aSupersededOrRetractedGateDeclarationIsRefusedByThePolicyBeforeAnyVerdict(@TempDir Path directory)
            throws Exception {
        for (ReferenceStatus status : List.of(ReferenceStatus.SUPERSEDED, ReferenceStatus.RETRACTED)) {
            PortaoDEvidenceFixtures.Tree tree =
                    PortaoDEvidenceFixtures.decided(directory.resolve(status.name()), DESCRIPTOR, Status.PASSED);
            ReferenceDeclaration declaration = tree.loadedPolicy()
                    .referenceSet(DESCRIPTOR.id(), RULE)
                    .references()
                    .getFirst();
            assertThat(declaration.violations()).isEmpty();

            PortaoDEvidenceFixtures.replaceIn(tree.policy(), "\"status\":\"ACTIVE\"", "\"status\":\"" + status + "\"");

            assertThatThrownBy(tree::loadedPolicy)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must be ACTIVE");
        }
    }
}
