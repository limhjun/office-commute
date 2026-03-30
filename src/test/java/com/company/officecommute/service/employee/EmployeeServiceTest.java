package com.company.officecommute.service.employee;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @InjectMocks
    private EmployeeService employeeService;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private TeamRepository teamRepository;

    private Employee employee;
    private Team team;

    @BeforeEach
    void setUp() {
        Long employeeId = 1L;
        team = new Team("백엔드팀");
        employee = new EmployeeBuilder()
                .withId(employeeId)
                .withTeam(team)
                .withName("임형준")
                .withRole(MEMBER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("EMP001")
                .withPin("1234")
                .build();

    }

    @Test
    @DisplayName("올바른 사번과 PIN으로 인증 성공")
    void authenticate_success() {
        String employeeCode = "EMP001";
        String pin = "1234";
        BDDMockito.given(employeeRepository.findByEmployeeCode(employeeCode))
                .willReturn(Optional.of(employee));

        Employee result = employeeService.authenticate(employeeCode, pin);

        assertThat(result).isEqualTo(employee);
    }

    @Test
    @DisplayName("존재하지 않는 사번으로 인증 실패")
    void authenticate_employeeCodeNotFound() {
        BDDMockito.given(employeeRepository.findByEmployeeCode("INVALID_CODE"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.authenticate("INVALID_CODE", "1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 사번입니다");
    }

    @Test
    @DisplayName("잘못된 PIN으로 인증 실패")
    void authenticate_wrongPin() {
        String wrongPin = "9999";
        BDDMockito.given(employeeRepository.findByEmployeeCode("EMP001"))
                .willReturn(Optional.of(employee));

        assertThatThrownBy(() -> employeeService.authenticate("EMP001", wrongPin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PIN이 일치하지 않습니다.");
    }

    @Test
    @DisplayName("직원이 정상적으로 등록된다")
    void registerEmployee_success() {
        EmployeeSaveRequest request = new EmployeeSaveRequest(
                "임형준",
                MEMBER,
                LocalDate.of(1998, 8, 18),
                LocalDate.of(2024, 1, 1),
                "EMP001",
                "1234"
        );
        BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001"))
                .willReturn(false);

        employeeService.registerEmployee(request);

        verify(employeeRepository).existsByEmployeeCode("EMP001");
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    @DisplayName("중복된 직원 코드로 등록시 예외가 발생한다")
    void registerEmployee_with_duplicateEmpCode() {
        EmployeeSaveRequest request = new EmployeeSaveRequest(
                "임형준",
                MEMBER,
                LocalDate.of(1998, 8, 18),
                LocalDate.of(2024, 1, 1),
                "EMP001",
                "1234"
        );
        BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001"))
                .willReturn(true);

        assertThatThrownBy(() -> employeeService.registerEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 직원 코드입니다.");
        verify(employeeRepository).existsByEmployeeCode("EMP001");
        verify(employeeRepository, BDDMockito.never()).save(any(Employee.class));
    }

    @Test
    void testRegisterEmployee() {
        EmployeeSaveRequest request = new EmployeeSaveRequest(
                "임형준",
                MEMBER,
                LocalDate.of(1998, 8, 18),
                LocalDate.of(2024, 1, 1),
                "EMP001",
                "1234"
        );
        BDDMockito.given(employeeRepository.save(any(Employee.class)))
                .willReturn(employee);

        employeeService.registerEmployee(request);

        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void testFindAllEmployee() {
        BDDMockito.given(employeeRepository.findEmployeeHierarchy())
                .willReturn(List.of(employee));

        List<EmployeeFindResponse> employees = employeeService.findAllEmployee();

        assertThat(employees).hasSize(1);
        assertThat(employees.contains(EmployeeFindResponse.from(employee))).isTrue();
    }

    @Test
    void testUpdateEmployeeTeamName() {
        EmployeeUpdateTeamNameRequest request = new EmployeeUpdateTeamNameRequest(1L, "백엔드팀");
        BDDMockito.given(employeeRepository.findById(1L))
                .willReturn(Optional.of(employee));

        BDDMockito.given(teamRepository.findByName(anyString()))
                .willReturn(Optional.of(team));

        employeeService.updateEmployeeTeamName(request);

        assertThat(employee.getTeamName()).isEqualTo("백엔드팀");
        assertThat(team.getMemberCount()).isEqualTo(1);
    }

}
