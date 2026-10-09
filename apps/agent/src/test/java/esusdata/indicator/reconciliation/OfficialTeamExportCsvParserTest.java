package esusdata.indicator.reconciliation;

import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.CONCEPT_COLUMN;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.EAP_1;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.ESF_1;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.ESF_2;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.INDICATOR_NAMES;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.MUNICIPALITY_IBGE;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.QUADRIMESTRE;
import static esusdata.indicator.reconciliation.SiapsTeamExportFixtures.TOTAL_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.OfficialTeamExportCsvParser.ExpectedScope;
import esusdata.indicator.reconciliation.OfficialTeamReference.Skipped;
import esusdata.indicator.reconciliation.SiapsTeamExportFixtures.Export;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The strict reader of the official SIAPS team export, on invented files in the exact layout of the
 * real ones ({@link SiapsTeamExportFixtures}). Every refusal is also checked not to name a value
 * the file holds: the parser runs on real files, and a message ends up in logs.
 */
class OfficialTeamExportCsvParserTest {

    private static final ExpectedScope SCOPE = new ExpectedScope(MUNICIPALITY_IBGE, QUADRIMESTRE);
    private static final GatePack C1 = GatePack.bySiapsCode(110).orElseThrow();
    private static final GatePack C2 = GatePack.bySiapsCode(108).orElseThrow();
    private static final String C1_NAME = INDICATOR_NAMES.get(0);
    private static final String C3_NAME = INDICATOR_NAMES.get(2);
    private static final String ESF = SiapsParser.ESF;
    private static final String EAP = SiapsParser.EAP;
    private static final String KNOWN_LAYOUT = "known layout";
    private static final String PERSON_LEVEL = "person-level column";

    /** Values of the invented file that a message must never carry. */
    private static final List<String> IDENTIFYING = List.of(
            ESF_1,
            ESF_2,
            EAP_1,
            SiapsTeamExportFixtures.ESB_1,
            SiapsTeamExportFixtures.EMULTI_1,
            "9000001",
            "8000001",
            "ESTABELECIMENTO TESTE",
            "EQUIPE TESTE",
            "MUNICIPIO TESTE");

    private static OfficialTeamReference parse(Export export) {
        return OfficialTeamExportCsvParser.parse(export.bytes(), SCOPE);
    }

    /** The message of the refusal; the file must be refused and the message must name nothing the file holds. */
    private static String refusal(String text, ExpectedScope scope) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Throwable thrown = catchThrowable(() -> OfficialTeamExportCsvParser.parse(bytes, scope));
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(thrown.getMessage())
                .startsWith("official team export refused")
                .doesNotContain(IDENTIFYING.toArray(new String[0]));
        return thrown.getMessage();
    }

    private static String refusal(Export export) {
        return refusal(export.text(), SCOPE);
    }

    @Test
    void readsTheLayoutOfARealExport() {
        OfficialTeamReference reference = parse(SiapsTeamExportFixtures.standard());

        assertThat(reference.municipalityIbge()).isEqualTo(MUNICIPALITY_IBGE);
        assertThat(reference.quadrimestre()).isEqualTo(QUADRIMESTRE);
        assertThat(reference.officialStatus()).isEqualTo(OfficialStatus.PRELIMINARY);
        assertThat(reference.officialGeneratedAt()).isEqualTo(LocalDateTime.of(2026, 10, 8, 17, 42));
        // the official universe is every eSF and eAP team of the file, with its type
        assertThat(reference.teamTypes()).isEqualTo(Map.of(ESF_1, ESF, ESF_2, ESF, EAP_1, EAP));
        assertThat(reference.universe(ESF)).containsExactly(ESF_1, ESF_2);
        assertThat(reference.universe(EAP)).containsExactly(EAP_1);
        assertThat(reference.officialUniverse(110, ESF)).containsExactly(ESF_1, ESF_2);
        assertThat(reference.officialUniverse(GatePack.NOTA_FINAL_CODE, EAP)).containsExactly(EAP_1);
        // the class of each team for an indicator and for the Nota Final
        assertThat(reference.classes(C1))
                .containsEntry(ESF_1, Classification.REGULAR)
                .containsEntry(ESF_2, Classification.OTIMO)
                .containsEntry(EAP_1, Classification.BOM);
        assertThat(reference.classes(GatePack.NOTA_FINAL))
                .containsEntry(ESF_1, Classification.BOM)
                .containsEntry(ESF_2, Classification.OTIMO);
        assertThat(GatePack.allWithNotaFinal()).allMatch(reference::isComplete);
        // eSB and eMulti are counted (a team, and its two indicator rows plus its Total row) and not read
        assertThat(reference.skipped()).isEqualTo(Map.of("eSB", new Skipped(1, 3), "eMulti", new Skipped(1, 3)));
    }

    @Test
    void readsTheFigureOfEachRowAndTheFinalNoteOfEachTeam() {
        NormalizedReference c1 = parse(SiapsTeamExportFixtures.standard()).subset(C1);
        NormalizedReference notaFinal =
                parse(SiapsTeamExportFixtures.standard()).subset(GatePack.NOTA_FINAL);

        assertThat(c1.rows().getFirst())
                .isEqualTo(new NormalizedReference.IndicatorRow(
                        ESF_1,
                        ESF,
                        new BigDecimal("12.5"),
                        Classification.REGULAR,
                        new BigDecimal("0.25"),
                        BigDecimal.ONE,
                        new BigDecimal("0.25")));
        // 0.25 * 1 + 0.5 * 2 + 0.75 * 2 + 1 * 1 + 0.75 * 1 + 0.5 * 1 + 0.25 * 2 = 5.5
        assertThat(notaFinal.rows().getFirst())
                .isEqualTo(new NormalizedReference.FinalRow(ESF_1, ESF, new BigDecimal("5.5"), Classification.BOM));
    }

    @Test
    void normalizesAnIneThatLostItsLeadingZeros() {
        OfficialTeamReference reference = parse(SiapsTeamExportFixtures.export()
                .team("11", ESF, Classification.BOM, SiapsTeamExportFixtures.all(Classification.BOM)));

        assertThat(reference.universe(ESF)).containsExactly(ESF_1);
    }

    @Test
    void toleratesAMissingByteOrderMarkAndBareLineFeeds() {
        String text = SiapsTeamExportFixtures.standard().text();
        String withoutMark = text.substring(1).replace("\r\n", "\n");

        OfficialTeamReference reference =
                OfficialTeamExportCsvParser.parse(withoutMark.getBytes(StandardCharsets.UTF_8), SCOPE);

        assertThat(reference.universe(ESF)).containsExactly(ESF_1, ESF_2);
    }

    @Test
    void readsAFileFromDisk(@TempDir Path directory) throws IOException {
        Path file = SiapsTeamExportFixtures.standard().write(directory, "export.csv");

        OfficialTeamReference reference =
                OfficialTeamExportCsvParser.parse(file, ExpectedScope.ofMunicipality(MUNICIPALITY_IBGE));

        // no expected quadrimestre: the file's own, still agreed on by the preamble and every row
        assertThat(reference.quadrimestre()).isEqualTo(QUADRIMESTRE);
    }

    @Test
    void theStatusLineIsOptionalAndWithoutItTheRevisionIsFinal() {
        OfficialTeamReference preliminary = parse(SiapsTeamExportFixtures.standard());
        OfficialTeamReference revised = parse(SiapsTeamExportFixtures.standard().statusLine(null));

        assertThat(preliminary.officialStatus()).isEqualTo(OfficialStatus.PRELIMINARY);
        assertThat(revised.officialStatus()).isEqualTo(OfficialStatus.FINAL);
        assertThat(revised.subset(C1).sha256())
                .isNotEqualTo(preliminary.subset(C1).sha256());
    }

    @Test
    void anyOtherTextInTheStatusSlotIsRefused() {
        String message = refusal(SiapsTeamExportFixtures.standard().statusLine("Dado Definitivo"));

        assertThat(message).contains("empty line after the revision status");
    }

    @Test
    void refusesAMunicipalityThatIsNotTheExpectedOneInThePreambleOrInAnyRow() {
        String inThePreamble = refusal(SiapsTeamExportFixtures.standard().municipality("123456"));
        Export inARow = SiapsTeamExportFixtures.standard();
        inARow.row(ESF_1, C1_NAME)[SiapsTeamExportFixtures.IBGE_COLUMN] = "123456";
        Export inAnOutOfScopeRow = SiapsTeamExportFixtures.standard();
        inAnOutOfScopeRow
                .row(SiapsTeamExportFixtures.ESB_1, "Escovação supervisionada")[SiapsTeamExportFixtures.IBGE_COLUMN] = "123456";

        assertThat(inThePreamble).contains("municipality 123456", "expected 999999");
        assertThat(refusal(inARow)).contains("table row 1", "COD IBGE");
        assertThat(refusal(inAnOutOfScopeRow)).contains("COD IBGE");
    }

    @Test
    void refusesAQuadrimestreThatIsNotTheExpectedOneOrThatTheRowsDoNotShare() {
        String text = SiapsTeamExportFixtures.standard().text();
        Export inARow = SiapsTeamExportFixtures.standard();
        inARow.row(EAP_1, C3_NAME)[SiapsTeamExportFixtures.QUADRIMESTRE_COLUMN] = "Q2/26";

        assertThat(refusal(text, new ExpectedScope(MUNICIPALITY_IBGE, "2026Q2")))
                .contains("2026Q1", "expected 2026Q2");
        assertThat(refusal(inARow)).contains("QUADRIMESTRE is not the quadrimestre of the preamble");
        assertThat(refusal(SiapsTeamExportFixtures.standard().competence("Q1/2026")))
                .contains("competence");
    }

    @Test
    void refusesAUfThatTheRowsDoNotShare() {
        Export export = SiapsTeamExportFixtures.standard();
        export.row(ESF_2, C1_NAME)[SiapsTeamExportFixtures.UF_COLUMN] = "YY";

        assertThat(refusal(export)).contains("UF is not the UF of the preamble");
    }

    @Test
    void refusesAHeaderThatIsNotTheKnownLayout() {
        String renamed = SiapsTeamExportFixtures.HEADER_LINE.replace(";Indicador;", ";Indicadores;");
        String shorter = SiapsTeamExportFixtures.HEADER_LINE.replace(";CLASSIFICAÇÃO FINAL", "");

        assertThat(refusal(SiapsTeamExportFixtures.standard().header(renamed))).contains(KNOWN_LAYOUT);
        assertThat(refusal(SiapsTeamExportFixtures.standard().header(shorter))).contains(KNOWN_LAYOUT);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "CPF",
                "CNS",
                "Nº do CNS",
                "Nome do cidadão",
                "Data de nascimento",
                "Telefone",
                "Endereço",
                "NOME DA EQUIPE E DO RESPONSÁVEL"
            })
    void refusesAPersonLevelExportByTheWholeWordOfAColumn(String column) {
        String header = SiapsTeamExportFixtures.HEADER_LINE + ";" + column;

        assertThat(refusal(SiapsTeamExportFixtures.standard().header(header))).contains(PERSON_LEVEL);
    }

    @Test
    void theNameOfTheTeamIsTheOnlyNameColumnAndItIsNotALoophole() {
        String renamedToAPerson = SiapsTeamExportFixtures.HEADER_LINE.replace("NOME DA EQUIPE", "NOME DO CIDADÃO");

        // accepted: the exact column of the real layout (the standard export has it, with its trailing TAB)
        assertThat(parse(SiapsTeamExportFixtures.standard()).teamCount()).isEqualTo(3);
        assertThat(refusal(SiapsTeamExportFixtures.standard().header(renamedToAPerson)))
                .contains(PERSON_LEVEL);
    }

    @Test
    void aWordThatOnlyContainsAPersonalWordIsNotPersonLevelButStillNotTheLayout() {
        String header = SiapsTeamExportFixtures.HEADER_LINE + ";CNSOLE;ENDERECOS";

        assertThat(refusal(SiapsTeamExportFixtures.standard().header(header)))
                .contains(KNOWN_LAYOUT)
                .doesNotContain(PERSON_LEVEL);
    }

    @Test
    void refusesARepeatedTeamAndIndicatorAndARepeatedTotalRow() {
        String indicator = refusal(SiapsTeamExportFixtures.standard().duplicateRow(ESF_1, C3_NAME));
        String total = refusal(SiapsTeamExportFixtures.standard().duplicateRow(EAP_1, TOTAL_NAME));

        assertThat(indicator).contains("a second row for one team and indicator");
        assertThat(total).contains("a second Total row for one team");
    }

    @Test
    void refusesATeamTypeItDoesNotKnowAndATeamWithTwoTypes() {
        Export unknown = SiapsTeamExportFixtures.standard()
                .team("0000000016", "eXX", Classification.BOM, SiapsTeamExportFixtures.all(Classification.BOM));
        Export twoTypes = SiapsTeamExportFixtures.standard();
        twoTypes.row(ESF_1, C3_NAME)[SiapsTeamExportFixtures.TYPE_COLUMN] = EAP;

        assertThat(refusal(unknown)).contains("SIGLA DA EQUIPE is not a team type");
        assertThat(refusal(twoTypes)).contains("a team with two types");
    }

    @Test
    void refusesAnIndicatorThatIsNotOneOfEsfAndEap() {
        Export export = SiapsTeamExportFixtures.standard();
        export.row(ESF_1, C3_NAME)[SiapsTeamExportFixtures.INDICATOR_COLUMN] = "Escovação supervisionada";

        assertThat(refusal(export)).contains("INDICADOR is not an indicator of eSF and eAP");
    }

    @Test
    void refusesAConceptItDoesNotKnow() {
        Export export = SiapsTeamExportFixtures.standard();
        export.row(ESF_1, C3_NAME)[CONCEPT_COLUMN] = "EXCELENTE";

        assertThat(refusal(export)).contains("CONCEITO OBTIDO DO INDICADOR NO QUADRIMESTRE is not a class");
    }

    @Test
    void refusesAFactorThatDisagreesWithItsConceptAndANoteThatIsNotFactorTimesWeight() {
        Export factor = SiapsTeamExportFixtures.standard();
        factor.row(ESF_1, C3_NAME)[SiapsTeamExportFixtures.FACTOR_COLUMN] = "1";
        Export note = SiapsTeamExportFixtures.standard();
        note.row(ESF_1, C3_NAME)[SiapsTeamExportFixtures.NOTE_COLUMN] = "1";

        assertThat(refusal(factor)).contains("does not agree with the concept");
        assertThat(refusal(note)).contains("NOTA DO INDICADOR is not the factor times the weight");
    }

    @Test
    void refusesNegativeAndNonNumericFigures() {
        Export negative = SiapsTeamExportFixtures.standard();
        negative.row(ESF_1, C1_NAME)[SiapsTeamExportFixtures.RESULT_COLUMN] = "-5";
        Export comma = SiapsTeamExportFixtures.standard();
        comma.row(ESF_1, C1_NAME)[SiapsTeamExportFixtures.RESULT_COLUMN] = "21,21";
        Export weight = SiapsTeamExportFixtures.standard();
        weight.row(ESF_1, C1_NAME)[SiapsTeamExportFixtures.WEIGHT_COLUMN] = "dois";

        assertThat(refusal(negative)).contains("is negative");
        assertThat(refusal(comma)).contains("RESULTADO DO QUADRIMESTRE MEDIA DOS MESES is not a number");
        assertThat(refusal(weight)).contains("PESO DO INDICADOR is not a number");
    }

    @Test
    void refusesATotalRowWithoutAFinalNoteOrClass() {
        Export noNote = SiapsTeamExportFixtures.standard();
        noNote.row(ESF_1, TOTAL_NAME)[SiapsTeamExportFixtures.FINAL_NOTE_COLUMN] = " - ";
        Export noClass = SiapsTeamExportFixtures.standard();
        noClass.row(ESF_1, TOTAL_NAME)[SiapsTeamExportFixtures.FINAL_CLASS_COLUMN] = "-";
        Export wrongClass = SiapsTeamExportFixtures.standard();
        wrongClass.row(ESF_1, TOTAL_NAME)[SiapsTeamExportFixtures.FINAL_CLASS_COLUMN] = "OTIMA";

        assertThat(refusal(noNote)).contains("a Total row without a final note or class");
        assertThat(refusal(noClass)).contains("a Total row without a final note or class");
        assertThat(refusal(wrongClass)).contains("CLASSIFICACAO FINAL is not a class");
    }

    @Test
    void refusesAFinalClassThatIsNotTheBandOfTheFinalNote() {
        // ESF_2 has seven Ótimo indicators: final note 10, so Quadro 6 says Ótimo
        Export below = SiapsTeamExportFixtures.standard();
        below.row(ESF_2, TOTAL_NAME)[SiapsTeamExportFixtures.FINAL_CLASS_COLUMN] = "REGULAR";
        // ESF_1 has a final note of 5.5, Bom; Ótimo needs more than 7.5
        Export above = SiapsTeamExportFixtures.standard();
        above.row(ESF_1, TOTAL_NAME)[SiapsTeamExportFixtures.FINAL_CLASS_COLUMN] = "ÓTIMO";

        assertThat(refusal(below)).contains("is neither the band of Quadro 6");
        assertThat(refusal(above)).contains("is neither the band of Quadro 6");
    }

    @Test
    void aBomOffTheBandOfTheFinalNoteIsTheClassANewTeamGets() {
        Export newTeam = SiapsTeamExportFixtures.standard();
        newTeam.row(ESF_2, TOTAL_NAME)[SiapsTeamExportFixtures.FINAL_CLASS_COLUMN] = "BOM";

        assertThat(parse(newTeam).classes(GatePack.NOTA_FINAL)).containsEntry(ESF_2, Classification.BOM);
    }

    @Test
    void refusesAnIndicatorRowThatCarriesAFinalNoteOrATotalRowThatCarriesAFigure() {
        Export indicator = SiapsTeamExportFixtures.standard();
        indicator.row(ESF_1, C1_NAME)[SiapsTeamExportFixtures.FINAL_NOTE_COLUMN] = "5";
        Export total = SiapsTeamExportFixtures.standard();
        total.row(ESF_1, TOTAL_NAME)[SiapsTeamExportFixtures.RESULT_COLUMN] = "10";

        assertThat(refusal(indicator)).contains("NOTA FINAL DA EQUIPE must be a dash");
        assertThat(refusal(total)).contains("RESULTADO DO QUADRIMESTRE MEDIA DOS MESES must be a dash");
    }

    @Test
    void refusesAFileThatDoesNotSayWhenItWasGenerated() {
        String unreadable = refusal(SiapsTeamExportFixtures.standard().generatedAt("ontem"));
        String missing = refusal(
                SiapsTeamExportFixtures.standard()
                        .text()
                        .replace("Dado gerado em: " + SiapsTeamExportFixtures.GENERATED_AT, ""),
                SCOPE);
        String impossible = refusal(SiapsTeamExportFixtures.standard().generatedAt("31 de fevereiro de 2026 - 17:42h"));

        assertThat(unreadable).contains("does not say when it was generated");
        assertThat(missing).contains("when the file was generated");
        assertThat(impossible).contains("does not say when it was generated");
    }

    @Test
    void refusesAPreambleThatIsNotTheKnownOne() {
        String text = SiapsTeamExportFixtures.standard().text();
        String extraFilter = text.replace("Filtro:\r\n", "Filtro:\r\nEquipe: qualquer\r\n");
        String extraAtTheEnd = text.replace(
                "\r\n" + SiapsTeamExportFixtures.HEADER_LINE,
                "\r\nOutra linha\r\n" + SiapsTeamExportFixtures.HEADER_LINE);
        String otherReport = text.replace("Avaliação do quadrimestre\r\n", "Outro relatório\r\n");

        assertThat(refusal(extraFilter, SCOPE)).contains("the competence");
        assertThat(refusal(extraAtTheEnd, SCOPE)).contains("more lines than the known layout");
        assertThat(refusal(otherReport, SCOPE)).contains("the report name");
    }

    @Test
    void refusesAnExportCutShortOrWithSomethingAfterItsTable() {
        String complete = SiapsTeamExportFixtures.standard().text();
        String withoutFooter = refusal(SiapsTeamExportFixtures.standard().withoutFooter());
        String cutMidRow = refusal(complete.substring(0, complete.lastIndexOf("\"Total") + 30), SCOPE);
        String extra = refusal(complete + "\r\nQualquer coisa", SCOPE);

        assertThat(withoutFooter).contains("no source line at its end");
        assertThat(cutMidRow).contains("something other than the source line");
        assertThat(extra).contains("something other than the source line");
    }

    @Test
    void refusesBytesThatAreNotUtf8() {
        byte[] latin1 = SiapsTeamExportFixtures.standard().text().getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> OfficialTeamExportCsvParser.parse(latin1, SCOPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid UTF-8");
    }

    @Test
    void refusesAnExportWithoutAnyEsfOrEapTeam() {
        Export onlyOthers = SiapsTeamExportFixtures.export()
                .outOfScopeTeam(SiapsTeamExportFixtures.ESB_1, "eSB")
                .outOfScopeTeam(SiapsTeamExportFixtures.EMULTI_1, "eMulti");

        assertThat(refusal(onlyOthers)).contains("no eSF or eAP team");
    }

    @Test
    void doesNotReadTheRowsOfEsbAndEmultiOnlyCountsThem() {
        Export export = SiapsTeamExportFixtures.standard();
        export.row(SiapsTeamExportFixtures.ESB_1, "Escovação supervisionada")[CONCEPT_COLUMN] = "???";
        export.row(SiapsTeamExportFixtures.EMULTI_1, TOTAL_NAME)[SiapsTeamExportFixtures.FINAL_NOTE_COLUMN] = "n/a";

        OfficialTeamReference reference = parse(export);

        assertThat(reference.skipped()).containsOnlyKeys("eSB", "eMulti");
        assertThat(reference.teamCount()).isEqualTo(3);
    }

    @Test
    void aMissingRowMakesTheIndicatorIncompleteAndIsNeverFilledWithAZero() {
        OfficialTeamReference reference =
                parse(SiapsTeamExportFixtures.standard().dropRow(ESF_2, C1_NAME));

        assertThat(reference.isComplete(C1)).isFalse();
        assertThat(reference.missingRows(C1)).isEqualTo(1);
        assertThat(reference.officialUniverse(110, ESF)).containsExactly(ESF_1);
        assertThat(reference.universe(ESF)).containsExactly(ESF_1, ESF_2);
        assertThat(reference.classes(C1)).doesNotContainKey(ESF_2);
        assertThat(reference.isComplete(C2)).isTrue();
        assertThat(reference.subset(C2).rows()).hasSize(3);
        assertThatThrownBy(() -> reference.subset(C1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete for C1");
    }

    @Test
    void aMissingTotalRowMakesTheNotaFinalIncomplete() {
        OfficialTeamReference reference =
                parse(SiapsTeamExportFixtures.standard().dropRow(EAP_1, TOTAL_NAME));

        assertThat(reference.isComplete(GatePack.NOTA_FINAL)).isFalse();
        assertThat(reference.officialUniverse(GatePack.NOTA_FINAL_CODE, EAP)).isEmpty();
        assertThat(reference.isComplete(C1)).isTrue();
        assertThatThrownBy(() -> reference.subset(GatePack.NOTA_FINAL)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTeamWithOnlyItsTotalRowStillBelongsToTheUniverseAndLacksEveryIndicator() {
        OfficialTeamReference reference =
                parse(SiapsTeamExportFixtures.standard().team("0000000020", ESF, Classification.BOM));

        assertThat(reference.teamCount()).isEqualTo(4);
        assertThat(GatePack.all()).noneMatch(reference::isComplete);
        assertThat(reference.isComplete(GatePack.NOTA_FINAL)).isTrue();
    }

    @Test
    void aRedownloadWithAnotherTimestampAndOtherNamesKeepsTheNormalizedHashes() {
        Export first = SiapsTeamExportFixtures.standard();
        Export second = SiapsTeamExportFixtures.export()
                .generatedAt("08 de outubro de 2026 - 18:05h")
                .renamed()
                .standardTeams();

        OfficialTeamReference one = parse(first);
        OfficialTeamReference another = parse(second);

        assertThat(second.text()).isNotEqualTo(first.text());
        assertThat(another.officialGeneratedAt()).isNotEqualTo(one.officialGeneratedAt());
        for (GatePack pack : GatePack.allWithNotaFinal()) {
            assertThat(another.subset(pack).sha256())
                    .as(pack.code())
                    .isEqualTo(one.subset(pack).sha256());
        }
    }

    @Test
    void changingOneClassChangesOnlyTheHashOfItsPack() {
        OfficialTeamReference before = parse(SiapsTeamExportFixtures.standard());
        Export changed = SiapsTeamExportFixtures.standard();
        changed.row(ESF_1, C3_NAME)[CONCEPT_COLUMN] = "ÓTIMO";
        changed.row(ESF_1, C3_NAME)[SiapsTeamExportFixtures.FACTOR_COLUMN] = "1";
        changed.row(ESF_1, C3_NAME)[SiapsTeamExportFixtures.NOTE_COLUMN] = "2";

        OfficialTeamReference after = parse(changed);

        GatePack c3 = GatePack.bySiapsCode(107).orElseThrow();
        assertThat(after.subset(c3).sha256()).isNotEqualTo(before.subset(c3).sha256());
        assertThat(after.subset(C1).sha256()).isEqualTo(before.subset(C1).sha256());
        assertThat(after.subset(GatePack.NOTA_FINAL).sha256())
                .isEqualTo(before.subset(GatePack.NOTA_FINAL).sha256());
    }

    @Test
    void theSubsetIsWhatTheArtifactStoreCapturesAndLoadsBack(@TempDir Path directory) throws IOException {
        Export export = SiapsTeamExportFixtures.standard();
        OfficialTeamReference reference = parse(export);
        NormalizedReference subset = reference.subset(C1);
        CaptureMetadata metadata = new CaptureMetadata(
                SiapsReferenceManifest.referenceId(
                        "zz", MUNICIPALITY_IBGE, QUADRIMESTRE, C1, SourceKind.OFFICIAL_TEAM_EXPORT_CSV, 1),
                OffsetDateTime.of(2026, 10, 8, 12, 34, 56, 0, ZoneOffset.ofHours(-3)),
                reference.officialGeneratedAt(),
                "SIAPS / Avaliação do Quadrimestre / Qualidade",
                "export.csv");
        ReferenceArtifactStore store = new ReferenceArtifactStore(directory);

        SiapsReferenceManifest manifest = store.store(export.bytes(), subset, metadata);

        assertThat(subset.sourceKind()).isEqualTo(SourceKind.OFFICIAL_TEAM_EXPORT_CSV);
        assertThat(subset.parserVersion()).isEqualTo(OfficialTeamExportCsvParser.PARSER_VERSION);
        assertThat(manifest.rowCount()).isEqualTo(3);
        assertThat(manifest.teamTypes()).containsExactly(EAP, ESF);
        assertThat(manifest.officialStatus()).isEqualTo(OfficialStatus.PRELIMINARY);
        assertThat(store.load(manifest)).isEqualTo(subset);
    }
}
