package esusdata.report;

import esusdata.config.SqliteConfig;
import esusdata.report.model.ReportExportRepository;
import esusdata.result.model.ResultRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class ReportConfig {

    private static final Logger log = LoggerFactory.getLogger(ReportConfig.class);

    @Bean
    @DependsOn(SqliteConfig.FLYWAY_MIGRATION)
    public ReportExportRepository reportExportRepository(JdbcTemplate sqliteJdbcTemplate) {
        return new JdbcReportExportRepository(sqliteJdbcTemplate);
    }

    @Bean
    public ReportExportService reportExportService(
            ReportExportRepository reportExportRepository, ResultRepository resultRepository, Clock clock) {
        return new ReportExportService(reportExportRepository, resultRepository, clock);
    }

    /**
     * Purges exports that expired while the application was down (§1.12 L437, orphan cleanup).
     * Runs at boot as an eager {@code @Bean} factory, like {@code jobRecoveryReport}, before Tomcat
     * accepts a request.
     */
    @Bean
    @DependsOn(SqliteConfig.FLYWAY_MIGRATION)
    public ExpiredExportsPurge expiredExportsPurgedOnBoot(ReportExportService reportExportService) {
        int purged = reportExportService.purgeExpired();
        log.info("Expired exports purged on boot: {}", purged);
        return new ExpiredExportsPurge(purged);
    }

    public record ExpiredExportsPurge(int purged) {}
}
