package com.company.officecommute.service.annual_leave;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse;
import com.company.officecommute.repository.annual_leave.AnnualLeaveRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.commute.CommuteHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AnnualLeaveService {

    private static final Logger log = LoggerFactory.getLogger(AnnualLeaveService.class);

    private final EmployeeRepository employeeRepository;
    private final AnnualLeaveRepository annualLeaveRepository;
    private final CommuteHistoryService commuteHistoryService;

    public AnnualLeaveService(
            EmployeeRepository employeeRepository,
            AnnualLeaveRepository annualLeaveRepository,
            CommuteHistoryService commuteHistoryService) {
        this.employeeRepository = employeeRepository;
        this.annualLeaveRepository = annualLeaveRepository;
        this.commuteHistoryService = commuteHistoryService;
    }

    @Transactional
    public List<AnnualLeaveEnrollmentResponse> enrollAnnualLeave(Long employeeId, List<LocalDate> wantedDates) {
        log.info("연차 신청 시작 - employeeId: {}", employeeId);
        Employee employee = employeeRepository.findByEmployeeIdWithTeam(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        List<AnnualLeave> existingAnnualLeaves = annualLeaveRepository.findByEmployeeId(employeeId);
        List<AnnualLeave> enrolledLeaves = employee.enrollAnnualLeave(wantedDates, existingAnnualLeaves);
        List<AnnualLeave> savedLeaves = annualLeaveRepository.saveAll(enrolledLeaves);

        commuteHistoryService.registerDayOffs(employeeId, savedLeaves, employee.getZoneId());

        log.info("연차 신청 완료 - employeeId: {}, 신청한 연차 수: {}", employeeId, savedLeaves.size());
        return AnnualLeaveEnrollmentResponse.listFrom(savedLeaves);
    }

    @Transactional(readOnly = true)
    public AnnualLeaveGetRemainingResponse getRemainingAnnualLeaves(Long employeeId) {
        List<AnnualLeave> remainingLeaves = annualLeaveRepository.findByEmployeeId(employeeId)
                .stream()
                .filter(AnnualLeave::isRemain)
                .toList();

        return new AnnualLeaveGetRemainingResponse(employeeId, remainingLeaves);
    }
}
