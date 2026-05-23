package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.web.ApiConvertor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OverTimeServiceTest {

    private static final YearMonth YEAR_MONTH = YearMonth.of(2024, 8);

    @InjectMocks
    private OverTimeService overTimeService;

    @Mock
    private CommuteHistoryRepository commuteHistoryRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ApiConvertor apiConvertor;

    @Test
    @DisplayName("근무 기록 없는 직원도 초과근무 0분으로 포함한다")
    void calculateOverTime_includesEmployeeWithoutCommuteHistory() {
        Team backend = new Team(1L, "백엔드팀", "팀장");
        Employee recordedEmployee = employee(1L, "임형준", backend, "EMP001", "hyungjun@company.com");
        Employee noHistoryEmployee = employee(2L, "김개발", backend, "EMP002", "dev@company.com");
        given(employeeRepository.findAllWithTeam()).willReturn(List.of(recordedEmployee, noHistoryEmployee));
        given(commuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(new TotalWorkingMinutes(1L, "임형준", "백엔드팀", 9_700L)));
        givenStandardWorkingMinutes(9_600L);

        List<OverTimeCalculateResponse> responses = overTimeService.calculateOverTime(YEAR_MONTH);

        assertThat(responses)
                .extracting(
                        OverTimeCalculateResponse::id,
                        OverTimeCalculateResponse::name,
                        OverTimeCalculateResponse::teamName,
                        OverTimeCalculateResponse::overTimeMinutes
                )
                .containsExactly(
                        tuple(1L, "임형준", "백엔드팀", 100L),
                        tuple(2L, "김개발", "백엔드팀", 0L)
                );
    }

    @Test
    @DisplayName("근무 기록 있는 직원의 기존 초과근무 계산을 유지한다")
    void calculateOverTime_keepsExistingCalculationForEmployeeWithCommuteHistory() {
        Team backend = new Team(1L, "백엔드팀", "팀장");
        Employee employee = employee(1L, "임형준", backend, "EMP001", "hyungjun@company.com");
        given(employeeRepository.findAllWithTeam()).willReturn(List.of(employee));
        given(commuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of(new TotalWorkingMinutes(1L, "임형준", "백엔드팀", 10_000L)));
        givenStandardWorkingMinutes(9_600L);

        List<OverTimeCalculateResponse> responses = overTimeService.calculateOverTime(YEAR_MONTH);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().overTimeMinutes()).isEqualTo(400L);
    }

    @Test
    @DisplayName("미배정 직원은 근무 기록이 없어도 팀명을 미배정으로 표시한다")
    void calculateOverTime_usesUnassignedTeamNameForEmployeeWithoutTeam() {
        Employee employee = employee(1L, "임형준", null, "EMP001", "hyungjun@company.com");
        given(employeeRepository.findAllWithTeam()).willReturn(List.of(employee));
        given(commuteHistoryRepository.findTotalWorkingMinutesByWorkDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .willReturn(List.of());
        givenStandardWorkingMinutes(9_600L);

        List<OverTimeCalculateResponse> responses = overTimeService.calculateOverTime(YEAR_MONTH);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().teamName()).isEqualTo("미배정");
        assertThat(responses.getFirst().overTimeMinutes()).isZero();
    }

    private void givenStandardWorkingMinutes(long standardWorkingMinutes) {
        given(apiConvertor.countNumberOfStandardWorkingDays(YEAR_MONTH)).willReturn(20L);
        given(apiConvertor.calculateStandardWorkingMinutes(20L)).willReturn(standardWorkingMinutes);
    }

    private Employee employee(Long id, String name, Team team, String employeeCode, String email) {
        return new Employee(
                id,
                team,
                name,
                Role.MEMBER,
                LocalDate.of(1998, 8, 18),
                LocalDate.of(2024, 1, 1),
                employeeCode,
                email,
                "password123"
        );
    }
}
