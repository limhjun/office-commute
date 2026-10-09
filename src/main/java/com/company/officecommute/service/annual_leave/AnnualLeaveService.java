package com.company.officecommute.service.annual_leave;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.annual_leave.AnnualLeaveDuplicateException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse.RemainingLeave;
import com.company.officecommute.global.persistence.DatabaseConstraintMatcher;
import com.company.officecommute.repository.annual_leave.AnnualLeaveRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.commute.CommuteHistoryService;
import com.company.officecommute.service.commute.CommuteWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AnnualLeaveService {

    private static final String UK_ANNUAL_LEAVE_EMPLOYEE_DATE = "uk_annual_leave_employee_date";
    private static final Logger log = LoggerFactory.getLogger(AnnualLeaveService.class);

    private final EmployeeRepository employeeRepository;
    private final AnnualLeaveRepository annualLeaveRepository;
    private final CommuteHistoryService commuteHistoryService;
    private final CommuteWriteLock commuteWriteLock;

    public AnnualLeaveService(
            EmployeeRepository employeeRepository,
            AnnualLeaveRepository annualLeaveRepository,
            CommuteHistoryService commuteHistoryService,
            CommuteWriteLock commuteWriteLock) {
        this.employeeRepository = employeeRepository;
        this.annualLeaveRepository = annualLeaveRepository;
        this.commuteHistoryService = commuteHistoryService;
        this.commuteWriteLock = commuteWriteLock;
    }

    @Transactional
    public List<AnnualLeaveEnrollmentResponse> enrollAnnualLeave(Long employeeId, List<LocalDate> wantedDates) {
        log.info("연차 신청 시작 - employeeId: {}", employeeId);
        // 연차도 근태 행을 만든다 — 월 마감과 직렬화되도록 다른 근태 쓰기와 같은 잠금을 먼저 잡는다.
        commuteWriteLock.lockEmployee(employeeId);
        Employee employee = employeeRepository.findByEmployeeIdWithTeam(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        List<AnnualLeave> existingAnnualLeaves = annualLeaveRepository.findByEmployeeId(employeeId);
        List<AnnualLeave> enrolledLeaves = employee.enrollAnnualLeave(wantedDates, existingAnnualLeaves);
        List<AnnualLeave> savedLeaves = saveAnnualLeaves(enrolledLeaves);

        commuteHistoryService.registerDayOffs(employeeId, savedLeaves, employee.getZoneId());

        log.info("연차 신청 완료 - employeeId: {}, 신청한 연차 수: {}", employeeId, savedLeaves.size());
        return AnnualLeaveEnrollmentResponse.listFrom(savedLeaves);
    }

    private List<AnnualLeave> saveAnnualLeaves(List<AnnualLeave> annualLeaves) {
        try {
            return annualLeaveRepository.saveAllAndFlush(annualLeaves);
        } catch (DataIntegrityViolationException e) {
            if (DatabaseConstraintMatcher.matches(e, UK_ANNUAL_LEAVE_EMPLOYEE_DATE)) {
                throw new AnnualLeaveDuplicateException(e);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public AnnualLeaveGetRemainingResponse getRemainingAnnualLeaves(Long employeeId) {
        List<RemainingLeave> remainingLeaves = annualLeaveRepository.findByEmployeeId(employeeId)
                .stream()
                .filter(AnnualLeave::isRemain)
                .map(RemainingLeave::from)
                .toList();

        return new AnnualLeaveGetRemainingResponse(employeeId, remainingLeaves);
    }
}
