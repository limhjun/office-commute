package com.company.officecommute.repository.correction;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteHistoryFixture;
import com.company.officecommute.domain.correction.CommuteCorrectionRequest;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.report.ReportDispatch;
import com.company.officecommute.domain.report.ReportFile;
import com.company.officecommute.domain.report.ReportKind;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.report.ReportDispatchRepository;
import com.company.officecommute.repository.report.ReportFileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V15·V16 마이그레이션을 Flyway 로 적용하고 {@code ddl-auto=validate} 로 엔티티와 대조한다 — 컨텍스트가 뜨는 것
 * 자체가 스키마 정합 검사다. 대기 요청 UNIQUE, 발송 종류별 UNIQUE, LONGBLOB 보관, DATETIME(6) 정밀도,
 * 직원 행 잠금 쿼리의 MySQL 문법을 확인한다.
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
class CommuteCorrectionMySqlIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired private CommuteCorrectionRequestRepository correctionRequestRepository;
    @Autowired private CommuteHistoryRepository commuteHistoryRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ReportDispatchRepository reportDispatchRepository;
    @Autowired private ReportFileRepository reportFileRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("V15 의 uk_correction_request_pending 이 같은 기록의 두 번째 대기 요청을 막고, 처리 후 재신청은 허용한다")
    void pendingUniqueConstraint() {
        Employee member = saveEmployee("MYMEM01");
        CommuteHistory open = commuteHistoryRepository.saveAndFlush(CommuteHistoryFixture.open(
                null, member.getEmployeeId(), ZonedDateTime.of(2026, 9, 2, 9, 0, 0, 0, KST), KST));

        CommuteCorrectionRequest first = correctionRequestRepository.saveAndFlush(request(open));
        assertThatThrownBy(() -> correctionRequestRepository.saveAndFlush(request(open)))
                .isInstanceOf(DataIntegrityViolationException.class);

        first.cancel(member.getEmployeeId(), NOW);
        correctionRequestRepository.saveAndFlush(first);
        assertThat(correctionRequestRepository.saveAndFlush(request(open)).getCorrectionRequestId()).isNotNull();
    }

    @Test
    @DisplayName("정정 승인 UPDATE 는 버전이 같을 때만 반영되고 버전을 올린다 — DATETIME(6) 은 초 단위 값을 그대로 보존한다")
    void conditionalUpdateWithVersion() {
        Employee member = saveEmployee("MYMEM02");
        CommuteHistory open = commuteHistoryRepository.saveAndFlush(CommuteHistoryFixture.open(
                null, member.getEmployeeId(), ZonedDateTime.of(2026, 9, 3, 9, 0, 0, 0, KST), KST));
        Instant end = ZonedDateTime.of(2026, 9, 3, 19, 30, 59, 0, KST).toInstant();

        Integer stale = transactionTemplate.execute(status -> commuteHistoryRepository.updateWorkEndTimeIfVersion(
                open.getCommuteHistoryId(), open.getVersion() + 1, end, 630));
        Integer applied = transactionTemplate.execute(status -> commuteHistoryRepository.updateWorkEndTimeIfVersion(
                open.getCommuteHistoryId(), open.getVersion(), end, 630));

        assertThat(stale).isZero();
        assertThat(applied).isOne();
        CommuteHistory reloaded = commuteHistoryRepository.findById(open.getCommuteHistoryId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(open.getVersion() + 1);
        assertThat(reloaded.getWorkEndTime()).isEqualTo(end);
    }

    @Test
    @DisplayName("V16 — 발송 이력은 월·종류별로 유일하고, 보관 파일은 LONGBLOB 으로 그대로 읽힌다")
    void dispatchKindAndReportFile() {
        YearMonth august = YearMonth.of(2026, 8);
        reportDispatchRepository.saveAndFlush(ReportDispatch.claim(august, ReportKind.ORIGINAL, NOW));
        reportDispatchRepository.saveAndFlush(ReportDispatch.claim(august, ReportKind.CORRECTION, NOW));
        assertThatThrownBy(() -> reportDispatchRepository.saveAndFlush(ReportDispatch.claim(august, ReportKind.ORIGINAL, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);

        byte[] content = new byte[300_000];
        content[0] = 1;
        content[content.length - 1] = 2;
        reportFileRepository.saveAndFlush(new ReportFile(august, ReportKind.ORIGINAL, "a.xlsx", content, 3, NOW));
        ReportFile stored = reportFileRepository.findByTargetYearMonthAndKind(august, ReportKind.ORIGINAL).orElseThrow();
        assertThat(stored.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("직원 행 잠금 쿼리가 MySQL 문법으로 실행된다")
    void lockQueriesRun() {
        Employee employee = saveEmployee("MYMEM03");

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(employeeRepository.lockById(employee.getEmployeeId())).contains(employee.getEmployeeId());
            assertThat(employeeRepository.lockAll()).contains(employee.getEmployeeId());
        });
    }

    private CommuteCorrectionRequest request(CommuteHistory commute) {
        return CommuteCorrectionRequest.submit(commute, Role.MEMBER, null,
                commute.getWorkStartTime().plusSeconds(3600), 60, "사유", NOW);
    }

    private Employee saveEmployee(String code) {
        return employeeRepository.saveAndFlush(new EmployeeBuilder()
                .withName(code)
                .withRole(Role.MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode(code)
                .withEmail(code.toLowerCase() + "@company.com")
                .withPassword("password123")
                .build());
    }
}
