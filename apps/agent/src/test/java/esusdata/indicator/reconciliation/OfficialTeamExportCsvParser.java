package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Bands;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.pack.c1.C1Rule;
import esusdata.indicator.pack.componente3.ComponentIII;
import esusdata.indicator.pack.componente3.Nt08Tables;
import esusdata.indicator.reconciliation.NormalizedReference.FinalRow;
import esusdata.indicator.reconciliation.NormalizedReference.IndicatorRow;
import esusdata.indicator.reconciliation.OfficialTeamReference.Skipped;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the official SIAPS team export, "Avaliação do quadrimestre" of the Componente de Qualidade
 * (docs/superpowers/specs/2026-10-08-reconciliacao-siaps-retrospectiva-design.md, §9.3 and §11),
 * the file a manager downloads by hand. It supports exactly the layout {@value #PARSER_VERSION}
 * and refuses anything else: a guessed column would compare the wrong numbers, and this file is
 * the one that can decide the Portão D.
 *
 * <p>The layout: UTF-8 (a byte order mark is tolerated), {@code ;}, every cell quoted and padded
 * with spaces and TABs; a fixed preamble (titles, "Dado gerado em", the report name, an optional
 * "Dado Preliminar" line, the UF, the municipality as six digits and a name, the quadrimestre as
 * {@code Q1/26}); a 17-column header; one row per team and indicator, plus one {@code Total} row
 * per team with its final note and class; and a closing "Fonte" line, without which the file is
 * taken as truncated. The municipality and the quadrimestre are read from the preamble and the
 * rows, never from the file name, and must be the ones the caller expects.
 *
 * <p>The file is refused when it is a person-level export (a column named CPF, CNS, nome,
 * nascimento, telefone or endereço, apart from the team's own name), when its municipality or
 * quadrimestre is not the expected one anywhere, when it repeats a team and indicator, when a team
 * type, an indicator, a class or a number is not one it knows, when an indicator's concept is not
 * the band its ficha gives the result, when a factor disagrees with its class, when a weight is not the one the NT 8/2026 gives the indicator, or when the final note of
 * a team with all seven indicator rows is not the sum of their notes. Teams of the types outside the Componente de Qualidade of eSF/eAP (eSB, eMulti) are
 * counted and left unread. A team that lacks a row is not refused: the reference reports it as
 * incomplete ({@link OfficialTeamReference#isComplete}).
 *
 * <p>No message names a value of the file (an INE, a CNES, an establishment, a team or a
 * municipality name): it names the table row and the column, so a refusal can be shown and logged.
 */
final class OfficialTeamExportCsvParser {

    /** The layout this parser reads; a new layout is a new version, never an edit of this one. */
    static final String PARSER_VERSION = "siaps-team-export@1";

    private static final char BOM = '﻿';
    private static final int COLUMNS = 17;

    private static final int QUADRIMESTRE = 0;
    private static final int UF = 1;
    private static final int IBGE = 2;
    private static final int MUNICIPALITY_NAME = 3;
    private static final int INE = 6;
    private static final int TYPE = 8;
    private static final int INDICATOR = 9;
    private static final int RESULT = 10;
    private static final int CONCEPT = 11;
    private static final int FACTOR = 12;
    private static final int WEIGHT = 13;
    private static final int NOTE = 14;
    private static final int FINAL_NOTE = 15;
    private static final int FINAL_CLASS = 16;
    private static final List<Integer> TOTAL_DASHES_AT_START = List.of(UF, IBGE, MUNICIPALITY_NAME);
    private static final List<Integer> TOTAL_DASHES = List.of(RESULT, CONCEPT, FACTOR, WEIGHT, NOTE);

    /** The SIAPS shows a result with at most two decimals, and drops their trailing zeros. */
    private static final int SHOWN_DECIMALS = 2;

    private static final String DASH = "-";
    private static final String TOTAL = "Total";
    private static final String NOTA_FINAL = "NOTA FINAL E CLASSIFICACAO FINAL";
    private static final String NAME_OF_TEAM = "NOME DA EQUIPE";

    /** The header, normalized as {@link SiapsCsv#normalize} does. */
    private static final List<String> HEADER = List.of(
            "QUADRIMESTRE",
            "UF",
            "COD IBGE",
            "MUNICIPIO",
            "CNES",
            "ESTABELECIMENTO",
            "INE",
            NAME_OF_TEAM,
            "SIGLA DA EQUIPE",
            "INDICADOR",
            "RESULTADO DO QUADRIMESTRE MEDIA DOS MESES",
            "CONCEITO OBTIDO DO INDICADOR NO QUADRIMESTRE",
            "CONCEITO OBTIDO DO INDICADOR NO QUADRIMESTRE - VARIAVEL NUMERICA",
            "PESO DO INDICADOR",
            "NOTA DO INDICADOR",
            "NOTA FINAL DA EQUIPE",
            "CLASSIFICACAO FINAL");

    /** A column with one of these words (whole words, accents and case aside) is about a person. */
    private static final Set<String> PERSON_WORDS = Set.of("CPF", "CNS", "NOME", "NASCIMENTO", "TELEFONE", "ENDERECO");

    /** The SIAPS names of the indicators of eSF and eAP, normalized, with their SIAPS codes. */
    private static final Map<String, Integer> INDICATORS = Map.of(
            "MAIS ACESSO A APS", 110,
            "CUIDADO NO DESENVOLVIMENTO INFANTIL", 108,
            "CUIDADO NA GESTACAO E PUERPERIO", 107,
            "CUIDADO DA PESSOA COM DIABETES", 105,
            "CUIDADO DA PESSOA COM HIPERTENSAO", 104,
            "CUIDADO DA PESSOA IDOSA", 106,
            "CUIDADO DA MULHER NA PREVENCAO DO CANCER", 109);

    /** The team types outside the Componente de Qualidade of eSF/eAP: counted, never read. */
    private static final Set<String> OUT_OF_SCOPE = Set.of("eSB", "eMulti");

    private static final List<String> TITLES = List.of(
            "Ministério da Saúde - MS",
            "Secretaria de Atenção Primária à Saúde - Saps",
            "Sistema de Informação para a Atenção Primária à Saúde – Siaps");
    private static final String GENERATED_AT = "Dado gerado em: ";
    private static final String REPORT = "Avaliação do quadrimestre";
    private static final String PRELIMINARY = "Dado Preliminar";
    private static final String DEMOGRAPHICS = "Dados sociodemográficos:";
    private static final String UF_PREFIX = "UF: ";
    private static final String MUNICIPALITY_PREFIX = "Município: ";
    private static final String FILTER = "Filtro:";
    private static final String COMPETENCE_PREFIX = "Competência selecionada: ";
    private static final String VIEW = "Visualizacao: Avaliação do Quadrimestre";
    private static final String FOOTER = "Fonte: Sistema de Informação para a Atenção Primária à Saúde - SIAPS";

    private static final Pattern GENERATED_AT_TEXT =
            Pattern.compile("(\\d{1,2}) de (\\p{L}+) de (\\d{4}) - (\\d{1,2}):(\\d{2})h");
    private static final Pattern UF_TEXT = Pattern.compile("[A-Z]{2}");
    private static final Pattern MUNICIPALITY_TEXT = Pattern.compile("(\\d{6}) / .+");
    private static final Pattern COMPETENCE_TEXT = Pattern.compile("Q[1-3]/\\d{2}");
    private static final Pattern INE_TEXT = Pattern.compile("\\d{1,10}");
    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");
    private static final Pattern NEGATIVE_NUMBER = Pattern.compile("-\\d+(\\.\\d+)?");
    private static final Pattern NON_WORD = Pattern.compile("[^A-Z0-9]+");

    /** The months of the "Dado gerado em" line, normalized (no accent, upper case). */
    private static final List<String> MONTHS = List.of(
            "JANEIRO",
            "FEVEREIRO",
            "MARCO",
            "ABRIL",
            "MAIO",
            "JUNHO",
            "JULHO",
            "AGOSTO",
            "SETEMBRO",
            "OUTUBRO",
            "NOVEMBRO",
            "DEZEMBRO");

    /** What the file must be about, known from the PEC and never from the file. */
    record ExpectedScope(String municipalityIbge, String quadrimestre) {

        /**
         * @param municipalityIbge the 7-digit IBGE code ({@code PEC_MUNICIPALITY_IBGE}); the file
         *     says its first six digits
         * @param quadrimestre the SIAPS spelling ({@code 2026Q1}) or {@code null} to take the one
         *     the file says, which the rows must still agree with
         */
        ExpectedScope {
            ReferenceFormats.ibge7(municipalityIbge);
            if (quadrimestre != null) {
                ReferenceFormats.quadrimestre(quadrimestre);
            }
        }

        /** Any quadrimestre: the file's own. */
        static ExpectedScope ofMunicipality(String municipalityIbge) {
            return new ExpectedScope(municipalityIbge, null);
        }
    }

    private OfficialTeamExportCsvParser() {}

    /**
     * Reads the file and returns what it says.
     *
     * @throws IllegalArgumentException when the file is not exactly the layout, or is not about the
     *     expected municipality and quadrimestre
     */
    static OfficialTeamReference parse(Path file, ExpectedScope scope) throws IOException {
        return parse(Files.readAllBytes(file), scope);
    }

    /** As {@link #parse(Path, ExpectedScope)}, over the bytes of the file (the ones to hash and store). */
    static OfficialTeamReference parse(byte[] raw, ExpectedScope scope) {
        List<List<String>> records = SiapsCsv.records(withoutBom(decode(raw)));
        int header = headerAt(records);
        Preamble preamble = Preamble.read(records.subList(0, header));
        preamble.requireScope(scope);
        requireHeader(records.get(header));
        int end = tableEnd(records, header + 1);
        requireFooter(records.subList(end, records.size()));
        Collector table = new Collector(preamble);
        for (List<String> record : records.subList(header + 1, end)) {
            table.accept(record);
        }
        return table.reference(scope.municipalityIbge());
    }

    private static String decode(byte[] raw) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(refusal("the export is not valid UTF-8"), e);
        }
    }

    private static String withoutBom(String text) {
        return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
    }

    private static String refusal(String what) {
        return "official team export refused: " + what;
    }

    /** The preamble is one cell per line; the first line with more is the header. */
    private static int headerAt(List<List<String>> records) {
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).size() != 1) {
                return i;
            }
        }
        throw new IllegalArgumentException(refusal("the export has no header"));
    }

    /** The person guard first, so a person-level export is called that, then the exact columns. */
    private static void requireHeader(List<String> cells) {
        List<String> names = cells.stream().map(SiapsCsv::normalize).toList();
        for (String name : names) {
            if (!NAME_OF_TEAM.equals(name) && isAboutAPerson(name)) {
                throw new IllegalArgumentException(refusal("the export has a person-level column: " + name));
            }
        }
        if (!HEADER.equals(names)) {
            throw new IllegalArgumentException(refusal("the header is not the known layout " + PARSER_VERSION));
        }
    }

    private static boolean isAboutAPerson(String name) {
        return NON_WORD.splitAsStream(name).anyMatch(PERSON_WORDS::contains);
    }

    private static int tableEnd(List<List<String>> records, int first) {
        int end = first;
        while (end < records.size() && records.get(end).size() == COLUMNS) {
            end++;
        }
        return end;
    }

    /** After the table: empty lines and the source line, nothing else; without it the file is cut short. */
    private static void requireFooter(List<List<String>> tail) {
        List<String> lines = tail.stream()
                .map(record -> normalized(String.join(";", record)))
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    refusal("the export has no source line at its end; it may be cut short"));
        }
        if (!lines.equals(List.of(FOOTER))) {
            throw new IllegalArgumentException(
                    refusal("the export has something other than the source line after its table"));
        }
    }

    private static String normalized(String line) {
        return Normalizer.normalize(line, Normalizer.Form.NFC).strip();
    }

    /** What the lines before the header say; each one is exactly the known text or the export is refused. */
    private record Preamble(
            LocalDateTime generatedAt, OfficialStatus status, String uf, String municipality, String quadrimestre) {

        static Preamble read(List<List<String>> records) {
            LineCursor lines = new LineCursor(records.stream()
                    .map(record -> normalized(record.getFirst()))
                    .toList());
            for (String title : TITLES) {
                lines.expect(title, "the SIAPS titles");
            }
            LocalDateTime generatedAt = generatedAt(lines.value(GENERATED_AT, "when the file was generated"));
            lines.expect(REPORT, "the report name");
            OfficialStatus status = lines.skipIf(PRELIMINARY) ? OfficialStatus.PRELIMINARY : OfficialStatus.FINAL;
            lines.expect("", "an empty line after the revision status (Dado Preliminar or none)");
            lines.expect(DEMOGRAPHICS, "the sociodemographic data heading");
            String uf = lines.value(UF_PREFIX, "the UF");
            String municipality = lines.value(MUNICIPALITY_PREFIX, "the municipality");
            lines.expect("", "an empty line after the municipality");
            lines.expect(FILTER, "the filter heading");
            String competence = lines.value(COMPETENCE_PREFIX, "the competence");
            lines.expect(VIEW, "the view");
            lines.expect("", "an empty line before the header");
            lines.requireEnd();
            return new Preamble(
                    generatedAt,
                    status,
                    require(UF_TEXT, uf, "the UF"),
                    municipalityCode(municipality),
                    quadrimestre(competence));
        }

        void requireScope(ExpectedScope scope) {
            String expected = SiapsFormats.ibgeOfSiaps(scope.municipalityIbge());
            if (!expected.equals(municipality)) {
                throw new IllegalArgumentException(
                        refusal("the export is about municipality " + municipality + ", not the expected " + expected));
            }
            if (scope.quadrimestre() != null && !scope.quadrimestre().equals(quadrimestre)) {
                throw new IllegalArgumentException(
                        refusal("the export is about " + quadrimestre + ", not the expected " + scope.quadrimestre()));
            }
        }

        private static String require(Pattern pattern, String value, String what) {
            if (!pattern.matcher(value).matches()) {
                throw new IllegalArgumentException(
                        refusal("the preamble does not give " + what + " in the known form"));
            }
            return value;
        }

        private static String municipalityCode(String line) {
            Matcher matcher = MUNICIPALITY_TEXT.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        refusal("the preamble does not give the municipality in the known form"));
            }
            return matcher.group(1);
        }

        private static String quadrimestre(String competence) {
            require(COMPETENCE_TEXT, competence, "the competence");
            return SiapsFormats.quadrimestre(SiapsFormats.quadrimestre(competence));
        }

        private static LocalDateTime generatedAt(String text) {
            Matcher matcher = GENERATED_AT_TEXT.matcher(text);
            int month = matcher.matches() ? MONTHS.indexOf(SiapsCsv.normalize(matcher.group(2))) + 1 : 0;
            if (month == 0) {
                throw new IllegalArgumentException(refusal("the export does not say when it was generated"));
            }
            try {
                return LocalDateTime.of(
                        Integer.parseInt(matcher.group(3)),
                        month,
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(4)),
                        Integer.parseInt(matcher.group(5)));
            } catch (DateTimeException e) {
                throw new IllegalArgumentException(refusal("the export does not say when it was generated"), e);
            }
        }
    }

    /** The preamble lines, taken in order, one expectation at a time. */
    private static final class LineCursor {

        private final List<String> lines;
        private int at;

        LineCursor(List<String> lines) {
            this.lines = lines;
        }

        private String next(String what) {
            if (at >= lines.size()) {
                throw new IllegalArgumentException(refusal("the preamble ends before " + what));
            }
            return lines.get(at++);
        }

        void expect(String exact, String what) {
            if (!exact.equals(next(what))) {
                throw new IllegalArgumentException(refusal("the preamble does not have " + what));
            }
        }

        /** The text after {@code prefix} on the next line. */
        String value(String prefix, String what) {
            String line = next(what);
            if (!line.startsWith(prefix)) {
                throw new IllegalArgumentException(refusal("the preamble does not have " + what));
            }
            return line.substring(prefix.length()).strip();
        }

        /** Takes the next line when it is {@code exact}. */
        boolean skipIf(String exact) {
            if (at < lines.size() && exact.equals(lines.get(at))) {
                at++;
                return true;
            }
            return false;
        }

        void requireEnd() {
            if (at != lines.size()) {
                throw new IllegalArgumentException(refusal("the preamble has more lines than the known layout"));
            }
        }
    }

    /** The table, row by row: checks each row and gathers what the reference holds. */
    private static final class Collector {

        private final Preamble preamble;
        private final Map<String, String> typeByIne = new TreeMap<>();
        private final Map<Integer, Map<String, IndicatorRow>> indicators = new TreeMap<>();
        private final Map<String, FinalRow> totals = new TreeMap<>();
        private final Map<String, Integer> totalRowNumbers = new TreeMap<>();
        private final Map<String, Set<String>> skippedTeams = new TreeMap<>();
        private final Map<String, Integer> skippedRows = new TreeMap<>();
        private int row;

        Collector(Preamble preamble) {
            this.preamble = preamble;
        }

        void accept(List<String> record) {
            row++;
            List<String> cells = record.stream().map(String::strip).toList();
            String type = cells.get(TYPE);
            if (!SiapsParser.ESF.equals(type) && !SiapsParser.EAP.equals(type) && !OUT_OF_SCOPE.contains(type)) {
                throw fail(HEADER.get(TYPE) + " is not a team type the export has");
            }
            boolean total = TOTAL.equals(cells.get(QUADRIMESTRE));
            if (!total) {
                requireScope(cells);
            }
            String ine = ine(cells.get(INE));
            requireOneTypePerTeam(ine, type);
            if (OUT_OF_SCOPE.contains(type)) {
                skippedTeams.computeIfAbsent(type, key -> new TreeSet<>()).add(ine);
                skippedRows.merge(type, 1, Integer::sum);
            } else if (total) {
                addTotal(ine, type, cells);
            } else {
                addIndicator(ine, type, cells);
            }
        }

        private IllegalArgumentException fail(String what) {
            return new IllegalArgumentException(refusal("table row " + row + ": " + what));
        }

        /** The quadrimestre, UF and municipality of a team row are the preamble's, which are the expected ones. */
        private void requireScope(List<String> cells) {
            String quadrimestre = cells.get(QUADRIMESTRE);
            if (!COMPETENCE_TEXT.matcher(quadrimestre).matches()
                    || !preamble.quadrimestre()
                            .equals(SiapsFormats.quadrimestre(SiapsFormats.quadrimestre(quadrimestre)))) {
                throw fail(HEADER.get(QUADRIMESTRE) + " is not the quadrimestre of the preamble");
            }
            if (!preamble.uf().equals(cells.get(UF))) {
                throw fail(HEADER.get(UF) + " is not the UF of the preamble");
            }
            if (!preamble.municipality().equals(cells.get(IBGE))) {
                throw fail(HEADER.get(IBGE) + " is not the municipality of the preamble");
            }
        }

        private String ine(String cell) {
            if (!INE_TEXT.matcher(cell).matches()) {
                throw fail(HEADER.get(INE) + " is not an INE");
            }
            return SiapsFormats.ine(cell);
        }

        private void requireOneTypePerTeam(String ine, String type) {
            String known = typeByIne.putIfAbsent(ine, type);
            if (known != null && !known.equals(type)) {
                throw fail("a team with two types");
            }
        }

        private void addIndicator(String ine, String type, List<String> cells) {
            requireDash(cells, FINAL_NOTE);
            requireDash(cells, FINAL_CLASS);
            int code = indicatorCode(cells.get(INDICATOR));
            BigDecimal result = decimal(cells, RESULT);
            Classification concept = classification(cells.get(CONCEPT), HEADER.get(CONCEPT));
            if (!conceptsOf(code, result).contains(concept)) {
                throw fail(HEADER.get(CONCEPT) + " is not the band the indicator's ficha gives the result");
            }
            BigDecimal factor = decimal(cells, FACTOR);
            if (factor.compareTo(factorOf(concept)) != 0) {
                throw fail(HEADER.get(FACTOR) + " does not agree with the concept");
            }
            BigDecimal weight = decimal(cells, WEIGHT);
            if (weight.compareTo(prescribedWeight(code)) != 0) {
                throw fail(HEADER.get(WEIGHT) + " is not the weight the NT 8/2026 gives the indicator");
            }
            BigDecimal note = decimal(cells, NOTE);
            if (note.compareTo(factor.multiply(weight)) != 0) {
                throw fail(HEADER.get(NOTE) + " is not the factor times the weight");
            }
            IndicatorRow indicatorRow = new IndicatorRow(ine, type, result, concept, factor, weight, note);
            if (indicators.computeIfAbsent(code, key -> new TreeMap<>()).put(ine, indicatorRow) != null) {
                throw fail("a second row for one team and indicator");
            }
        }

        private void addTotal(String ine, String type, List<String> cells) {
            for (int column : TOTAL_DASHES_AT_START) {
                requireDash(cells, column);
            }
            for (int column : TOTAL_DASHES) {
                requireDash(cells, column);
            }
            if (!NOTA_FINAL.equals(SiapsCsv.normalize(cells.get(INDICATOR)))) {
                throw fail("a Total row that is not the final note and class");
            }
            if (DASH.equals(cells.get(FINAL_NOTE)) || DASH.equals(cells.get(FINAL_CLASS))) {
                throw fail("a Total row without a final note or class");
            }
            BigDecimal finalNote = decimal(cells, FINAL_NOTE);
            Classification finalClass = classification(cells.get(FINAL_CLASS), HEADER.get(FINAL_CLASS));
            if (finalClass != Nt08Tables.classifyFinalScore(exact(finalNote)) && finalClass != Classification.BOM) {
                throw fail(HEADER.get(FINAL_CLASS) + " is neither the band of Quadro 6 for " + HEADER.get(FINAL_NOTE)
                        + " nor the Bom a new team gets (NT 8/2026, item 2.6)");
            }
            FinalRow finalRow = new FinalRow(ine, type, finalNote, finalClass);
            if (totals.put(ine, finalRow) != null) {
                throw fail("a second Total row for one team");
            }
            totalRowNumbers.put(ine, row);
        }

        /**
         * The final note of a team that has a row for each of the seven indicators is the sum of
         * their notes, each already the factor times the weight: a Total row that contradicts the
         * rows of its own team is refused, never compared. A team that lacks a row is left to the
         * completeness of the reference.
         */
        private void requireFinalNotesAreTheSumOfTheIndicatorNotes() {
            for (Map.Entry<String, FinalRow> total : totals.entrySet()) {
                Optional<BigDecimal> sum = sumOfTheIndicatorNotes(total.getKey());
                if (sum.isPresent() && sum.get().compareTo(total.getValue().finalNote()) != 0) {
                    throw new IllegalArgumentException(refusal("table row " + totalRowNumbers.get(total.getKey()) + ": "
                            + HEADER.get(FINAL_NOTE) + " is not the sum of the " + HEADER.get(NOTE)
                            + " of the seven indicators of the team"));
                }
            }
        }

        /** The sum of the notes of the team's seven indicators; empty when it lacks the row of one. */
        private Optional<BigDecimal> sumOfTheIndicatorNotes(String ine) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int code : Set.copyOf(INDICATORS.values())) {
                IndicatorRow indicator = indicators.getOrDefault(code, Map.of()).get(ine);
                if (indicator == null) {
                    return Optional.empty();
                }
                sum = sum.add(indicator.note());
            }
            return Optional.of(sum);
        }

        /** The weight of the indicator in Quadro 2 of the NT 8/2026: the one the local Nota Final uses. */
        private static BigDecimal prescribedWeight(int code) {
            String packId = GatePack.bySiapsCode(code).orElseThrow().packId();
            return ComponentIII.DESCRIPTOR.components().stream()
                    .filter(component -> component.code().equals(packId))
                    .map(component -> new BigDecimal(component.weight()))
                    .findFirst()
                    .orElseThrow();
        }

        /**
         * The concepts the ficha's bands give a result the file shows rounded to two decimals: its band, and on the edge of a band the band on the other side as well, since the value
         * the SIAPS classified may lie just across it. C1 has bands of its own ({@link C1Rule#classify},
         * where above 70 is Regular); C2 to C7 share theirs. Above 100, C2 to C7 have no band.
         */
        private static Set<Classification> conceptsOf(int code, BigDecimal result) {
            BigDecimal half = BigDecimal.valueOf(5, Math.max(SHOWN_DECIMALS, result.scale()) + 1);
            boolean c1 = C1Rule.INDICATOR_PACK.equals(
                    GatePack.bySiapsCode(code).orElseThrow().packId());
            Set<Classification> concepts = EnumSet.noneOf(Classification.class);
            for (BigDecimal value : List.of(result.subtract(half).max(BigDecimal.ZERO), result.add(half))) {
                ExactRatio exact = exact(value);
                if (c1) {
                    concepts.add(C1Rule.classify(exact));
                } else {
                    Bands.QUALIDADE_C2_C7.classify(exact).ifPresent(concepts::add);
                }
            }
            return concepts;
        }

        /** A decimal of the file as an exact fraction, for the bands that are never rounded first. */
        private static ExactRatio exact(BigDecimal value) {
            return value.scale() > 0
                    ? ExactRatio.of(value.unscaledValue(), BigInteger.TEN.pow(value.scale()))
                    : ExactRatio.of(value.toBigIntegerExact(), BigInteger.ONE);
        }

        private void requireDash(List<String> cells, int column) {
            if (!DASH.equals(cells.get(column))) {
                throw fail(HEADER.get(column) + " must be a dash on this row");
            }
        }

        private int indicatorCode(String cell) {
            Integer code = INDICATORS.get(SiapsCsv.normalize(cell));
            if (code == null) {
                throw fail(HEADER.get(INDICATOR) + " is not an indicator of eSF and eAP");
            }
            return code;
        }

        private BigDecimal decimal(List<String> cells, int column) {
            String cell = cells.get(column);
            if (NEGATIVE_NUMBER.matcher(cell).matches()) {
                throw fail(HEADER.get(column) + " is negative");
            }
            if (!NUMBER.matcher(cell).matches()) {
                throw fail(HEADER.get(column) + " is not a number");
            }
            return new BigDecimal(cell);
        }

        private Classification classification(String cell, String column) {
            return switch (SiapsCsv.normalize(cell)) {
                case "REGULAR" -> Classification.REGULAR;
                case "SUFICIENTE" -> Classification.SUFICIENTE;
                case "BOM" -> Classification.BOM;
                case "OTIMO" -> Classification.OTIMO;
                default -> throw fail(column + " is not a class");
            };
        }

        /** The factor of a concept in the NT 8/2026: 0,25, 0,50, 0,75 and 1,00. */
        private static BigDecimal factorOf(Classification concept) {
            return switch (concept) {
                case REGULAR -> new BigDecimal("0.25");
                case SUFICIENTE -> new BigDecimal("0.5");
                case BOM -> new BigDecimal("0.75");
                case OTIMO -> BigDecimal.ONE;
            };
        }

        OfficialTeamReference reference(String municipalityIbge) {
            requireFinalNotesAreTheSumOfTheIndicatorNotes();
            Map<String, String> inScope = new TreeMap<>(typeByIne);
            inScope.values().removeIf(OUT_OF_SCOPE::contains);
            if (inScope.isEmpty()) {
                throw new IllegalArgumentException(refusal("the export holds no eSF or eAP team"));
            }
            Map<String, Skipped> skipped = new TreeMap<>();
            skippedTeams.forEach((type, ines) -> skipped.put(type, new Skipped(ines.size(), skippedRows.get(type))));
            return new OfficialTeamReference(
                    municipalityIbge,
                    preamble.quadrimestre(),
                    preamble.status(),
                    preamble.generatedAt(),
                    inScope,
                    indicators,
                    totals,
                    skipped);
        }
    }
}
