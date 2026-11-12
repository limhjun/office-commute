package com.company.officecommute.repository.report;

import com.company.officecommute.domain.report.ReportDispatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2({@code ReportDispatchRepositoryTest})는 엔티티 매핑으로 스키마를 만들기 때문에
 * "V13 마이그레이션이 엔티티와 어긋났다"를 잡지 못한다. 여기서는 Flyway 로 실제 마이그레이션을
 * 적용하고 {@code ddl-auto=validate} 로 검증하므로, 컨텍스트가 뜨는 것 자체가 V13 정합 검사다.
 * <p>
 * Docker 가 없는 환경에서는 건너뛴다(기존 MySQL 통합 테스트와 같은 조건).
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never"
})
@Testcontainers(disabledWithoutDocker = true)
class ReportDispatchRepositoryMySqlIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final Instant NOW = Instant.parse("2026-08-01T06:00:00Z");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReportDispatchRepository reportDispatchRepository;

    @Test
    @DisplayName("V16 의 uk_report_dispatch_year_month_kind 가 같은 달·같은 종류의 두 번째 선점을 막는다")
    void duplicateYearMonth_rejectedByMigrationConstraint() {
        reportDispatchRepository.saveAndFlush(ReportDispatch.claim(JULY, NOW));

        assertThatThrownBy(() -> reportDispatchRepository.saveAndFlush(ReportDispatch.claim(JULY, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
