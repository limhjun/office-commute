package com.company.officecommute.dto.report.response;

import com.company.officecommute.domain.report.DispatchStatus;
import com.company.officecommute.domain.report.ReportDispatch;
import com.company.officecommute.domain.report.ReportKind;
import com.company.officecommute.dto.employee.response.EmployeeRef;

import java.time.Instant;
import java.time.YearMonth;

/**
 * 발송 상태. 관리자가 미마감(보류)과 실제 발송 장애, 수신 불명을 구분해 조치할 수 있도록
 * 현재 상태와 보관 파일 여부를 그대로 돌려준다.
 */
public record OverTimeReportDispatchResponse(
        YearMonth yearMonth,
        ReportKind kind,
        DispatchStatus status,
        int attemptCount,
        Instant lastAttemptedAt,
        Instant sentAt,
        String lastFailureReason,
        boolean finalFileAvailable,
        EmployeeRef deliveryConfirmedBy,
        Instant deliveryConfirmedAt,
        String deliveryConfirmationNote
) {
    public static OverTimeReportDispatchResponse from(
            ReportDispatch dispatch,
            boolean finalFileAvailable,
            EmployeeRef deliveryConfirmedBy
    ) {
        return new OverTimeReportDispatchResponse(
                dispatch.getTargetYearMonth(),
                dispatch.getKind(),
                dispatch.getStatus(),
                dispatch.getAttemptCount(),
                dispatch.getLastAttemptedAt(),
                dispatch.getSentAt(),
                dispatch.getLastFailureReason(),
                finalFileAvailable,
                deliveryConfirmedBy,
                dispatch.getDeliveryConfirmedAt(),
                dispatch.getDeliveryConfirmationNote()
        );
    }
}
