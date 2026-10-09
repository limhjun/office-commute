package com.company.officecommute.service.employee;

import com.company.officecommute.auth.AuthenticationFailedException;
import com.company.officecommute.domain.correction.CorrectionErrorCode;
import com.company.officecommute.domain.correction.CorrectionException;
import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import com.company.officecommute.domain.employee.EmployeeAlreadyExistsException;
import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.domain.team.TeamNotFoundException;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.dto.employee.response.EmployeeRegisterResponse;
import com.company.officecommute.repository.correction.CommuteCorrectionRequestRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import com.company.officecommute.service.commute.CommuteWriteLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.company.officecommute.domain.employee.Role.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    private EmployeeService employeeService;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private CommuteCorrectionRequestRepository correctionRequestRepository;
    @Mock
    private CommuteWriteLock commuteWriteLock;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private Employee employee;
    private Team team;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeService(employeeRepository, teamRepository, passwordEncoder, correctionRequestRepository,
                commuteWriteLock);
        team = new Team(1L, "백엔드팀", "이매니저", 0);
        employee = new EmployeeBuilder()
                .withId(1L)
                .withTeam(team)
                .withName("임형준")
                .withRole(MEMBER)
                .withBirthday(LocalDate.of(1998, 8, 18))
                .withStartDate(LocalDate.of(2024, 1, 1))
                .withEmployeeCode("EMP001")
                .withEmail("hyungjunn@company.com")
                .withPassword(passwordEncoder.encode("password123"))
                .build();
    }

    private EmployeeSaveRequest sampleRequest(Long teamId) {
        return new EmployeeSaveRequest(
                "임형준",
                MEMBER,
                LocalDate.of(1998, 8, 18),
                LocalDate.of(2024, 1, 1),
                "EMP001",
                "hyungjunn@company.com",
                "password123",
                teamId,
                null
        );
    }

    @Nested
    @DisplayName("registerEmployee")
    class Register {

        @Test
        @DisplayName("teamId 없이 등록하면 미배정 직원으로 저장되고 employeeId가 반환된다")
        void registerWithoutTeam() {
            EmployeeSaveRequest request = sampleRequest(null);
            BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001")).willReturn(false);
            BDDMockito.given(employeeRepository.existsByEmail("hyungjunn@company.com")).willReturn(false);
            BDDMockito.given(employeeRepository.save(any(Employee.class))).willReturn(employee);

            EmployeeRegisterResponse response = employeeService.registerEmployee(request);

            assertThat(response.employeeId()).isEqualTo(1L);
            ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
            verify(employeeRepository).save(captor.capture());
            assertThat(captor.getValue().getTeam()).isNull();
        }

        @Test
        @DisplayName("teamId가 있으면 팀과 연결되어 저장된다")
        void registerWithTeam() {
            EmployeeSaveRequest request = sampleRequest(1L);
            BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001")).willReturn(false);
            BDDMockito.given(employeeRepository.existsByEmail("hyungjunn@company.com")).willReturn(false);
            BDDMockito.given(teamRepository.findById(1L)).willReturn(Optional.of(team));
            BDDMockito.given(employeeRepository.save(any(Employee.class))).willReturn(employee);

            employeeService.registerEmployee(request);

            ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
            verify(employeeRepository).save(captor.capture());
            assertThat(captor.getValue().getTeam()).isEqualTo(team);
        }

        @Test
        @DisplayName("password는 BCrypt로 인코딩되어 저장된다 (평문 저장 금지)")
        void registerEncodesPassword() {
            EmployeeSaveRequest request = sampleRequest(null);
            BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001")).willReturn(false);
            BDDMockito.given(employeeRepository.existsByEmail("hyungjunn@company.com")).willReturn(false);
            BDDMockito.given(employeeRepository.save(any(Employee.class))).willReturn(employee);

            employeeService.registerEmployee(request);

            ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
            verify(employeeRepository).save(captor.capture());
            String stored = captor.getValue().getPassword();
            assertThat(stored).isNotEqualTo("password123");
            assertThat(passwordEncoder.matches("password123", stored)).isTrue();
        }

        @Test
        @DisplayName("중복된 employeeCode로 등록하면 EMPLOYEE_ALREADY_EXISTS")
        void duplicateEmployeeCode() {
            EmployeeSaveRequest request = sampleRequest(null);
            BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001")).willReturn(true);

            assertThatThrownBy(() -> employeeService.registerEmployee(request))
                    .isInstanceOf(EmployeeAlreadyExistsException.class)
                    .hasMessageContaining("EMP001");
            verify(employeeRepository, never()).save(any(Employee.class));
        }

        @Test
        @DisplayName("중복된 email로 등록하면 EMPLOYEE_ALREADY_EXISTS")
        void duplicateEmail() {
            EmployeeSaveRequest request = sampleRequest(null);
            BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001")).willReturn(false);
            BDDMockito.given(employeeRepository.existsByEmail("hyungjunn@company.com")).willReturn(true);

            assertThatThrownBy(() -> employeeService.registerEmployee(request))
                    .isInstanceOf(EmployeeAlreadyExistsException.class)
                    .hasMessageContaining("hyungjunn@company.com");
            verify(employeeRepository, never()).save(any(Employee.class));
        }

        @Test
        @DisplayName("동시성 race로 DataIntegrityViolation이 나면 EMPLOYEE_ALREADY_EXISTS로 변환된다")
        void raceConvertedToDomainException() {
            EmployeeSaveRequest request = sampleRequest(null);
            BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001"))
                    .willReturn(false)
                    .willReturn(true);
            BDDMockito.given(employeeRepository.existsByEmail("hyungjunn@company.com")).willReturn(false);
            BDDMockito.given(employeeRepository.save(any(Employee.class)))
                    .willThrow(new DataIntegrityViolationException("uk_employee_code"));

            assertThatThrownBy(() -> employeeService.registerEmployee(request))
                    .isInstanceOf(EmployeeAlreadyExistsException.class)
                    .hasMessageContaining("EMP001");
        }

        @Test
        @DisplayName("존재하지 않는 teamId면 TEAM_NOT_FOUND")
        void teamNotFound() {
            EmployeeSaveRequest request = sampleRequest(99L);
            BDDMockito.given(employeeRepository.existsByEmployeeCode("EMP001")).willReturn(false);
            BDDMockito.given(employeeRepository.existsByEmail("hyungjunn@company.com")).willReturn(false);
            BDDMockito.given(teamRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> employeeService.registerEmployee(request))
                    .isInstanceOf(TeamNotFoundException.class)
                    .hasMessageContaining("99");
            verify(employeeRepository, never()).save(any(Employee.class));
        }
    }

    @Nested
    @DisplayName("changeTeam")
    class ChangeTeam {

        @Test
        @DisplayName("teamId가 있으면 직원의 팀이 해당 팀으로 변경된다")
        void changeToTeam() {
            Team newTeam = new Team(2L, "프론트팀", null, 0);
            BDDMockito.given(employeeRepository.findById(1L)).willReturn(Optional.of(employee));
            BDDMockito.given(teamRepository.findById(2L)).willReturn(Optional.of(newTeam));

            employeeService.changeTeam(1L, 2L);

            assertThat(employee.getTeam()).isEqualTo(newTeam);
        }

        @Test
        @DisplayName("teamId가 null이면 직원이 팀 미배정 상태로 변경된다")
        void changeToUnassigned() {
            BDDMockito.given(employeeRepository.findById(1L)).willReturn(Optional.of(employee));

            employeeService.changeTeam(1L, null);

            assertThat(employee.getTeam()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 employeeId면 EMPLOYEE_NOT_FOUND")
        void employeeNotFound() {
            BDDMockito.given(employeeRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> employeeService.changeTeam(99L, 1L))
                    .isInstanceOf(EmployeeNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("존재하지 않는 teamId로 변경하면 TEAM_NOT_FOUND")
        void teamNotFound() {
            BDDMockito.given(employeeRepository.findById(1L)).willReturn(Optional.of(employee));
            BDDMockito.given(teamRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> employeeService.changeTeam(1L, 99L))
                    .isInstanceOf(TeamNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("changeWorkEndDate")
    class ChangeWorkEndDate {

        @Test
        @DisplayName("퇴사일이 도메인에 반영된다")
        void setsWorkEndDate() {
            BDDMockito.given(employeeRepository.findById(1L)).willReturn(Optional.of(employee));

            employeeService.changeWorkEndDate(1L, LocalDate.of(2026, 7, 31));

            assertThat(employee.getWorkEndDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        }

        @Test
        @DisplayName("null이면 퇴사가 취소된다")
        void clearsWorkEndDateWithNull() {
            employee.changeWorkEndDate(LocalDate.of(2026, 7, 31));
            BDDMockito.given(employeeRepository.findById(1L)).willReturn(Optional.of(employee));

            employeeService.changeWorkEndDate(1L, null);

            assertThat(employee.getWorkEndDate()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 employeeId면 EMPLOYEE_NOT_FOUND")
        void employeeNotFound() {
            BDDMockito.given(employeeRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> employeeService.changeWorkEndDate(99L, LocalDate.of(2026, 7, 31)))
                    .isInstanceOf(EmployeeNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("신청자이거나 지정 승인자인 대기 정정이 있으면 퇴사일을 지정할 수 없다")
        void rejectedWhilePendingCorrectionInvolvesEmployee() {
            BDDMockito.given(employeeRepository.findById(1L)).willReturn(Optional.of(employee));
            BDDMockito.given(correctionRequestRepository.existsByRequesterIdAndStatus(1L, CorrectionStatus.PENDING))
                    .willReturn(false);
            BDDMockito.given(correctionRequestRepository.existsByAssignedApproverIdAndStatus(1L, CorrectionStatus.PENDING))
                    .willReturn(true);

            assertThatThrownBy(() -> employeeService.changeWorkEndDate(1L, LocalDate.of(2026, 7, 31)))
                    .isInstanceOf(CorrectionException.class)
                    .extracting(e -> ((CorrectionException) e).getCode())
                    .isEqualTo(CorrectionErrorCode.PENDING_CORRECTION_EXISTS);
            assertThat(employee.getWorkEndDate()).isNull();
            verify(commuteWriteLock).lockAllEmployees();
        }
    }

    @Nested
    @DisplayName("assignCorrectionApprover")
    class AssignCorrectionApprover {

        private Employee manager;
        private Employee approver;

        @BeforeEach
        void setUp() {
            manager = new EmployeeBuilder().withId(10L).withName("관리자").withRole(Role.MANAGER)
                    .withBirthday(LocalDate.of(1990, 1, 1)).withStartDate(LocalDate.of(2024, 1, 1))
                    .withEmployeeCode("MGR001").withEmail("mgr@company.com").withPassword("password123").build();
            approver = new EmployeeBuilder().withId(20L).withName("상위승인자").withRole(Role.COMMUTE_APPROVER)
                    .withBirthday(LocalDate.of(1990, 1, 1)).withStartDate(LocalDate.of(2024, 1, 1))
                    .withEmployeeCode("APR001").withEmail("apr@company.com").withPassword("password123").build();
        }

        @Test
        @DisplayName("관리자에게 상위 승인자를 지정한다")
        void assignsApproverToManager() {
            BDDMockito.given(employeeRepository.findById(10L)).willReturn(Optional.of(manager));
            BDDMockito.given(employeeRepository.findById(20L)).willReturn(Optional.of(approver));

            employeeService.assignCorrectionApprover(10L, 20L);

            assertThat(manager.getCorrectionApproverId()).isEqualTo(20L);
            verify(commuteWriteLock).lockAllEmployees();
        }

        @Test
        @DisplayName("대상 직원에게 승인 대기 정정이 있으면 담당자를 바꿀 수 없다")
        void rejectedWhilePending() {
            BDDMockito.given(employeeRepository.findById(10L)).willReturn(Optional.of(manager));
            BDDMockito.given(employeeRepository.findById(20L)).willReturn(Optional.of(approver));
            BDDMockito.given(correctionRequestRepository.existsByRequesterIdAndStatus(10L, CorrectionStatus.PENDING))
                    .willReturn(true);

            assertThatThrownBy(() -> employeeService.assignCorrectionApprover(10L, 20L))
                    .isInstanceOf(CorrectionException.class)
                    .extracting(e -> ((CorrectionException) e).getCode())
                    .isEqualTo(CorrectionErrorCode.PENDING_CORRECTION_EXISTS);
            assertThat(manager.getCorrectionApproverId()).isNull();
        }

        @Test
        @DisplayName("본인 지정·같은 역할 지정·MEMBER 대상 지정은 INVALID_CORRECTION_APPROVER")
        void rejectsInvalidPairs() {
            Employee anotherManager = new EmployeeBuilder().withId(11L).withName("관리자2").withRole(Role.MANAGER)
                    .withBirthday(LocalDate.of(1990, 1, 1)).withStartDate(LocalDate.of(2024, 1, 1))
                    .withEmployeeCode("MGR002").withEmail("mgr2@company.com").withPassword("password123").build();

            assertInvalid(() -> manager.assignCorrectionApprover(manager));
            assertInvalid(() -> manager.assignCorrectionApprover(anotherManager));
            assertInvalid(() -> employee.assignCorrectionApprover(manager));
            // 상위 승인자의 담당자는 관리자다
            approver.assignCorrectionApprover(manager);
            assertThat(approver.getCorrectionApproverId()).isEqualTo(10L);
        }

        private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
            assertThatThrownBy(call)
                    .isInstanceOf(CorrectionException.class)
                    .extracting(e -> ((CorrectionException) e).getCode())
                    .isEqualTo(CorrectionErrorCode.INVALID_CORRECTION_APPROVER);
        }
    }

    @Nested
    @DisplayName("findAllEmployee")
    class FindAll {

        @Test
        @DisplayName("findAllWithTeam 결과를 DTO로 매핑하여 반환한다")
        void mapsResponses() {
            BDDMockito.given(employeeRepository.findAllWithTeam()).willReturn(List.of(employee));

            List<EmployeeFindResponse> employees = employeeService.findAllEmployee();

            assertThat(employees).hasSize(1);
            EmployeeFindResponse first = employees.get(0);
            assertThat(first.employeeId()).isEqualTo(1L);
            assertThat(first.teamId()).isEqualTo(1L);
            assertThat(first.teamName()).isEqualTo("백엔드팀");
            assertThat(first.name()).isEqualTo("임형준");
            assertThat(first.role()).isEqualTo("MEMBER");
            assertThat(first.birthday()).isEqualTo(LocalDate.of(1998, 8, 18));
            assertThat(first.workStartDate()).isEqualTo(LocalDate.of(2024, 1, 1));
            assertThat(first.workEndDate()).isNull();
        }
    }

    @Nested
    @DisplayName("authenticate")
    class Authenticate {

        @Test
        @DisplayName("올바른 이메일과 비밀번호로 인증 성공")
        void success() {
            BDDMockito.given(employeeRepository.findByEmail("hyungjunn@company.com"))
                    .willReturn(Optional.of(employee));

            Employee result = employeeService.authenticate("hyungjunn@company.com", "password123");

            assertThat(result).isEqualTo(employee);
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 인증 실패")
        void emailNotFound() {
            BDDMockito.given(employeeRepository.findByEmail("unknown@company.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> employeeService.authenticate("unknown@company.com", "password123"))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessage("존재하지 않는 이메일입니다.");
        }

        @Test
        @DisplayName("잘못된 비밀번호로 인증 실패")
        void wrongPassword() {
            BDDMockito.given(employeeRepository.findByEmail("hyungjunn@company.com"))
                    .willReturn(Optional.of(employee));

            assertThatThrownBy(() -> employeeService.authenticate("hyungjunn@company.com", "wrongpassword"))
                    .isInstanceOf(AuthenticationFailedException.class)
                    .hasMessage("비밀번호가 일치하지 않습니다.");
        }
    }
}
