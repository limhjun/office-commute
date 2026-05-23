package com.company.officecommute.service.commute;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteNotStartedException;
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.commute.PreviousCommuteNotEndedException;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class CommuteHistoryService {

    private static final String UK_COMMUTE_HISTORY_EMPLOYEE_DATE = "uk_commute_history_employee_date";

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    public CommuteHistoryService(
            CommuteHistoryRepository commuteHistoryRepository,
            EmployeeRepository employeeRepository,
            Clock clock
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.clock = clock;
    }

    @Transactional
    public void registerWorkStartTime(Long employeeId) {
        Employee employee = getEmployee(employeeId);
        ZoneId employeeZone = employee.getZoneId();
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(employeeZone));
        LocalDate workDate = now.toLocalDate();
        if (commuteHistoryRepository.existsByEmployeeIdAndWorkDate(employee.getEmployeeId(), workDate)) {
            throw new DuplicateWorkOnDateException(workDate);
        }
        validatePreviousWorkCompleted(employee.getEmployeeId());

        CommuteHistory newWork = new CommuteHistory(null, employee.getEmployeeId(), now, null, 0, employeeZone);
        try {
            commuteHistoryRepository.saveAndFlush(newWork);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateWorkConstraint(e)) {
                throw new DuplicateWorkOnDateException(workDate, e);
            }
            throw e;
        }
    }

    private boolean isDuplicateWorkConstraint(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                String constraintName = constraintViolationException.getConstraintName();
                return constraintName != null
                        && constraintName.toLowerCase().contains(UK_COMMUTE_HISTORY_EMPLOYEE_DATE);
            }
            current = current.getCause();
        }
        return false;
    }

    @Transactional
    public void registerWorkEndTime(Long employeeId) {
        Employee employee = getEmployee(employeeId);
        CommuteHistory lastCommute = findFirstByEmployeeId(employee.getEmployeeId());
        ZoneId workZone = lastCommute.getWorkZoneId();
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(workZone));
        CommuteHistory commuteHistory = lastCommute.endWork(now);
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
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }

    private void validatePreviousWorkCompleted(Long employeeId) {
        commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(employeeId)
                .ifPresent(commuteHistory -> {
                    throw new PreviousCommuteNotEndedException();
                });
    }

    private CommuteHistory findFirstByEmployeeId(Long employeeId) {
        return commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(employeeId)
                .orElseThrow(CommuteNotStartedException::new);
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
