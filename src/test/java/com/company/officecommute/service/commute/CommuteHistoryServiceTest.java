package com.company.officecommute.service.commute;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.closing.ClosingException;
import com.company.officecommute.domain.closing.CommuteLockReason;
import com.company.officecommute.domain.commute.CommuteAlreadyEndedException;
import com.company.officecommute.domain.commute.CommuteEndWindowExpiredException;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteHistoryFixture;
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.correction.CommuteCorrectionRequestRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.closing.CommutePeriodGuard;
import com.company.officecommute.service.closing.ProtectedPeriods;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.company.officecommute.domain.employee.Role.MEMBER;
import static com.company.officecommute.service.employee.Employees.employee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommuteHistoryServiceTest {

    private CommuteHistoryService commuteHistoryService;

    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CommuteCorrectionRequestRepository correctionRequestRepository;

    @Mock
    private CommuteWriteLock commuteWriteLock;

    @Mock
    private CommutePeriodGuard commutePeriodGuard;

    private ZonedDateTime workStartTime;

    private ZonedDateTime workEndTime;

    @BeforeEach
    void setUp() {
        workStartTime = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        workEndTime = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.of("Asia/Seoul"));
        Clock fixedClock = Clock.fixed(workEndTime.toInstant(), ZoneId.of("UTC"));
        commuteHistoryService = newService(fixedClock);
    }

    private CommuteHistoryService newService(Clock clock) {
        return new CommuteHistoryService(commuteHistoryRepository, employeeRepository, correctionRequestRepository,
                commuteWriteLock, commutePeriodGuard, clock);
    }

    @Test
    void testRegisterWorkStartTime() {
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));

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
                        .findFirstByEmployeeIdAndUsingDayOffFalseOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.of(CommuteHistoryFixture.open(1L, 1L, workStartTime)));
        BDDMockito.given(commuteHistoryRepository.updateWorkEndTimeIfOpen(eq(1L), any(Instant.class), eq(10L * 60)))
                .willReturn(1);

        // when
        commuteHistoryService.registerWorkEndTime(1L);

        // then
        ArgumentCaptor<Instant> endTimeCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(commuteHistoryRepository).updateWorkEndTimeIfOpen(eq(1L), endTimeCaptor.capture(), eq(10L * 60));
        assertThat(endTimeCaptor.getValue()).isEqualTo(workEndTime.toInstant());
        then(commuteHistoryRepository).should(never()).save(any(CommuteHistory.class));
    }

    @Test
    @DisplayName("registerWorkEndTime — 조회 후 다른 요청이 먼저 퇴근 처리해 update가 0건이면 CommuteAlreadyEndedException")
    void registerWorkEndTime_throwsAlreadyEnded_whenConditionalUpdateMatchesNoRow() {
        // given
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository
                        .findFirstByEmployeeIdAndUsingDayOffFalseOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.of(CommuteHistoryFixture.open(1L, 1L, workStartTime)));
        BDDMockito.given(commuteHistoryRepository.updateWorkEndTimeIfOpen(eq(1L), any(Instant.class), eq(10L * 60)))
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
        commuteHistoryService = newService(fixed);

        Employee seoulEmployee = new EmployeeBuilder().withId(10L).withName("seoul").withRole(MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1)).withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("SE0001").withEmail("seoul@company.com").withPassword("password123")
                .withTimezone("Asia/Seoul").build();

        BDDMockito.given(employeeRepository.findById(10L)).willReturn(Optional.of(seoulEmployee));

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
        commuteHistoryService = newService(fixed);

        Employee laEmployee = new EmployeeBuilder().withId(20L).withName("la").withRole(MEMBER)
                .withBirthday(LocalDate.of(1990, 1, 1)).withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("LA0001").withEmail("la@company.com").withPassword("password123")
                .withTimezone("America/Los_Angeles").build();

        BDDMockito.given(employeeRepository.findById(20L)).willReturn(Optional.of(laEmployee));

        commuteHistoryService.registerWorkStartTime(20L);

        ArgumentCaptor<CommuteHistory> captor = ArgumentCaptor.forClass(CommuteHistory.class);
        verify(commuteHistoryRepository).saveAndFlush(captor.capture());
        CommuteHistory saved = captor.getValue();
        // LA 시각으로는 2026-01-14
        assertThat(saved.getWorkDate()).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(saved.getWorkZone()).isEqualTo("America/Los_Angeles");
    }

    @Test
    @DisplayName("registerWorkStartTime — 같은 employee+workDate 기록이 있으면 DuplicateWorkOnDateException")
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
    @DisplayName("registerWorkStartTime — H2가 장식한 중복 제약명도 Duplicate로 재던진다")
    void registerWorkStartTime_translatesDecoratedH2ConstraintName() {
        // given
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "unique constraint",
                new ConstraintViolationException("unique constraint", null, "PUBLIC.UK_COMMUTE_HISTORY_EMPLOYEE_DATE_INDEX_C")
        );
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
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
    @DisplayName("registerWorkStartTime — 출근 전에 직원 행을 잠그고, 근무일이 보호 기간이면 저장하지 않는다")
    void registerWorkStartTime_locksEmployeeAndRejectsProtectedWorkDate() {
        // given
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.willThrow(CommuteLockReason.MONTH_CLOSED.toException())
                .given(commutePeriodGuard).assertWritable(LocalDate.of(2024, 1, 1));

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isInstanceOf(ClosingException.class);
        then(commuteWriteLock).should().lockEmployee(1L);
        then(commuteHistoryRepository).should(never()).saveAndFlush(any(CommuteHistory.class));
    }

    @Test
    @DisplayName("registerWorkEndTime — 출근 후 24시간을 넘긴 최신 근무는 일반 퇴근하지 않는다")
    void registerWorkEndTime_rejectsAfter24Hours() {
        // given — 고정 시각(2024-01-01 18:00 KST)보다 24시간 1분 전 출근
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository.findFirstByEmployeeIdAndUsingDayOffFalseOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.of(CommuteHistoryFixture.open(1L, 1L, workEndTime.minusHours(24).minusMinutes(1))));

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkEndTime(1L))
                .isInstanceOf(CommuteEndWindowExpiredException.class);
        then(commuteHistoryRepository).should(never())
                .updateWorkEndTimeIfOpen(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("registerWorkStartTime — cause 체인에 ConstraintViolationException이 없으면 그대로 재던진다")
    void registerWorkStartTime_rethrowsViolationWithoutConstraintViolationCause() {
        // given
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "not null violation",
                new RuntimeException("boom")
        );
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
                .willReturn(false);
        BDDMockito.given(commuteHistoryRepository.saveAndFlush(any(CommuteHistory.class)))
                .willThrow(violation);

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isSameAs(violation);
    }

    @Test
    @DisplayName("registerWorkStartTime — 제약명이 null인 ConstraintViolation은 그대로 재던진다")
    void registerWorkStartTime_rethrowsViolationWithNullConstraintName() {
        // given
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "unique constraint",
                new ConstraintViolationException("unique constraint", null, (String) null)
        );
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
                .willReturn(false);
        BDDMockito.given(commuteHistoryRepository.saveAndFlush(any(CommuteHistory.class)))
                .willThrow(violation);

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isSameAs(violation);
    }

    @Test
    @DisplayName("registerWorkStartTime — cause 체인 깊숙이 중첩된 중복 제약도 Duplicate로 재던진다")
    void registerWorkStartTime_translatesDeeplyNestedConstraintViolation() {
        // given — DataIntegrityViolation → RuntimeException → ConstraintViolation (depth 2)
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "unique constraint",
                new RuntimeException("wrapper",
                        new ConstraintViolationException("unique constraint", null, "uk_commute_history_employee_date"))
        );
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
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
    @DisplayName("registerDayOffs — 신청 일자마다 연차 기록을 저장한다")
    void registerDayOffs_savesDayOffPerDate() {
        // given
        BDDMockito.given(commutePeriodGuard.load()).willReturn(new ProtectedPeriods(List.of(), Set.of()));
        ZoneId zone = ZoneId.of("Asia/Seoul");
        List<AnnualLeave> savedLeaves = List.of(
                new AnnualLeave(1L, 1L, LocalDate.now().plusDays(10)),
                new AnnualLeave(2L, 1L, LocalDate.now().plusDays(11))
        );
        BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
                .willReturn(false);

        // when
        commuteHistoryService.registerDayOffs(1L, savedLeaves, zone);

        // then
        ArgumentCaptor<CommuteHistory> captor = ArgumentCaptor.forClass(CommuteHistory.class);
        verify(commuteHistoryRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CommuteHistory::getWorkDate)
                .containsExactly(LocalDate.now().plusDays(10), LocalDate.now().plusDays(11));
    }

    @Test
    @DisplayName("registerDayOffs — 신청 일자에 이미 출근 기록이 있으면 DuplicateWorkOnDateException")
    void registerDayOffs_throwsDuplicate_whenWorkRecordExistsOnDate() {
        // given
        ZoneId zone = ZoneId.of("Asia/Seoul");
        LocalDate conflictDate = LocalDate.now().plusDays(10);
        List<AnnualLeave> savedLeaves = List.of(new AnnualLeave(1L, 1L, conflictDate));
        BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(1L, conflictDate))
                .willReturn(true);

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerDayOffs(1L, savedLeaves, zone))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasMessageContaining(conflictDate.toString());

        then(commuteHistoryRepository).should(never()).saveAndFlush(any(CommuteHistory.class));
    }

    @Test
    @DisplayName("registerDayOffs — existsBy 통과 후 race로 중복 제약에 걸리면 Duplicate로 재던진다")
    void registerDayOffs_translatesDataIntegrityViolationRace() {
        // given
        BDDMockito.given(commutePeriodGuard.load()).willReturn(new ProtectedPeriods(List.of(), Set.of()));
        ZoneId zone = ZoneId.of("Asia/Seoul");
        List<AnnualLeave> savedLeaves = List.of(new AnnualLeave(1L, 1L, LocalDate.now().plusDays(10)));
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "unique constraint",
                new ConstraintViolationException("unique constraint", null, "uk_commute_history_employee_date")
        );
        BDDMockito.given(commuteHistoryRepository.existsByEmployeeIdAndWorkDate(eq(1L), any(LocalDate.class)))
                .willReturn(false);
        BDDMockito.given(commuteHistoryRepository.saveAndFlush(any(CommuteHistory.class)))
                .willThrow(violation);

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerDayOffs(1L, savedLeaves, zone))
                .isInstanceOf(DuplicateWorkOnDateException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
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
        BDDMockito.given(commuteHistoryRepository.saveAndFlush(any(CommuteHistory.class)))
                .willThrow(violation);

        // when / then
        assertThatThrownBy(() -> commuteHistoryService.registerWorkStartTime(1L))
                .isSameAs(violation);
    }
}
