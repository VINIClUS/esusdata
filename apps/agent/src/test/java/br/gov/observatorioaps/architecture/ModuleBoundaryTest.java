package br.gov.observatorioaps.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Tech Spec §1.5: "indicator-engine não depende de JDBC, HikariCP, HTTP ou leitura direta do PEC;
 * não executa SQL de usuário." ENG-35: "Teste de arquitetura impede JDBC/SQL na avaliação
 * metodológica; o motor funciona com fixture canônica sem banco PEC."
 *
 * <p>This is the one place these rules are enforced as code — not as a comment, not as a code
 * review convention.
 */
class ModuleBoundaryTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.gov.observatorioaps");

    @Test
    void indicatorEngineDoesNotDependOnJdbcOrSql() {
        noClasses()
                .that().resideInAPackage("br.gov.observatorioaps.indicatorengine..")
                .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..")
                .check(CLASSES);
    }

    @Test
    void indicatorEngineDoesNotDependOnHikariOrSpringJdbc() {
        noClasses()
                .that().resideInAPackage("br.gov.observatorioaps.indicatorengine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.zaxxer.hikari..", "org.springframework.jdbc..")
                .check(CLASSES);
    }

    @Test
    void indicatorEngineDoesNotDependOnPecAdapterOrSourceConnector() {
        noClasses()
                .that().resideInAPackage("br.gov.observatorioaps.indicatorengine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "br.gov.observatorioaps.pecadapter..",
                        "br.gov.observatorioaps.sourceconnector..")
                .check(CLASSES);
    }

    @Test
    void indicatorEngineDoesNotDependOnHttpClientOrSpringWeb() {
        noClasses()
                .that().resideInAPackage("br.gov.observatorioaps.indicatorengine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.net.http..", "org.springframework.web..")
                .check(CLASSES);
    }

    @Test
    void indicatorPacksDoNotDependOnJdbcSqlOrPecAdapter() {
        ArchRuleDefinition.noClasses()
                .that().resideInAPackage("br.gov.observatorioaps.indicatorpacks..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.sql..", "javax.sql..",
                        "com.zaxxer.hikari..", "org.springframework.jdbc..",
                        "br.gov.observatorioaps.pecadapter..",
                        "br.gov.observatorioaps.sourceconnector..")
                .check(CLASSES);
    }
}
