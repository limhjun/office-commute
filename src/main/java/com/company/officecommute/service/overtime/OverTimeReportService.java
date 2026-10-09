package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.report.ReportFinality;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@Service
public class OverTimeReportService {

    // TODO: 통상시급은 본래 직원별 속성. 현재 전 직원 동일값으로 단순화.
    // (가산 전 값. 가산율은 아래 MULTIPLIER 로 별도 적용)
    private static final long HOURLY_ORDINARY_WAGE = 15000;
    private static final BigDecimal OVERTIME_MULTIPLIER = new BigDecimal("1.5"); // 연장근로·휴일근로 8h 이내 가산
    private static final BigDecimal HOLIDAY_EXCESS_MULTIPLIER = new BigDecimal("2.0"); // 휴일근로 8h 초과 가산

    // 조회 순서(DB 반환 순서)는 보장되지 않는다. 매달 같은 순서로 나와야 전월 리포트와 나란히 비교할 수 있다.
    // 사번은 unique 라 동점자가 남지 않는 전순서(total order)가 된다.
    private static final Comparator<OverTimeReportData> REPORT_ORDER =
            Comparator.comparing(OverTimeReportData::teamName)
                    .thenComparing(OverTimeReportData::employeeName)
                    .thenComparing(OverTimeReportData::employeeCode);

    private final OverTimeService overTimeService;
    private final OverTimeExcelWriter overTimeExcelWriter;

    public OverTimeReportService(
            OverTimeService overTimeService,
            OverTimeExcelWriter overTimeExcelWriter
    ) {
        this.overTimeService = overTimeService;
        this.overTimeExcelWriter = overTimeExcelWriter;
    }

    public void generateExcelReport(YearMonth yearMonth, OutputStream outputStream) throws IOException {
        writeExcelReport(generateReport(yearMonth), outputStream);
    }

    public void writeExcelReport(OverTimeReport report, OutputStream outputStream) throws IOException {
        overTimeExcelWriter.write(report, outputStream);
    }

    public OverTimeReport generateReport(YearMonth yearMonth) {
        return generateReportSnapshot(yearMonth).report();
    }

    /**
     * 발송 판단과 관리자 경고에 쓰는 미마감 정보를 한 번만 조회한다. 건수는 이 목록에서
     * 도출하므로 경고 제목/본문/첨부 리포트와 상세 목록이 항상 같은 스냅샷을 가리킨다.
     * <p>
     * 미마감을 집계보다 <b>먼저</b> 읽는다. 대상 월(과거)의 기록은 이후 마감될 수만 있고 새로
     * 열리지 않으므로, 집계가 미마감 행을 봤다면 이 목록에도 반드시 있다 — "게이트는 통과했는데
     * 리포트는 과소 집계"인 조합이 나오지 않는다. 순서를 뒤집으면 두 읽기 사이의 마감이 그 조합을 만든다.
     */
    public OverTimeReportSnapshot generateReportSnapshot(YearMonth yearMonth) {
        return generateReportSnapshot(yearMonth, ReportFinality.REFERENCE);
    }

    /**
     * {@code finality}는 파일에 찍힐 성격일 뿐 집계 방식은 같다. 확정본·정정본은 월 마감 뒤에만 만들어지므로
     * 입력 기간이 잠겨 있어 읽는 순서와 무관하게 같은 값이 나온다.
     */
    public OverTimeReportSnapshot generateReportSnapshot(YearMonth yearMonth, ReportFinality finality) {
        List<UnclosedCommute> unclosedCommutes = overTimeService.findUnclosedCommutes(yearMonth);
        long pendingCorrectionCount = overTimeService.countPendingCorrections(yearMonth);
        List<OverTimeCalculateResponse> overTimeData = overTimeService.calculateOverTime(yearMonth);

        OverTimeReport report = createReport(
                yearMonth, overTimeData, unclosedCommutes.size(), pendingCorrectionCount, finality);
        return new OverTimeReportSnapshot(report, unclosedCommutes);
    }

    private OverTimeReport createReport(
            YearMonth yearMonth,
            List<OverTimeCalculateResponse> overTimeData,
            long unclosedCommuteCount,
            long pendingCorrectionCount,
            ReportFinality finality
    ) {

        List<OverTimeReportData> rows = overTimeData.stream()
                .map(this::convertToReportData)
                .sorted(REPORT_ORDER)
                .toList();

        return new OverTimeReport(yearMonth, rows, unclosedCommuteCount, pendingCorrectionCount, finality);
    }

    private OverTimeReportData convertToReportData(OverTimeCalculateResponse response) {
        return new OverTimeReportData(
                response.employeeCode(),
                response.name(),
                response.teamName(),
                response.overTimeMinutes(),
                response.holidayWithin8HoursMinutes(),
                response.holidayExceeding8HoursMinutes(),
                calculateOverTimePay(response)
        );
    }

    // 반올림은 합산 후 한 번만 — 트랙별로 먼저 반올림하면 분 단위 비례가 어긋난다
    private long calculateOverTimePay(OverTimeCalculateResponse response) {
        BigDecimal multipliedMinutes = BigDecimal.valueOf(response.overTimeMinutes() + response.holidayWithin8HoursMinutes())
                .multiply(OVERTIME_MULTIPLIER)
                .add(BigDecimal.valueOf(response.holidayExceeding8HoursMinutes()).multiply(HOLIDAY_EXCESS_MULTIPLIER));
        return multipliedMinutes
                .multiply(BigDecimal.valueOf(HOURLY_ORDINARY_WAGE))
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
