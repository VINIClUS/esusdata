package esusdata.source.pec;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Capabilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The frozen queries of the foundation capabilities (ADR 0030), read without a database: one
 * typed positional bind per descriptor bind and nothing that the execution plane's literal {@code ?}
 * to {@code $n} rewrite could miscount, no personal data, every table schema-qualified and nothing
 * newer than PostgreSQL 9.6.13 or volatile.
 */
class CapabilityQueriesStaticTest {

    private static final Pattern TYPED_PLACEHOLDER = Pattern.compile("CAST\\(\\?\\s+AS\\s+(text\\[\\]|text|date)\\)");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[a-z_][a-z0-9_]*\\b");
    private static final Pattern TABLE = Pattern.compile("(\\w++\\.)?\\b(tb_\\w++)");
    private static final Map<String, String> BIND_SQL_TYPE = Map.of(
            "MUNICIPALITY_IBGE", "text",
            "PERIOD_START", "date",
            "PERIOD_END_EXCLUSIVE", "date",
            "DATE", "date",
            "TEXT_ARRAY", "text[]");

    /**
     * Columns that hold a person's name, document, contact or address, or the household pseudonyms,
     * in the DW dictionary (docs/discovery/2026-10-02-dw-dicionario-c2-c7.md, "[PII — nunca projetar]").
     * A frozen query never reads them, not even in a filter.
     */
    private static final List<String> PERSONAL_DATA = List.of(
            "no_cidadao",
            "no_social_cidadao",
            "no_nome",
            "no_nome_social",
            "no_nome_mae",
            "no_nome_pai",
            "no_mae",
            "no_responsavel",
            "no_email",
            "nu_nis",
            "nu_dnv_cidadao",
            "nu_obito_do",
            "nu_prontuario",
            "nu_celular",
            "nu_fone_residencial",
            "nu_latitude",
            "nu_longitude",
            "co_identificacao",
            "co_uuid_origem_fcd",
            "co_cds_domicilio",
            "nu_portaria_naturalizacao",
            "no_maternidade_referencia",
            "no_causa_internacao12",
            "no_plantas_medicinais",
            "no_outra_condicao1",
            "no_outra_condicao2",
            "no_outra_condicao3",
            "no_acompanhado_instituicao",
            "no_visita_familiar_parentesco",
            "ds_outra_localidade",
            "no_profissional",
            "tb_dim_profissional");

    /** Prefixes of whole families of personal columns (CPF, CNS, telephone, address, micro-area). */
    private static final List<String> PERSONAL_DATA_PREFIXES = List.of(
            "nu_cpf",
            "nu_cns",
            "nu_participante_cns",
            "nu_telefone",
            "ds_logradouro",
            "no_tipo_logradouro",
            "nu_numero",
            "st_sem_numero",
            "ds_complemento",
            "no_bairro",
            "no_municipio_",
            "sg_uf_",
            "ds_cep",
            "nu_micro_area");

    /** Syntax newer than PostgreSQL 9.6, or functions whose result changes inside a transaction. */
    private static final List<String> FORBIDDEN_WORDS = List.of(
            "materialized",
            "generated",
            "trim_scale",
            "now",
            "random",
            "clock_timestamp",
            "statement_timestamp",
            "current_date",
            "current_timestamp",
            "localtime",
            "localtimestamp",
            "timeofday",
            "nextval",
            "setval",
            "txid_current");

    static Stream<String> foundation() {
        return Capabilities.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void everyPlaceholderIsOneTypedBindOfTheDescriptorInOrder(String capability) {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);
        String code = CapabilitySql.codeOnly(contract.queryText());
        List<String> types = new ArrayList<>();
        Matcher placeholder = TYPED_PLACEHOLDER.matcher(code);
        while (placeholder.find()) {
            types.add(placeholder.group(1));
        }

        assertThat(types)
                .as(contract.capability() + ": CAST(? AS <type>) in the order of the descriptor's binds")
                .containsExactlyElementsOf(contract.binds().stream()
                        .map(bind -> BIND_SQL_TYPE.get(bind.type()))
                        .toList());
        assertThat(count(contract.queryText(), '?'))
                .as(contract.capability() + ": every ? of the file is a typed bind")
                .isEqualTo(types.size())
                .isEqualTo(contract.binds().size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void noPlaceholderHidesInACommentOrLiteralNorInAnOperator(String capability) {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);
        String code = CapabilitySql.codeOnly(contract.queryText());

        assertThat(count(code, '?'))
                .as(contract.capability() + ": the execution plane rewrites every ? literally")
                .isEqualTo(count(contract.queryText(), '?'));
        assertThat(code).doesNotContain("?|", "?&", "??");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void noQueryReadsPersonalDataNorTheBirthMunicipality(String capability) {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);
        List<String> identifiers = identifiers(contract.queryText());

        assertThat(identifiers).as(contract.capability()).doesNotContainAnyElementsOf(PERSONAL_DATA);
        assertThat(identifiers)
                .as(contract.capability())
                .noneMatch(identifier -> PERSONAL_DATA_PREFIXES.stream().anyMatch(identifier::startsWith));
        assertThat(identifiers)
                .as(contract.capability() + ": co_dim_municipio_cidadao is the birth municipality (achado 1)")
                .doesNotContain("co_dim_municipio_cidadao");
        assertThat(contract.columnNames())
                .as(contract.capability() + ": projected columns")
                .doesNotContainAnyElementsOf(PERSONAL_DATA);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void everyTableIsSchemaQualified(String capability) {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);
        Matcher table = TABLE.matcher(CapabilitySql.codeOnly(contract.queryText()));
        List<String> unqualified = new ArrayList<>();
        while (table.find()) {
            if (!"public.".equals(table.group(1))) {
                unqualified.add(table.group(2));
            }
        }

        assertThat(unqualified).as(contract.capability()).isEmpty();
        assertThat(CapabilitySql.tablesRead(contract.queryText()))
                .as(contract.capability())
                .isNotEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void queriesUseNothingNewerThanPostgresql96NorVolatile(String capability) {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);
        assertThat(identifiers(contract.queryText()))
                .as(contract.capability())
                .doesNotContainAnyElementsOf(FORBIDDEN_WORDS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foundation")
    void everyColumnIsReadThroughAnAliasThatNamesOneTable(String capability) {
        CapabilityContract contract = CapabilityCatalog.packaged().require(capability);
        assertThat(CapabilitySql.columnsRead(contract.queryText()).keySet())
                .as(contract.capability() + ": every table read has its columns found through its alias")
                .containsExactlyElementsOf(new TreeSet<>(CapabilitySql.tablesRead(contract.queryText())));
    }

    private static List<String> identifiers(String sql) {
        Matcher identifier = IDENTIFIER.matcher(CapabilitySql.codeOnly(sql).toLowerCase(Locale.ROOT));
        List<String> found = new ArrayList<>();
        while (identifier.find()) {
            found.add(identifier.group());
        }
        return found;
    }

    private static long count(String text, char wanted) {
        return text.chars().filter(character -> character == wanted).count();
    }
}
