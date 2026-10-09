package com.company.officecommute.service.annual_leave;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaveDuplicateException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse;
import com.company.officecommute.repository.annual_leave.AnnualLeaveRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.commute.CommuteHistoryService;
import com.company.officecommute.service.commute.CommuteWriteLock;
import com.company.officecommute.domain.employee.EmployeeBuilder;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.company.officecommute.domain.employee.Role.MEMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private CommuteHistoryService commuteHistoryService;

    @Mock
    private CommuteWriteLock commuteWriteLock;

    private Long employeeId;
    private Employee employee;

    @BeforeEach
    void setUp() {
        employeeId = 1L;
        Team team = Team.register("백엔드팀", null, 0);
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
        BDDMockito.given(annualLeaveRepository.saveAllAndFlush(any()))
                .willReturn(savedLeaves);

        List<AnnualLeaveEnrollmentResponse> responses = annualLeaveService.enrollAnnualLeave(employeeId, wantedDates);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).annualLeaveId()).isEqualTo(1L);
        assertThat(responses.get(0).enrolledDate()).isEqualTo(wantedDates.get(0));
        verify(commuteHistoryService, times(1))
                .registerDayOffs(eq(employeeId), eq(savedLeaves), eq(employee.getZoneId()));
    }

    @Test
    @DisplayName("연차 저장 중 중복 제약 위반을 AnnualLeaveDuplicateException으로 변환한다")
    void translatesDuplicateConstraintViolation() {
        DataIntegrityViolationException violation = constraintViolation(
                "PUBLIC.UK_ANNUAL_LEAVE_EMPLOYEE_DATE_INDEX_C");
        givenEmployeeWithoutAnnualLeaves();
        BDDMockito.given(annualLeaveRepository.saveAllAndFlush(any()))
                .willThrow(violation);

        assertThatThrownBy(() -> annualLeaveService.enrollAnnualLeave(
                employeeId, List.of(LocalDate.of(2099, 7, 13))))
                .isInstanceOf(AnnualLeaveDuplicateException.class)
                .hasCause(violation);
    }

    @Test
    @DisplayName("MySQL이 테이블명과 함께 보고한 중복 제약도 AnnualLeaveDuplicateException으로 변환한다")
    void translatesMySqlDuplicateConstraintViolation() {
        DataIntegrityViolationException violation = constraintViolation(
                "annual_leave.uk_annual_leave_employee_date");
        givenEmployeeWithoutAnnualLeaves();
        BDDMockito.given(annualLeaveRepository.saveAllAndFlush(any()))
                .willThrow(violation);

        assertThatThrownBy(() -> annualLeaveService.enrollAnnualLeave(
                employeeId, List.of(LocalDate.of(2099, 7, 13))))
                .isInstanceOf(AnnualLeaveDuplicateException.class)
                .hasCause(violation);
    }

    @Test
    @DisplayName("연차 저장 중 다른 제약 위반은 원본 예외를 그대로 던진다")
    void rethrowsUnrelatedConstraintViolation() {
        DataIntegrityViolationException violation = constraintViolation("uk_other_constraint");
        givenEmployeeWithoutAnnualLeaves();
        BDDMockito.given(annualLeaveRepository.saveAllAndFlush(any()))
                .willThrow(violation);

        assertThatThrownBy(() -> annualLeaveService.enrollAnnualLeave(
                employeeId, List.of(LocalDate.of(2099, 7, 13))))
                .isSameAs(violation);
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

    private void givenEmployeeWithoutAnnualLeaves() {
        BDDMockito.given(employeeRepository.findByEmployeeIdWithTeam(employeeId))
                .willReturn(Optional.of(employee));
        BDDMockito.given(annualLeaveRepository.findByEmployeeId(employeeId))
                .willReturn(List.of());
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "constraint violation",
                new ConstraintViolationException("constraint violation", null, constraintName)
        );
    }
}
