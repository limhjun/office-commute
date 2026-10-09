package com.company.officecommute.controller.correction;

import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.dto.correction.request.CorrectionApproveRequest;
import com.company.officecommute.dto.correction.request.CorrectionCreateRequest;
import com.company.officecommute.dto.correction.request.CorrectionRejectRequest;
import com.company.officecommute.dto.correction.response.CorrectionRequestResponse;
import com.company.officecommute.service.correction.CommuteCorrectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 퇴근 시각 정정. 권한은 역할 애너테이션이 아니라 서비스가 요청자·처리자·지정 관계로 판단한다 —
 * 같은 역할이라도 대상에 따라 처리 가능 여부가 갈린다.
 */
@RestController
@RequestMapping("/api/commute-corrections")
public class CommuteCorrectionController {

    private final CommuteCorrectionService commuteCorrectionService;

    public CommuteCorrectionController(CommuteCorrectionService commuteCorrectionService) {
        this.commuteCorrectionService = commuteCorrectionService;
    }

    @PostMapping
    public ResponseEntity<CorrectionRequestResponse> submit(
            @RequestAttribute("currentEmployeeId") Long employeeId,
            @Valid @RequestBody CorrectionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commuteCorrectionService.submit(employeeId, request));
    }

    @GetMapping("/mine")
    public List<CorrectionRequestResponse> findMine(
            @RequestAttribute("currentEmployeeId") Long employeeId,
            @RequestParam(required = false) CorrectionStatus status
    ) {
        return commuteCorrectionService.findMine(employeeId, status);
    }

    @GetMapping("/review")
    public List<CorrectionRequestResponse> findReviewScope(
            @RequestAttribute("currentEmployeeId") Long employeeId,
            @RequestParam(required = false) CorrectionStatus status
    ) {
        return commuteCorrectionService.findReviewScope(employeeId, status);
    }

    @GetMapping("/{requestId}")
    public CorrectionRequestResponse findOne(
            @RequestAttribute("currentEmployeeId") Long employeeId,
            @PathVariable Long requestId
    ) {
        return commuteCorrectionService.findOne(employeeId, requestId);
    }

    @PostMapping("/{requestId}/cancel")
    public CorrectionRequestResponse cancel(
            @RequestAttribute("currentEmployeeId") Long employeeId,
            @PathVariable Long requestId
    ) {
        return commuteCorrectionService.cancel(employeeId, requestId);
    }

    @PostMapping("/{requestId}/approve")
    public CorrectionRequestResponse approve(
            @RequestAttribute("currentEmployeeId") Long employeeId,
            @PathVariable Long requestId,
            @Valid @RequestBody(required = false) CorrectionApproveRequest request
    ) {
        String comment = request == null ? null : request.comment();
        return commuteCorrectionService.approve(employeeId, requestId, comment);
    }

    @PostMapping("/{requestId}/reject")
    public CorrectionRequestResponse reject(
            @RequestAttribute("currentEmployeeId") Long employeeId,
            @PathVariable Long requestId,
            @Valid @RequestBody CorrectionRejectRequest request
    ) {
        return commuteCorrectionService.reject(employeeId, requestId, request.reason());
    }
}
