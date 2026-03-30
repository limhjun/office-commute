package com.company.officecommute.controller.overtime;

import com.company.officecommute.auth.ManagerOnly;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.YearMonth;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
public class OverTimeController {

    private final OverTimeService overTimeService;
    private final OverTimeReportService overTimeReportService;

    public OverTimeController(OverTimeService overTimeService, OverTimeReportService overTimeReportService) {
        this.overTimeService = overTimeService;
        this.overTimeReportService = overTimeReportService;
    }

    @ManagerOnly
    @GetMapping("/overtime")
    public List<OverTimeCalculateResponse> calculateOverTime(@RequestParam YearMonth yearMonth) {
        return overTimeService.calculateOverTime(yearMonth);
    }

    @ManagerOnly
    @GetMapping("/overtime/report/excel")
    public ResponseEntity<byte[]> downloadOverTimeReport(@RequestParam YearMonth yearMonth) throws IOException {
        byte[] excelData = overTimeReportService.generateExcelReport(yearMonth);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(yearMonth.getYear() + "년" + yearMonth.getMonthValue() + "월_초과근무보고서.xlsx", UTF_8).build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }
}
