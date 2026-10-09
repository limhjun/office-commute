package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.repository.commute.CommuteHistoryRepository;
import com.company.officecommute.repository.correction.CommuteCorrectionRequestRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OverTimeService {

    private final CommuteHistoryRepository commuteHistoryRepository;
    private final OverTimeSnapshotReader overTimeSnapshotReader;
    private final HolidayCalendar holidayCalendar;
    private final CommuteCorrectionRequestRepository correctionRequestRepository;

    public OverTimeService(
            CommuteHistoryRepository commuteHistoryRepository,
            OverTimeSnapshotReader overTimeSnapshotReader,
            HolidayCalendar holidayCalendar,
            CommuteCorrectionRequestRepository correctionRequestRepository
    ) {
        this.commuteHistoryRepository = commuteHistoryRepository;
        this.overTimeSnapshotReader = overTimeSnapshotReader;
        this.holidayCalendar = holidayCalendar;
        this.correctionRequestRepository = correctionRequestRepository;
    }

    /**
     * 집계 입력 범위의 승인 대기 정정 건수. 대기 중인 신청 값은 집계에 섞지 않으므로, 참고용 리포트가
     * 과대·과소 근무 분을 포함할 수 있음을 드러내는 신호로만 쓴다.
     */
    public long countPendingCorrections(YearMonth yearMonth) {
        OverTimePeriod period = new OverTimePeriod(yearMonth);
        return correctionRequestRepository.countByStatusAndWorkDateBetween(
                CorrectionStatus.PENDING, period.rangeStart(), period.rangeEnd());
    }

    /**
     * {@link #calculateOverTime}이 소비하는 범위({@link OverTimePeriod})에서 퇴근이 찍히지
     * 않은 기록 목록. 비어 있으면 미마감으로 인한 과소 집계가 없음이 보장된다.
     * 전월 말 스필오버 일자도 첫 주의 40시간 판정에 0분으로 들어가므로 검사 범위에 포함해야 한다.
     * <p>
     * 건수가 필요한 곳도 이 목록의 크기를 쓴다 — 별도 건수 쿼리를 두면 범위·조인 의미가 어긋날 수 있다.
     */
    public List<UnclosedCommute> findUnclosedCommutes(YearMonth yearMonth) {
        OverTimePeriod period = new OverTimePeriod(yearMonth);
        return commuteHistoryRepository.findUnclosedByWorkDateBetween(period.rangeStart(), period.rangeEnd());
    }

    /**
     * 공휴일 조회({@link HolidayCalendar})는 외부 호출일 수 있어 <b>읽기 트랜잭션 밖</b>에 둔다.
     * DB 스냅샷 안에 넣으면 외부 응답 시간만큼 커넥션을 붙잡고, 그 API 가 느려지는 날
     * 커넥션 풀이 먼저 마른다. 두 DB 조회의 일관성은
     * {@link OverTimeSnapshotReader}가 하나의 읽기 트랜잭션으로 보장한다.
     */
    public List<OverTimeCalculateResponse> calculateOverTime(YearMonth yearMonth) {
        OverTimePeriod period = new OverTimePeriod(yearMonth);
        Set<LocalDate> holidays = holidayCalendar.findHolidays(period);
        OverTimeSnapshot snapshot = overTimeSnapshotReader.read(period);
        Map<Long, Map<LocalDate, Long>> workingMinutesByEmployee =
                groupWorkingMinutesByEmployee(snapshot.dailyWorkingMinutes());

        return snapshot.employees().stream()
                .map(employee -> calculateOverTimeResponse(
                        employee, period, workingMinutesByEmployee, holidays
                ))
                .toList();
    }

    private static OverTimeCalculateResponse calculateOverTimeResponse(Employee employee, OverTimePeriod period, Map<Long, Map<LocalDate, Long>> workingMinutesByEmployee, Set<LocalDate> holidays) {
        MonthlyOverTime overTime = MonthlyOverTimeCalculator.calculate(
                period,
                workingMinutesByEmployee.getOrDefault(employee.getEmployeeId(), Map.of()),
                holidays
        );
        return OverTimeCalculateResponse.from(employee, overTime);
    }

    private static Map<Long, Map<LocalDate, Long>> groupWorkingMinutesByEmployee(
            List<DailyWorkingMinutes> dailyWorkingMinutes) {
        return dailyWorkingMinutes.stream()
                .collect(Collectors.groupingBy(
                        DailyWorkingMinutes::employeeId,
                        Collectors.toMap(DailyWorkingMinutes::workDate, DailyWorkingMinutes::workingMinutes)
                ));
    }
}
