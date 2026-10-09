package com.company.officecommute.service.employee;

import com.company.officecommute.auth.AuthenticationFailedException;
import com.company.officecommute.domain.correction.CorrectionErrorCode;
import com.company.officecommute.domain.correction.CorrectionException;
import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeAlreadyExistsException;
import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.domain.team.Team;
import com.company.officecommute.domain.team.TeamNotFoundException;
import com.company.officecommute.dto.auth.response.CurrentUserResponse;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.dto.employee.response.EmployeeRegisterResponse;
import com.company.officecommute.repository.correction.CommuteCorrectionRequestRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.repository.team.TeamRepository;
import com.company.officecommute.service.commute.CommuteWriteLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;
    private final CommuteCorrectionRequestRepository correctionRequestRepository;
    private final CommuteWriteLock commuteWriteLock;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            TeamRepository teamRepository,
            PasswordEncoder passwordEncoder,
            CommuteCorrectionRequestRepository correctionRequestRepository,
            CommuteWriteLock commuteWriteLock
    ) {
        this.employeeRepository = employeeRepository;
        this.teamRepository = teamRepository;
        this.passwordEncoder = passwordEncoder;
        this.correctionRequestRepository = correctionRequestRepository;
        this.commuteWriteLock = commuteWriteLock;
    }

    @Transactional
    public EmployeeRegisterResponse registerEmployee(EmployeeSaveRequest request) {
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw EmployeeAlreadyExistsException.ofEmployeeCode(request.employeeCode());
        }
        if (employeeRepository.existsByEmail(request.email())) {
            throw EmployeeAlreadyExistsException.ofEmail(request.email());
        }
        Team team = resolveTeam(request.teamId());
        Employee employee = Employee.register(
                request.name(),
                request.role(),
                request.birthday(),
                request.workStartDate(),
                request.employeeCode(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.timezone(),
                team
        );
        try {
            Employee saved = employeeRepository.save(employee);
            return new EmployeeRegisterResponse(saved.getEmployeeId());
        } catch (DataIntegrityViolationException e) {
            if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
                throw EmployeeAlreadyExistsException.ofEmployeeCode(request.employeeCode());
            }
            throw EmployeeAlreadyExistsException.ofEmail(request.email());
        }
    }

    @Transactional
    public void changeTeam(Long employeeId, Long teamId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        Team team = resolveTeam(teamId);
        employee.changeTeam(team);
    }

    /**
     * 퇴사일 지정은 계정 비활성화로 다룬다. 그 직원이 신청자이거나 지정 승인자인 대기 요청이 있으면 막는다 —
     * 처리할 사람이 사라진 요청이 남지 않게. 퇴사 취소(null)는 막지 않는다.
     */
    @Transactional
    public void changeWorkEndDate(Long employeeId, LocalDate workEndDate) {
        // 대기 요청은 다른 직원 행 잠금 아래에서도 생기므로(이 직원이 승인자인 경우) 전체를 잠근다.
        commuteWriteLock.lockAllEmployees();
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        if (workEndDate != null && hasPendingCorrectionInvolving(employeeId)) {
            throw new CorrectionException(CorrectionErrorCode.PENDING_CORRECTION_EXISTS,
                    "이 직원이 신청했거나 처리해야 하는 승인 대기 정정 요청을 먼저 처리해야 퇴사일을 지정할 수 있습니다.");
        }
        employee.changeWorkEndDate(workEndDate);
    }

    /**
     * 정정 승인 담당자 지정. 대기 요청이 있으면 막는다 — 담당자 변경은 대기 요청 취소 → 변경 → 재신청 순서다.
     */
    @Transactional
    public void assignCorrectionApprover(Long employeeId, Long approverId) {
        commuteWriteLock.lockAllEmployees();
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        Employee approver = approverId == null ? null : employeeRepository.findById(approverId)
                .orElseThrow(() -> new EmployeeNotFoundException(approverId));
        if (correctionRequestRepository.existsByRequesterIdAndStatus(employeeId, CorrectionStatus.PENDING)) {
            throw new CorrectionException(CorrectionErrorCode.PENDING_CORRECTION_EXISTS,
                    "승인 대기 중인 정정 요청이 있어 담당자를 변경할 수 없습니다. 요청을 취소한 뒤 변경하고 다시 신청해 주세요.");
        }
        employee.assignCorrectionApprover(approver);
    }

    private boolean hasPendingCorrectionInvolving(Long employeeId) {
        return correctionRequestRepository.existsByRequesterIdAndStatus(employeeId, CorrectionStatus.PENDING)
                || correctionRequestRepository.existsByAssignedApproverIdAndStatus(employeeId, CorrectionStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<EmployeeFindResponse> findAllEmployee() {
        return employeeRepository.findAllWithTeam()
                .stream()
                .map(EmployeeFindResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long employeeId) {
        Employee employee = employeeRepository.findByEmployeeIdWithTeam(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        return CurrentUserResponse.from(employee);
    }

    public Employee authenticate(String email, String password) {
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("존재하지 않는 이메일입니다."));
        if (!passwordEncoder.matches(password, employee.getPassword())) {
            throw new AuthenticationFailedException("비밀번호가 일치하지 않습니다.");
        }
        return employee;
    }

    private Team resolveTeam(Long teamId) {
        if (teamId == null) {
            return null;
        }
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));
    }
}
