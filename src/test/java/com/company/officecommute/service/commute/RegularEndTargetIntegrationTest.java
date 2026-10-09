package com.company.officecommute.service.commute;

import com.company.officecommute.config.MutableClock;
import com.company.officecommute.config.MutableClockConfig;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteHistoryFixture;
import com.company.officecommute.domain.commute.CommuteStatus;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.commute.response.CommuteDetailResponse;
import com.company.officecommute.dto.commute.response.RegularEndTargetResponse;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 월별 근태 응답의 일반 퇴근 대상({@code regularEndTarget}). 조회 월과 무관하게 {@code PUT /api/commute}가
 * 실제로 종료할 기록을 가리켜야 한다 — 특히 월 경계를 넘는 야간근무.
 */
@SpringBootTest
@Import(MutableClockConfig.class)
class RegularEndTargetIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Autowired private CommuteHistoryService commuteHistoryService;
    @Autowired private CommuteHistoryRepository commuteHistoryRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private MutableClock clock;

    private Long employeeId;

    @BeforeEach
    void setUp() {
        cleanUp();
        employeeId = employeeRepository.save(new EmployeeBuilder()
                .withName("야간근무자")
                .withRole(Role.MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("NIGHT01")
                .withEmail("night01@company.com")
                .withPassword("password123")
                .build()).getEmployeeId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        clock.setInstant(MutableClockConfig.DEFAULT_NOW);
    }

    private void cleanUp() {
        commuteHistoryRepository.deleteAllInBatch();
        employeeRepository.saveAll(employeeRepository.findAll().stream()
                .peek(employee -> employee.assignCorrectionApprover(null))
                .toList());
        employeeRepository.deleteAll();
    }

    @Test
    @DisplayName("9/30 22:00 시작 야간근무 — 10월 조회에는 details 가 없어도 퇴근 대상으로 식별되고, 9월 조회와 같은 기록이다")
    void overnightAcrossMonthBoundaryIsIdentifiedInBothMonths() {
        CommuteHistory night = saveOpen(at(2026, 9, 30, 22, 0));
        clock.setInstant(at(2026, 10, 1, 6, 0).toInstant());

        WorkDurationPerDateResponse october = commuteHistoryService.getWorkDurationPerDate(employeeId, OCTOBER);
        WorkDurationPerDateResponse september = commuteHistoryService.getWorkDurationPerDate(employeeId, SEPTEMBER);

        assertThat(october.details()).isEmpty();
        RegularEndTargetResponse target = october.regularEndTarget();
        assertThat(target.commuteHistoryId()).isEqualTo(night.getCommuteHistoryId());
        assertThat(target.workDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(target.workZone()).isEqualTo("Asia/Seoul");
        assertThat(target.workStartTime()).isEqualTo(OffsetDateTime.parse("2026-09-30T22:00:00+09:00"));
        assertThat(target.endableUntil()).isEqualTo(OffsetDateTime.parse("2026-10-01T22:00:00+09:00"));
        assertThat(september.regularEndTarget()).isEqualTo(target);
        assertThat(september.details())
                .extracting(CommuteDetailResponse::commuteHistoryId, CommuteDetailResponse::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(night.getCommuteHistoryId(), CommuteStatus.IN_PROGRESS));

        // 화면이 가리킨 대상과 실제 퇴근 대상이 같다
        commuteHistoryService.registerWorkEndTime(employeeId);
        assertThat(commuteHistoryRepository.findById(night.getCommuteHistoryId()).orElseThrow().endTimeIsNull()).isFalse();
        assertThat(commuteHistoryService.getWorkDurationPerDate(employeeId, OCTOBER).regularEndTarget()).isNull();
    }

    @Test
    @DisplayName("정확히 24시간까지는 대상이고, 1초라도 넘으면 대상이 없다(정정 신청 대상)")
    void targetFollows24HourBoundary() {
        CommuteHistory open = saveOpen(at(2026, 9, 30, 22, 0));

        clock.setInstant(at(2026, 10, 1, 22, 0).toInstant());
        assertThat(commuteHistoryService.getWorkDurationPerDate(employeeId, OCTOBER).regularEndTarget().commuteHistoryId())
                .isEqualTo(open.getCommuteHistoryId());

        clock.setInstant(at(2026, 10, 1, 22, 0).toInstant().plusSeconds(1));
        WorkDurationPerDateResponse expired = commuteHistoryService.getWorkDurationPerDate(employeeId, SEPTEMBER);
        assertThat(expired.regularEndTarget()).isNull();
        assertThat(expired.details().getFirst().status()).isEqualTo(CommuteStatus.CORRECTION_REQUIRED);
    }

    @Test
    @DisplayName("최신 근무가 끝났으면 이전 미퇴근이 있어도 대상이 없다 — 일반 퇴근은 과거 미퇴근을 닫지 않는다")
    void noTargetWhenLatestEnded() {
        CommuteHistory older = saveOpen(at(2026, 9, 29, 9, 0));
        commuteHistoryRepository.save(CommuteHistoryFixture.ended(
                null, employeeId, at(2026, 9, 30, 9, 0), at(2026, 9, 30, 18, 0), KST));
        clock.setInstant(at(2026, 9, 30, 19, 0).toInstant());

        WorkDurationPerDateResponse september = commuteHistoryService.getWorkDurationPerDate(employeeId, SEPTEMBER);

        assertThat(september.regularEndTarget()).isNull();
        assertThat(september.details())
                .filteredOn(detail -> detail.commuteHistoryId().equals(older.getCommuteHistoryId()))
                .extracting(CommuteDetailResponse::status)
                .containsExactly(CommuteStatus.CORRECTION_REQUIRED);
    }

    @Test
    @DisplayName("연차는 대상이 아니다 — 최신 연차 뒤에도 그 이전 실제 근무가 대상이다")
    void dayOffIsNeverTarget() {
        CommuteHistory work = saveOpen(at(2026, 9, 30, 22, 0));
        commuteHistoryRepository.save(CommuteHistoryFixture.annualLeave(employeeId, LocalDate.of(2026, 10, 2), KST));
        clock.setInstant(at(2026, 10, 1, 7, 0).toInstant());

        assertThat(commuteHistoryService.getWorkDurationPerDate(employeeId, OCTOBER).regularEndTarget().commuteHistoryId())
                .isEqualTo(work.getCommuteHistoryId());
    }

    private CommuteHistory saveOpen(ZonedDateTime start) {
        return commuteHistoryRepository.save(CommuteHistoryFixture.open(null, employeeId, start, KST));
    }

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, KST);
    }
}
