package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteAlreadyEndedException;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.employee.EmployeeBuilder;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.company.officecommute.domain.employee.Role.MEMBER;
import static com.company.officecommute.service.employee.Employees.employee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommuteHistoryServiceTest {

    private CommuteHistoryService commuteHistoryService;

    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    @Mock
    private EmployeeRepository employeeRepository;


    private ZonedDateTime workStartTime;

    private ZonedDateTime workEndTime;

    @BeforeEach
    void setUp() {
        workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        Clock fixedClock = Clock.fixed(workEndTime.toInstant(), ZoneId.of("UTC"));
        commuteHistoryService = new CommuteHistoryService(commuteHistoryRepository, employeeRepository, fixedClock);
    }

    @Test
    void testRegisterWorkStartTime() {
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository
                        .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.empty());

        commuteHistoryService.registerWorkStartTime(1L);

        verify(commuteHistoryRepository).saveAndFlush(any(CommuteHistory.class));
    }

    @Test
    @DisplayName("registerWorkEndTime — 조건부 update(workEndTime IS NULL)로 퇴근 시각과 근무 분을 기록한다")
    void testRegisterWorkEndTime() {
        // given
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository
                        .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.of(new CommuteHistory(1L, 1L, workStartTime, null, 0)));
        BDDMockito.given(commuteHistoryRepository.updateWorkEndTimeIfOpen(eq(1L), any(ZonedDateTime.class), eq(10L * 60)))
                .willReturn(1);

        // when
        commuteHistoryService.registerWorkEndTime(1L);

        // then
        ArgumentCaptor<ZonedDateTime> endTimeCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(commuteHistoryRepository).updateWorkEndTimeIfOpen(eq(1L), endTimeCaptor.capture(), eq(10L * 60));
        assertThat(endTimeCaptor.getValue().toInstant()).isEqualTo(workEndTime.toInstant());
        then(commuteHistoryRepository).should(never()).save(any(CommuteHistory.class));
    }

    @Test
    @DisplayName("registerWorkEndTime — 조회 후 다른 요청이 먼저 퇴근 처리해 update가 0건이면 CommuteAlreadyEndedException")
    void registerWorkEndTime_throwsAlreadyEnded_whenConditionalUpdateMatchesNoRow() {
        // given
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository
                        .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.of(new CommuteHistory(1L, 1L, workStartTime, null, 0)));
        BDDMockito.given(commuteHistoryRepository.updateWorkEndTimeIfOpen(eq(1L), any(ZonedDateTime.class), eq(10L * 60)))
                .willReturn(0);

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkEndTime(1L))
                .isInstanceOf(CommuteAlreadyEndedException.class)
                .hasMessage("이미 퇴근 처리된 근무입니다.");
    }

    @Test
    void seoulEmployee_workDate가_seoul_로컬일자로_계산된다() {
        // 2026-01-15 08:00 KST = 2026-01-14 23:00 UTC
        ZonedDateTime nowUtc = ZonedDateTime.of(2026, 1, 14, 23, 0, 0, 0, ZoneId.of("UTC"));
        Clock fixed = Clock.fixed(nowUtc.toInstant(), ZoneId.of("UTC"));
        commuteHistoryService = new CommuteHistoryService(commuteHistoryRepository, employeeRepository, fixed);

        Employee seoulEmployee = new EmployeeBuilder().withId(10L).withName("seoul").withRole(MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1)).withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("SE0001").withEmail("seoul@company.com").withPassword("password123")
                .withTimezone("Asia/Seoul").build();

        BDDMockito.given(employeeRepository.findById(10L)).willReturn(Optional.of(seoulEmployee));
        BDDMockito.given(commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(10L))
                .willReturn(Optional.empty());

        commuteHistoryService.registerWorkStartTime(10L);

        ArgumentCaptor<CommuteHistory> captor = ArgumentCaptor.forClass(CommuteHistory.class);
        verify(commuteHistoryRepository).saveAndFlush(captor.capture());
        CommuteHistory saved = captor.getValue();
        assertThat(saved.getWorkDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(saved.getWorkZone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void laEmployee_같은_instant이라도_LA_로컬일자로_계산된다() {
        // 2026-01-15 08:00 KST = 2026-01-14 23:00 UTC = 2026-01-14 15:00 PST
        ZonedDateTime nowUtc = ZonedDateTime.of(2026, 1, 14, 23, 0, 0, 0, ZoneId.of("UTC"));
        Clock fixed = Clock.fixed(nowUtc.toInstant(), ZoneId.of("UTC"));
        commuteHistoryService = new CommuteHistoryService(commuteHistoryRepository, employeeRepository, fixed);

        Employee laEmployee = new EmployeeBuilder().withId(20L).withName("la").withRole(MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1)).withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("LA0001").withEmail("la@company.com").withPassword("password123")
                .withTimezone("America/Los_Angeles").build();

        BDDMockito.given(employeeRepository.findById(20L)).willReturn(Optional.of(laEmployee));
        BDDMockito.given(commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(20L))
                .willReturn(Optional.empty());

        commuteHistoryService.registerWorkStartTime(20L);

        ArgumentCaptor<CommuteHistory> captor = ArgumentCaptor.forClass(CommuteHistory.class);
        verify(commuteHistoryRepository).saveAndFlush(captor.capture());
        CommuteHistory saved = captor.getValue();
        // LA 시각으로는 2026-01-14
        assertThat(saved.getWorkDate()).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(saved.getWorkZone()).isEqualTo("America/Los_Angeles");
    }

    @Test
    @DisplayName("registerWorkStartTime — 같은 employee+workDate 기록이 있으면 미완료 근무 검증보다 먼저 DuplicateWorkOnDateException")
    void registerWorkStartTime_throwsDuplicate_whenPriorRecordExistsOnSameDate() {
        // given
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
                .willReturn(true);

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasMessageContaining("이미 출근 기록이 존재");

        then(commuteHistoryRepository).should(never())
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L);
        then(commuteHistoryRepository).should(never()).saveAndFlush(any(CommuteHistory.class));
    }

    @Test
    @DisplayName("registerWorkStartTime — saveAndFlush의 중복 race를 Duplicate로 재던진다")
    void registerWorkStartTime_translatesDataIntegrityViolation() {
        // given
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "unique constraint",
                new ConstraintViolationException("unique constraint", null, "uk_commute_history_employee_date")
        );
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository
                        .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.empty());
        BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
                .willReturn(false);
        BDDMockito.given(commuteHistoryRepository.saveAndFlush(any(CommuteHistory.class)))
                .willThrow(violation);

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("registerWorkStartTime — race로 existsBy 직후 같은 날 open commute가 생겼다면 Duplicate로 던진다")
    void registerWorkStartTime_translatesSameDayOpenCommuteRaceToDuplicate() {
        // given — existsBy=false 통과 후 다른 thread가 막 commit한 상황을 시뮬레이션.
        // fixed clock(2024-01-01 18:00 KST)과 같은 날짜로 open commute를 만든다.
        ZoneId zone = ZoneId.of("Asia/Seoul");
        CommuteHistory openTodayCommute = new CommuteHistory(
                null,
                1L,
                ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, zone),
                null,
                0,
                zone
        );
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
                .willReturn(false);
        BDDMockito.given(commuteHistoryRepository
                        .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.of(openTodayCommute));

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasMessageContaining("이미 출근 기록이 존재");

        then(commuteHistoryRepository).should(never()).saveAndFlush(any(CommuteHistory.class));
    }

    @Test
    @DisplayName("registerWorkStartTime — 다른 날 open commute가 있으면 PreviousCommuteNotEndedException")
    void registerWorkStartTime_throwsPreviousCommuteNotEnded_whenOpenCommuteOnDifferentDate() {
        // given — fixed clock(2024-01-01) 기준 어제 미완료 근무가 남아있는 상태
        ZoneId zone = ZoneId.of("Asia/Seoul");
        CommuteHistory openYesterdayCommute = new CommuteHistory(
                null,
                1L,
                ZonedDateTime.of(2023, 12, 31, 9, 0, 0, 0, zone),
                null,
                0,
                zone
        );
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
                .willReturn(false);
        BDDMockito.given(commuteHistoryRepository
                        .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.of(openYesterdayCommute));

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isInstanceOf(com.company.officecommute.domain.commute.PreviousCommuteNotEndedException.class);

        then(commuteHistoryRepository).should(never()).saveAndFlush(any(CommuteHistory.class));
    }

    @Test
    @DisplayName("registerWorkStartTime — 중복이 아닌 DataIntegrityViolation은 Duplicate로 오분류하지 않는다")
    void registerWorkStartTime_doesNotTranslateNonDuplicateDataIntegrityViolation() {
        // given
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "not duplicate",
                new ConstraintViolationException("not duplicate", null, "some_other_constraint")
        );
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
                .willReturn(false);
        BDDMockito.given(commuteHistoryRepository
                        .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.empty());
        BDDMockito.given(commuteHistoryRepository.saveAndFlush(any(CommuteHistory.class)))
                .willThrow(violation);

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isSameAs(violation);
    }
}
