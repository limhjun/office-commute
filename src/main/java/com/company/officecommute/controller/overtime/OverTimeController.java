package com.company.officecommute.controller.overtime;

import com.company.officecommute.auth.ManagerOnly;
import com.company.officecommute.dto.overtime.response.HolidayCacheStatusResponse;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.service.overtime.HolidayCacheStatusService;
import com.company.officecommute.service.overtime.HolidaySyncService;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.YearMonth;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
public class OverTimeController {

    private final OverTimeService overTimeService;
    private final OverTimeReportService overTimeReportService;
    private final HolidayCacheStatusService holidayCacheStatusService;
    private final HolidaySyncService holidaySyncService;

    public OverTimeController(
            OverTimeService overTimeService,
            OverTimeReportService overTimeReportService,
            HolidayCacheStatusService holidayCacheStatusService,
            HolidaySyncService holidaySyncService
    ) {
        this.overTimeService = overTimeService;
        this.overTimeReportService = overTimeReportService;
        this.holidayCacheStatusService = holidayCacheStatusService;
        this.holidaySyncService = holidaySyncService;
    }

    @ManagerOnly
    @GetMapping("/overtime")
    public List<OverTimeCalculateResponse> calculateOverTime(@RequestParam YearMonth yearMonth) {
        return overTimeService.calculateOverTime(yearMonth);
    }

    @ManagerOnly
    @GetMapping("/overtime/holiday-status")
    public HolidayCacheStatusResponse getHolidayStatus(@RequestParam YearMonth yearMonth) {
        return holidayCacheStatusService.getStatus(yearMonth);
    }

    @ManagerOnly
    @PostMapping("/overtime/holiday-sync")
    public HolidayCacheStatusResponse syncHoliday(@RequestParam YearMonth yearMonth) {
        return holidaySyncService.refreshAndGetStatus(yearMonth);
    }

    @ManagerOnly
    @GetMapping("/overtime/report/excel")
    public ResponseEntity<StreamingResponseBody> downloadOverTimeReport(@RequestParam YearMonth yearMonth) {
        StreamingResponseBody body = outputStream ->
                overTimeReportService.generateExcelReport(yearMonth, outputStream);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(yearMonth.getYear() + "년" + yearMonth.getMonthValue() + "월_초과근무보고서.xlsx", UTF_8).build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }
}
