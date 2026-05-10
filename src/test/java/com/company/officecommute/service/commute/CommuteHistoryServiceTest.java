package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.employee.EmployeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static com.company.officecommute.domain.employee.Role.MEMBER;
import static com.company.officecommute.service.employee.Employees.employee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        verify(commuteHistoryRepository).save(any(CommuteHistory.class));
    }

    @Test
    void testRegisterWorkEndTime() {
        // given
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(commuteHistoryRepository
                        .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(1L))
                .willReturn(Optional.of(new CommuteHistory(1L, 1L, workStartTime, null, 0)));

        CommuteHistory expectedCommuteHistory = new CommuteHistory(1L, 1L, workStartTime, workEndTime, 10L * 60);
        BDDMockito.given(commuteHistoryRepository.save(any(CommuteHistory.class)))
                .willReturn(expectedCommuteHistory);

        // when
        commuteHistoryService.registerWorkEndTime(1L);

        // then
        ArgumentCaptor<CommuteHistory> commuteHistoryCaptor = ArgumentCaptor.forClass(CommuteHistory.class);
        verify(commuteHistoryRepository).save(commuteHistoryCaptor.capture());
        CommuteHistory savedCommuteHistory = commuteHistoryCaptor.getValue();

        assertThat(savedCommuteHistory.getWorkEndTime().toInstant()).isEqualTo(expectedCommuteHistory.getWorkEndTime().toInstant());
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
        verify(commuteHistoryRepository).save(captor.capture());
        CommuteHistory saved = captor.getValue();
        assertThat(saved.workStartTimeToLocalDate()).isEqualTo(LocalDate.of(2026, 1, 15));
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
        verify(commuteHistoryRepository).save(captor.capture());
        CommuteHistory saved = captor.getValue();
        // LA 시각으로는 2026-01-14
        assertThat(saved.workStartTimeToLocalDate()).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(saved.getWorkZone()).isEqualTo("America/Los_Angeles");
    }
}
