package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistoryFixture;
import com.company.officecommute.domain.commute.CommuteStatus;
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.commute.CommuteAlreadyEndedException;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.commute.response.CommuteDetailResponse;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class CommuteHistoryServiceIntegrationTest {

    @Autowired private CommuteHistoryService commuteHistoryService;
    @Autowired private CommuteHistoryRepository commuteHistoryRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private TeamRepository teamRepository;

    private Long testEmployeeId;

    @BeforeEach
    void setup() {
        commuteHistoryRepository.deleteAll();
        employeeRepository.deleteAll();
        teamRepository.deleteAll();

        Team team = Team.register("테스트팀", null, 0);
        teamRepository.save(team);

        Employee employee = new EmployeeBuilder()
                .withTeam(team)
                .withName("테스트직원")
                .withRole(Role.MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("TEST001")
                .withEmail("test@company.com")
                .withPassword("password123")
                .build();
        Employee savedEmployee = employeeRepository.save(employee);
        testEmployeeId = savedEmployee.getEmployeeId();
    }

    @AfterEach
    void cleanup() {
        commuteHistoryRepository.deleteAll();
        employeeRepository.deleteAll();
        teamRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 날 미완료 근무 중 재출근시 DuplicateWorkOnDateException (광클 방어)")
    void sameDayDoubleStartWhilePreviousOpenThrowsDuplicateWorkOnDate() {
        commuteHistoryService.registerWorkStartTime(testEmployeeId);

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasMessageContaining("이미 출근 기록이 존재");
    }

    @Test
    @DisplayName("과거 미퇴근이 남아 있어도 새 근무일 출근은 성공하고, 퇴근은 최신 근무만 닫는다")
    void previousOpenCommuteDoesNotBlockNewWorkDay() {
        ZoneId korea = ZoneId.of("Asia/Seoul");
        ZonedDateTime twoDaysAgo = ZonedDateTime.now(korea)
                .minusDays(2)
                .withHour(9).withMinute(0).withSecond(0).withNano(0);
        CommuteHistory previousOpen = commuteHistoryRepository.save(CommuteHistoryFixture.open(
                null, testEmployeeId, twoDaysAgo, korea));

        commuteHistoryService.registerWorkStartTime(testEmployeeId);
        commuteHistoryService.registerWorkEndTime(testEmployeeId);

        // 과거 미퇴근은 자동으로 닫히거나 추정값으로 채워지지 않는다
        assertThat(commuteHistoryRepository.findById(previousOpen.getCommuteHistoryId()).orElseThrow().endTimeIsNull())
                .isTrue();
        // 최신 근무가 이미 끝났으면 재클릭해도 과거 미퇴근을 대신 닫지 않는다
        assertThatThrownBy(() -> commuteHistoryService.registerWorkEndTime(testEmployeeId))
                .isInstanceOf(CommuteAlreadyEndedException.class);
        assertThat(commuteHistoryRepository.findById(previousOpen.getCommuteHistoryId()).orElseThrow().endTimeIsNull())
                .isTrue();
    }

    @Test
    @DisplayName("일반 퇴근은 version 을 1 올린다 — 이 기록에 대한 정정 대기 요청이 원본 변경을 알아챈다")
    void regularEndIncrementsVersion() {
        commuteHistoryService.registerWorkStartTime(testEmployeeId);
        CommuteHistory started = commuteHistoryRepository.findAll().getFirst();

        commuteHistoryService.registerWorkEndTime(testEmployeeId);

        assertThat(commuteHistoryRepository.findById(started.getCommuteHistoryId()).orElseThrow().getVersion())
                .isEqualTo(started.getVersion() + 1);
    }

    @Test
    @DisplayName("미래 연차 기록이 있어도 오늘 실제 출근과 퇴근이 가능하다")
    void testFutureAnnualLeaveDoesNotBlockTodayWorkStartAndEnd() {
        commuteHistoryRepository.save(CommuteHistoryFixture.annualLeave(
                testEmployeeId,
                LocalDate.now().plusDays(10),
                ZonedDateTime.now().getZone()
        ));

        commuteHistoryService.registerWorkStartTime(testEmployeeId);
        commuteHistoryService.registerWorkEndTime(testEmployeeId);

        assertThat(commuteHistoryRepository.findAll()).hasSize(2)
                .noneMatch(CommuteHistory::endTimeIsNull);
    }

    @Test
    @DisplayName("퇴근 후 같은 날 재출근시 DuplicateWorkOnDateException")
    void sameDaySecondStartThrowsDuplicateWorkOnDate() {
        commuteHistoryService.registerWorkStartTime(testEmployeeId);
        commuteHistoryService.registerWorkEndTime(testEmployeeId);

        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(testEmployeeId))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasMessageContaining("이미 출근 기록이 존재");
    }

    @Test
    @DisplayName("월별 조회는 일자별 출퇴근 시각과 상태를 돌려준다 — 24시간이 지난 미기록은 CORRECTION_REQUIRED")
    void monthlyViewCarriesCheckInAndCheckOutTimes() {
        ZoneId korea = ZoneId.of("Asia/Seoul");
        ZonedDateTime closedStart = ZonedDateTime.of(2026, 3, 2, 9, 3, 0, 0, korea);
        ZonedDateTime closedEnd = ZonedDateTime.of(2026, 3, 2, 18, 58, 0, 0, korea);
        ZonedDateTime openStart = ZonedDateTime.of(2026, 3, 3, 9, 1, 0, 0, korea);
        commuteHistoryRepository.save(
                CommuteHistoryFixture.ended(null, testEmployeeId, closedStart, closedEnd, korea));
        commuteHistoryRepository.save(
                CommuteHistoryFixture.open(null, testEmployeeId, openStart, korea));
        commuteHistoryRepository.save(
                CommuteHistoryFixture.annualLeave(testEmployeeId, LocalDate.of(2026, 3, 4), korea));

        WorkDurationPerDateResponse response =
                commuteHistoryService.getWorkDurationPerDate(testEmployeeId, YearMonth.of(2026, 3));

        assertThat(response.details())
                .extracting(
                        CommuteDetailResponse::date,
                        CommuteDetailResponse::workStartTime,
                        CommuteDetailResponse::workEndTime,
                        CommuteDetailResponse::workingMinutes,
                        CommuteDetailResponse::usingDayOff,
                        CommuteDetailResponse::status)
                .containsExactlyInAnyOrder(
                        tuple(LocalDate.of(2026, 3, 2), closedStart.toOffsetDateTime(),
                                closedEnd.toOffsetDateTime(), 9L * 60 + 55, false, CommuteStatus.COMPLETED),
                        tuple(LocalDate.of(2026, 3, 3), openStart.toOffsetDateTime(), null, 0L, false,
                                CommuteStatus.CORRECTION_REQUIRED),
                        tuple(LocalDate.of(2026, 3, 4), null, null, 0L, true, CommuteStatus.DAY_OFF));
        assertThat(response.sumWorkingMinutes()).isEqualTo(9L * 60 + 55);
    }
}
