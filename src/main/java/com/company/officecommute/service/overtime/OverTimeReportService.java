package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

@Service
public class OverTimeReportService {

    // TODO: 통상시급은 본래 직원별 속성. 현재 전 직원 동일값으로 단순화.
    // (가산 전 값. 연장근로 가산 1.5×는 OVERTIME_MULTIPLIER 로 별도 적용)
    private static final long HOURLY_ORDINARY_WAGE = 15000;
    private static final BigDecimal OVERTIME_MULTIPLIER = new BigDecimal("1.5"); // 근로기준법 연장근로 가산

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
        List<OverTimeReportData> reportData = generateOverTimeReportData(yearMonth);
        writeExcelReport(yearMonth, reportData, outputStream);
    }

    public void writeExcelReport(YearMonth yearMonth, List<OverTimeReportData> reportData, OutputStream outputStream) throws IOException {
        overTimeExcelWriter.write(yearMonth, reportData, outputStream);
    }

    public List<OverTimeReportData> generateOverTimeReportData(YearMonth yearMonth) {
        List<OverTimeCalculateResponse> overTimeData = overTimeService.calculateOverTime(yearMonth);

        return overTimeData.stream()
                .map(this::convertToReportData)
                .toList();
    }

    private OverTimeReportData convertToReportData(OverTimeCalculateResponse response) {
        long overTimePay = BigDecimal.valueOf(response.overTimeMinutes())
                .multiply(BigDecimal.valueOf(HOURLY_ORDINARY_WAGE))
                .multiply(OVERTIME_MULTIPLIER)
                .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP)
                .longValueExact();

        return new OverTimeReportData(
                response.name(),
                response.teamName(),
                response.overTimeMinutes(),
                overTimePay
        );
    }
}
