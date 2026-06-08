package com.company.officecommute.service.commute;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
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
        validateNoOpenCommute(employee.getEmployeeId(), workDate);

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

    public void registerDayOffs(Long employeeId, List<AnnualLeave> savedLeaves, ZoneId zoneId) {
        List<CommuteHistory> commuteHistories = savedLeaves.stream()
                .map(annualLeave -> new CommuteHistory(employeeId, annualLeave.getWantedDate(), zoneId))
                .toList();
        commuteHistoryRepository.saveAll(commuteHistories);
    }

    private Employee getEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
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

    private CommuteHistory findFirstByEmployeeId(Long employeeId) {
        return commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseAndWorkEndTimeIsNullOrderByWorkStartTimeDesc(employeeId)
                .orElseThrow(CommuteNotStartedException::new);
    }

    private List<CommuteHistory> findCommuteHistoriesByEmployeeIdAndMonth(Long employeeId, YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        return commuteHistoryRepository.findAllByEmployeeIdAndWorkDateBetween(
                employeeId, startDate, endDate);
    }
}
