package com.company.officecommute.service.commute;

import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.domain.commute.CommuteAlreadyEndedException;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteNotStartedException;
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.correction.CommuteCorrectionRequest;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.dto.commute.response.CommuteDetailResponse;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.global.persistence.DatabaseConstraintMatcher;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.correction.CommuteCorrectionRequestRepository;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.closing.CommutePeriodGuard;
import com.company.officecommute.service.closing.ProtectedPeriods;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CommuteHistoryService {

    private static final String UK_COMMUTE_HISTORY_EMPLOYEE_DATE = "uk_commute_history_employee_date";

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final CommuteCorrectionRequestRepository correctionRequestRepository;
    private final CommuteWriteLock commuteWriteLock;
    private final CommutePeriodGuard commutePeriodGuard;
    private final Clock clock;

    public CommuteHistoryService(
            CommuteHistoryRepository commuteHistoryRepository,
            EmployeeRepository employeeRepository,
            CommuteCorrectionRequestRepository correctionRequestRepository,
            CommuteWriteLock commuteWriteLock,
            CommutePeriodGuard commutePeriodGuard,
            Clock clock
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.correctionRequestRepository = correctionRequestRepository;
        this.commuteWriteLock = commuteWriteLock;
        this.commutePeriodGuard = commutePeriodGuard;
        this.clock = clock;
    }

    /**
     * 과거 미퇴근이 남아 있어도 새 근무일 출근은 허용한다 — 그 기록은 CORRECTION_REQUIRED 가 되어
     * 정정 신청으로 해결한다. 같은 근무일 재출근은 UNIQUE 제약이 최종 경계다.
     */
    @Transactional
    public void registerWorkStartTime(Long employeeId) {
        commuteWriteLock.lockEmployee(employeeId);
        Employee employee = getEmployee(employeeId);
        Instant workStartTime = clock.instant();
        CommuteHistory newCommute = CommuteHistory.registerWorkStart(
                employee.getEmployeeId(), workStartTime, employee.getZoneId());

        validateNoWorkOnDate(employee.getEmployeeId(), newCommute.getWorkDate());
        // 다른 시간대 직원은 회사 달력으로 이미 마감된 월의 날짜에 출근할 수 있다(예: LA 의 9/30 = KST 10/1).
        commutePeriodGuard.assertWritable(newCommute.getWorkDate());
        saveCommuteHistory(newCommute);
    }

    private void validateNoWorkOnDate(Long employeeId, LocalDate workDate) {
        if (commuteHistoryRepository.existsByEmployeeIdAndWorkDate(employeeId, workDate)) {
            throw new DuplicateWorkOnDateException(workDate);
        }
    }

    private void saveCommuteHistory(CommuteHistory commuteHistory) {
        try {
            commuteHistoryRepository.saveAndFlush(commuteHistory);
        } catch (DataIntegrityViolationException e) {
            if (DatabaseConstraintMatcher.matches(e, UK_COMMUTE_HISTORY_EMPLOYEE_DATE)) {
                throw new DuplicateWorkOnDateException(commuteHistory.getWorkDate(), e);
            }
            throw e;
        }
    }

    /**
     * 일반 퇴근은 가장 최근에 시작한 실제 근무만 대상으로 한다. 그 근무가 이미 끝났으면 거부하고,
     * 이전 미퇴근 기록을 대신 종료하지 않는다.
     */
    @Transactional
    public void registerWorkEndTime(Long employeeId) {
        commuteWriteLock.lockEmployee(employeeId);
        Employee employee = getEmployee(employeeId);
        CommuteHistory latestCommute = commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseOrderByWorkStartTimeDesc(employee.getEmployeeId())
                .orElseThrow(CommuteNotStartedException::new);
        Instant now = clock.instant();
        long workingMinutes = latestCommute.calculateRegularEndMinutes(now);
        commutePeriodGuard.assertWritable(latestCommute.getWorkDate());
        int updated = commuteHistoryRepository.updateWorkEndTimeIfOpen(
                latestCommute.getCommuteHistoryId(), now, workingMinutes);
        if (updated == 0) {
            // race net: 직원 행 잠금이 같은 직원의 퇴근을 직렬화하지만, 조건부 update 가 최종 경계다.
            throw new CommuteAlreadyEndedException();
        }
    }

    @Transactional(readOnly = true)
    public WorkDurationPerDateResponse getWorkDurationPerDate(Long employeeId, YearMonth yearMonth) {
        Employee employee = getEmployee(employeeId);
        CommuteHistories histories = new CommuteHistories(
                findCommuteHistoriesByEmployeeIdAndMonth(employee.getEmployeeId(), yearMonth));

        Instant now = clock.instant();
        // 후속 근무 판정은 조회 월에 한정하지 않는다 — 다음 달 출근도 이 달 미퇴근을 정정 필요로 만든다.
        Instant latestActualWorkStart = commuteHistoryRepository
                .findFirstByEmployeeIdAndUsingDayOffFalseOrderByWorkStartTimeDesc(employee.getEmployeeId())
                .map(CommuteHistory::getWorkStartTime)
                .orElse(null);
        Map<Long, Long> pendingRequestIdByCommuteId = findPendingRequestIds(histories.commuteHistoryIds());
        ProtectedPeriods protectedPeriods = commutePeriodGuard.load();

        return histories.toWorkDurationPerDateResponse(history -> CommuteDetailResponse.of(
                history,
                history.status(now, latestActualWorkStart),
                pendingRequestIdByCommuteId.get(history.getCommuteHistoryId()),
                protectedPeriods.lockReason(history.getWorkDate()).orElse(null)
        ));
    }

    private Map<Long, Long> findPendingRequestIds(List<Long> commuteHistoryIds) {
        if (commuteHistoryIds.isEmpty()) {
            return Map.of();
        }
        return correctionRequestRepository.findAllByPendingCommuteHistoryIdIn(commuteHistoryIds)
                .stream()
                .collect(Collectors.toMap(
                        CommuteCorrectionRequest::getCommuteHistoryId,
                        CommuteCorrectionRequest::getCorrectionRequestId));
    }

    private List<CommuteHistory> findCommuteHistoriesByEmployeeIdAndMonth(Long employeeId, YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        return commuteHistoryRepository.findAllByEmployeeIdAndWorkDateBetween(
                employeeId, startDate, endDate);
    }

    /**
     * 연차 신청 트랜잭션 안에서 불린다. 호출자가 직원 행을 먼저 잠근다.
     */
    public void registerDayOffs(Long employeeId, List<AnnualLeave> savedLeaves, ZoneId zoneId) {
        savedLeaves.forEach(annualLeave -> validateNoWorkOnDate(employeeId, annualLeave.getWantedDate()));
        ProtectedPeriods protectedPeriods = commutePeriodGuard.load();
        savedLeaves.forEach(annualLeave -> protectedPeriods.lockReason(annualLeave.getWantedDate())
                .ifPresent(reason -> {
                    throw reason.toException();
                }));
        savedLeaves.stream()
                .map(annualLeave -> CommuteHistory.registerAnnualLeave(employeeId, annualLeave.getWantedDate(), zoneId))
                .forEach(this::saveCommuteHistory);
    }

    private Employee getEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
