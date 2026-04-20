package com.company.officecommute.service.annual_leave;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse;
import com.company.officecommute.repository.annual_leave.AnnualLeaveRepository;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.employee.EmployeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.company.officecommute.domain.employee.Role.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnnualLeaveServiceTest {

    @InjectMocks
    private AnnualLeaveService annualLeaveService;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AnnualLeaveRepository annualLeaveRepository;
    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    private Long employeeId;
    private Employee employee;

    @BeforeEach
    void setUp() {
        employeeId = 1L;
        Team team = new Team("백엔드팀");
        employee = new EmployeeBuilder()
                .withId(employeeId)
                .withTeam(team)
                .withName("임형준")
                .withRole(MEMBER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("EMP001")
                .withEmail("hyungjunn@company.com")
                .withPassword("password123")
                .build();
    }

    @Test
    @DisplayName("연차 신청이 정상적으로 처리된다")
    void testEnrollAnnualLeave() {
        List<LocalDate> wantedDates = List.of(
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(11)
        );
        BDDMockito.given(employeeRepository.findByEmployeeIdWithTeam(1L))
                .willReturn(Optional.of(employee));
        BDDMockito.given(annualLeaveRepository.findByEmployeeId(employeeId))
                .willReturn(List.of());

        List<AnnualLeave> savedLeaves = List.of(
                new AnnualLeave(1L, employeeId, wantedDates.get(0)),
                new AnnualLeave(2L, employeeId, wantedDates.get(1))
        );
        BDDMockito.given(annualLeaveRepository.saveAll(any()))
                .willReturn(savedLeaves);
        BDDMockito.given(commuteHistoryRepository.saveAll(any()))
                .willReturn(List.of(
                        new CommuteHistory(employeeId),
                        new CommuteHistory(employeeId)
                ));

        List<AnnualLeaveEnrollmentResponse> responses = annualLeaveService.enrollAnnualLeave(employeeId, wantedDates);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).annualLeaveId()).isEqualTo(1L);
        assertThat(responses.get(0).enrolledDate()).isEqualTo(wantedDates.get(0));
        verify(commuteHistoryRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("남은 연차를 조회할 수 있다")
    void testGetRemainingAnnualLeave() {
        BDDMockito.given(annualLeaveRepository.findByEmployeeId(1L))
                .willReturn(List.of(new AnnualLeave(1L, 1L, LocalDate.now().plusDays(20))));

        AnnualLeaveGetRemainingResponse response = annualLeaveService.getRemainingAnnualLeaves(1L);

        verify(annualLeaveRepository).findByEmployeeId(1L);
        assertThat(response.employeeId()).isEqualTo(1L);
    }
}
