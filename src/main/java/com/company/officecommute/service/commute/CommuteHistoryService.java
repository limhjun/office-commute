package com.company.officecommute.service.commute;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.commute.CommuteAlreadyEndedException;
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
        ZonedDateTime workStartTime = ZonedDateTime.now(clock.withZone(employeeZone));
        CommuteHistory newCommute = CommuteHistory.registerWorkStart(
                employee.getEmployeeId(), workStartTime, employeeZone);

        validateCanRegisterWorkStart(employee.getEmployeeId(), newCommute.getWorkDate());
        saveCommuteHistory(newCommute);
    }

    private void validateCanRegisterWorkStart(Long employeeId, LocalDate workDate) {
        validateNoWorkOnDate(employeeId, workDate);
        validateNoOpenCommute(employeeId, workDate);
    }

    private void validateNoWorkOnDate(Long employeeId, LocalDate workDate) {
        if (commuteHistoryRepository.existsByEmployeeIdAndWorkDate(employeeId, workDate)) {
            throw new DuplicateWorkOnDateException(workDate);
        }
    }

    private void validateNoOpenCommute(Long employeeId, LocalDate currentWorkDate) {
        commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(employeeId)
                .ifPresent(openCommute -> {
                    // race net: existsBy 통과 후 다른 thread가 같은 날 commit한 경우, open commute의 workDate가 오늘과 일치한다.
                    if (currentWorkDate.equals(openCommute.getWorkDate())) {
                        throw new DuplicateWorkOnDateException(currentWorkDate);
                    }
                    throw new PreviousCommuteNotEndedException();
                });
    }

    private void saveCommuteHistory(CommuteHistory commuteHistory) {
        try {
            commuteHistoryRepository.saveAndFlush(commuteHistory);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateWorkConstraint(e)) {
                throw new DuplicateWorkOnDateException(commuteHistory.getWorkDate(), e);
            }
            throw e;
        }
    }

    @Transactional
    public void registerWorkEndTime(Long employeeId) {
        Employee employee = getEmployee(employeeId);
        CommuteHistory lastCommute = findFirstByEmployeeId(employee.getEmployeeId());
        ZoneId workZone = lastCommute.getWorkZoneId();
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(workZone));
        long workingMinutes = lastCommute.calculateWorkingMinutes(now);
        int updated = commuteHistoryRepository.updateWorkEndTimeIfOpen(
                lastCommute.getCommuteHistoryId(), now, workingMinutes);
        if (updated == 0) {
            // race net: 조회 후 다른 요청이 먼저 퇴근 처리하면 조건부 update가 0건이 된다.
            throw new CommuteAlreadyEndedException();
        }
    }

    private CommuteHistory findFirstByEmployeeId(Long employeeId) {
        return commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(employeeId)
                .orElseThrow(CommuteNotStartedException::new);
    }

    @Transactional(readOnly = true)
    public WorkDurationPerDateResponse getWorkDurationPerDate(Long employeeId, YearMonth yearMonth) {
        Employee employee = getEmployee(employeeId);
        List<CommuteHistory> histories = findCommuteHistoriesByEmployeeIdAndMonth(
                employee.getEmployeeId(), yearMonth);
        return new CommuteHistories(histories).toWorkDurationPerDateResponse();
    }

    private List<CommuteHistory> findCommuteHistoriesByEmployeeIdAndMonth(Long employeeId, YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        return commuteHistoryRepository.findAllByEmployeeIdAndWorkDateBetween(
                employeeId, startDate, endDate);
    }

    public void registerDayOffs(Long employeeId, List<AnnualLeave> savedLeaves, ZoneId zoneId) {
        List<CommuteHistory> commuteHistories = savedLeaves.stream()
                .map(annualLeave -> CommuteHistory.registerAnnualLeave(employeeId, annualLeave.getWantedDate(), zoneId))
                .toList();
        commuteHistoryRepository.saveAll(commuteHistories);
    }

    private boolean isDuplicateWorkConstraint(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return isCommuteHistoryEmployeeDateConstraint(
                        constraintViolationException.getConstraintName());
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isCommuteHistoryEmployeeDateConstraint(String constraintName) {
        return normalizeConstraintName(constraintName).equalsIgnoreCase(UK_COMMUTE_HISTORY_EMPLOYEE_DATE);
    }

    private String normalizeConstraintName(String constraintName) {
        if (constraintName == null) {
            return "";
        }
        String normalized = constraintName;
        int schemaSeparator = normalized.lastIndexOf('.');
        if (schemaSeparator >= 0) {
            normalized = normalized.substring(schemaSeparator + 1);
        }
        if (normalized.endsWith("_INDEX_C")) {
            normalized = normalized.substring(0, normalized.length() - "_INDEX_C".length());
        }
        return normalized;
    }

    private Employee getEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
