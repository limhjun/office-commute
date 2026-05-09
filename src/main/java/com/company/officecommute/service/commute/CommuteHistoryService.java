package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class CommuteHistoryService {

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeRepository employeeRepository;

    public CommuteHistoryService(
            CommuteHistoryRepository commuteHistoryRepository,
            EmployeeRepository employeeRepository
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public void registerWorkStartTime(Long employeeId) {
        Employee employee = getEmployee(employeeId);
        validatePreviousWorkCompleted(employee.getEmployeeId());
        CommuteHistory newWork = new CommuteHistory(null, employee.getEmployeeId(), ZonedDateTime.now(), null, 0);
        commuteHistoryRepository.save(newWork);
    }

    @Transactional
    public void registerWorkEndTime(Long employeeId, ZonedDateTime workEndTime) {
        Employee employee = getEmployee(employeeId);
        CommuteHistory lastCommute = findFirstByEmployeeId(employee.getEmployeeId());
        CommuteHistory commuteHistory = lastCommute.endWork(workEndTime);
        commuteHistoryRepository.save(commuteHistory);
    }

    @Transactional(readOnly = true)
    public WorkDurationPerDateResponse getWorkDurationPerDate(Long employeeId, YearMonth yearMonth) {
        Employee employee = getEmployee(employeeId);
        List<CommuteHistory> histories = findCommuteHistoriesByEmployeeIdAndMonth(
                employee.getEmployeeId(), yearMonth);
        return new CommuteHistories(histories).toWorkDurationPerDateResponse();
    }

    private Employee getEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직원입니다."));
    }

    private void validatePreviousWorkCompleted(Long employeeId) {
        commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(employeeId)
                .ifPresent(commuteHistory -> {
                    throw new IllegalStateException("이전 근무가 아직 종료되지 않았습니다.");
                });
    }

    private CommuteHistory findFirstByEmployeeId(Long employeeId) {
        return commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("출근 기록이 없습니다."));
    }

    private List<CommuteHistory> findCommuteHistoriesByEmployeeIdAndMonth(Long employeeId, YearMonth yearMonth) {
        ZonedDateTime startOfMonth = yearMonth.atDay(1).atStartOfDay(ZonedDateTime.now().getZone());
        ZonedDateTime endOfMonth = yearMonth.atEndOfMonth()
                .atTime(23, 59, 59)
                .atZone(ZonedDateTime.now().getZone());
        return commuteHistoryRepository.findAllByEmployeeIdAndWorkStartTimeBetween(
                employeeId, startOfMonth, endOfMonth);
    }
}
