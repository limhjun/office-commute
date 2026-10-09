package com.company.officecommute.controller.overtime;

import com.company.officecommute.auth.ManagerOnly;
import com.company.officecommute.domain.report.ReportFile;
import com.company.officecommute.domain.report.ReportFinality;
import com.company.officecommute.domain.report.ReportKind;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.report.request.DispatchConfirmationRequest;
import com.company.officecommute.dto.report.response.OverTimeReportDispatchResponse;
import com.company.officecommute.service.overtime.OverTimeReportFileName;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import com.company.officecommute.service.report.OverTimeReportDispatchService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.YearMonth;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping("/api")
public class OverTimeController {

    private final OverTimeService overTimeService;
    private final OverTimeReportService overTimeReportService;
    private final OverTimeReportDispatchService overTimeReportDispatchService;

    public OverTimeController(
            OverTimeService overTimeService,
            OverTimeReportService overTimeReportService,
            OverTimeReportDispatchService overTimeReportDispatchService
    ) {
        this.overTimeService = overTimeService;
        this.overTimeReportService = overTimeReportService;
        this.overTimeReportDispatchService = overTimeReportDispatchService;
    }

    @ManagerOnly
    @GetMapping("/overtime")
    public List<OverTimeCalculateResponse> calculateOverTime(@RequestParam YearMonth yearMonth) {
        return overTimeService.calculateOverTime(yearMonth);
    }

    /**
     * 수동 재실행. 배치·마감 직후 발송과 같은 멱등 경로라 이미 발송된 달에는 아무 일도 일어나지 않는다.
     * 월 마감 전이면 발송하지 않고 MONTH_NOT_CLOSED 로 보류된다.
     */
    @ManagerOnly
    @PostMapping("/overtime/report/dispatch")
    public OverTimeReportDispatchResponse dispatchOverTimeReport(@RequestParam YearMonth yearMonth) {
        return overTimeReportDispatchService.dispatchAndDescribe(yearMonth);
    }

    @ManagerOnly
    @PostMapping("/overtime/report/dispatch/confirmation")
    public OverTimeReportDispatchResponse confirmDelivery(
            @RequestAttribute("currentEmployeeId") Long employeeId,
            @Valid @RequestBody DispatchConfirmationRequest request
    ) {
        return overTimeReportDispatchService.confirmDelivery(
                employeeId, request.yearMonth(), request.kind(), request.outcome(), request.note());
    }

    /**
     * 참고용. 현재 데이터로 매번 다시 집계하므로 마감 여부와 관계없이 확정본이 아니다 — 파일명과 시트 상단에 표시한다.
     */
    @ManagerOnly
    @GetMapping("/overtime/report/excel")
    public ResponseEntity<StreamingResponseBody> downloadOverTimeReport(@RequestParam YearMonth yearMonth) {
        // 스트리밍 시작 전에 집계를 끝낸다. 응답이 커밋된 뒤 실패하면 200 + 깨진 파일이 나간다.
        OverTimeReport report = overTimeReportService.generateReport(yearMonth);
        StreamingResponseBody body = outputStream ->
                overTimeReportService.writeExcelReport(report, outputStream);

        return ResponseEntity.ok()
                .headers(excelHeaders(OverTimeReportFileName.of(yearMonth, ReportFinality.REFERENCE)))
                .body(body);
    }

    /**
     * 확정본. 발송에 쓴(쓸) 보관 파일을 그대로 내려준다 — 직원·팀·공휴일이 바뀌어도 발송본과 같다.
     */
    @ManagerOnly
    @GetMapping("/overtime/report/final-excel")
    public ResponseEntity<byte[]> downloadFinalOverTimeReport(
            @RequestParam YearMonth yearMonth,
            @RequestParam(defaultValue = "ORIGINAL") ReportKind kind
    ) {
        ReportFile file = overTimeReportDispatchService.findFinalFile(yearMonth, kind);
        return ResponseEntity.ok()
                .headers(excelHeaders(file.getFileName()))
                .body(file.getContent());
    }

    private static HttpHeaders excelHeaders(String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName, UTF_8).build());
        return headers;
    }
}
