package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import com.company.officecommute.web.ApiConvertor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.YearMonth;
import java.util.List;

@Service
public class OverTimeReportService {

    private static final long HOURLY_OVERTIME_PAY = 15000; // 시간당 초과근무 수당 (임시)

    private final OverTimeService overTimeService;
    private final OverTimeExcelWriter overTimeExcelWriter;
    private final ApiConvertor apiConvertor;

    public OverTimeReportService(
            OverTimeService overTimeService,
            OverTimeExcelWriter overTimeExcelWriter,
            ApiConvertor apiConvertor
    ) {
        this.overTimeService = overTimeService;
        this.overTimeExcelWriter = overTimeExcelWriter;
        this.apiConvertor = apiConvertor;
    }

    public void generateExcelReport(YearMonth yearMonth, OutputStream outputStream) throws IOException {
        List<OverTimeReportData> reportData = generateOverTimeReportData(yearMonth);
        overTimeExcelWriter.write(yearMonth, reportData, outputStream);
        apiConvertor.prefetchNextMonthHolidays(yearMonth);
    }

    private List<OverTimeReportData> generateOverTimeReportData(YearMonth yearMonth) {
        List<OverTimeCalculateResponse> overTimeData = overTimeService.calculateOverTime(yearMonth);

        return overTimeData.stream()
                .map(this::convertToReportData)
                .toList();
    }

    private OverTimeReportData convertToReportData(OverTimeCalculateResponse response) {
        long overTimeHours = response.overTimeMinutes() / 60;
        long overTimePay = overTimeHours * HOURLY_OVERTIME_PAY;

        return new OverTimeReportData(
                response.name(),
                response.teamName(),
                response.overTimeMinutes(),
                overTimePay
        );
    }
}
