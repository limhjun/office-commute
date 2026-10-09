package com.company.officecommute.controller.closing;

import com.company.officecommute.auth.ManagerOnly;
import com.company.officecommute.domain.closing.MonthlyClosingType;
import com.company.officecommute.dto.closing.request.MonthlyClosingCreateRequest;
import com.company.officecommute.dto.closing.response.MonthlyClosingCreateResponse;
import com.company.officecommute.dto.closing.response.MonthlyClosingResponse;
import com.company.officecommute.dto.closing.response.MonthlyClosingStatusResponse;
import com.company.officecommute.dto.report.response.OverTimeReportDispatchResponse;
import com.company.officecommute.service.closing.MonthlyClosingService;
import com.company.officecommute.service.report.OverTimeReportDispatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/monthly-closings")
public class MonthlyClosingController {

    private final MonthlyClosingService monthlyClosingService;
    private final OverTimeReportDispatchService dispatchService;

    public MonthlyClosingController(
            MonthlyClosingService monthlyClosingService,
            OverTimeReportDispatchService dispatchService
    ) {
        this.monthlyClosingService = monthlyClosingService;
        this.dispatchService = dispatchService;
    }

    @ManagerOnly
    @GetMapping
    public List<MonthlyClosingResponse> findAll() {
        return monthlyClosingService.findAll();
    }

    @ManagerOnly
    @GetMapping("/{yearMonth}")
    public MonthlyClosingStatusResponse getStatus(@PathVariable YearMonth yearMonth) {
        return monthlyClosingService.getStatus(yearMonth);
    }

    /**
     * 마감을 먼저 커밋한 뒤 같은 요청에서 발송한다. 발송이 실패해도 마감은 유지되고, 커밋 직후 중단되면
     * 예약 재시도나 수동 발송 API 로 복구한다. 정정 없이 등록한 기존 발송 월은 다시 보내지 않는다.
     */
    @ManagerOnly
    @PostMapping
    public ResponseEntity<MonthlyClosingCreateResponse> close(
            @RequestAttribute("currentEmployeeId") Long employeeId,
            @Valid @RequestBody MonthlyClosingCreateRequest request
    ) {
        MonthlyClosingResponse closing = monthlyClosingService.close(employeeId, request);
        OverTimeReportDispatchResponse dispatch = closing.type() == MonthlyClosingType.LEGACY_CONFIRMED
                ? null
                : dispatchService.dispatchAndDescribe(closing.yearMonth());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MonthlyClosingCreateResponse(closing, dispatch));
    }
}
