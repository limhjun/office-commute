package com.company.officecommute.dto.closing.response;

import com.company.officecommute.dto.employee.response.EmployeeRef;
import com.company.officecommute.dto.report.response.OverTimeReportDispatchResponse;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record MonthlyClosingStatusResponse(
        YearMonth yearMonth,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        boolean closable,
        MonthlyClosingResponse closing,
        boolean legacyDispatch,
        List<ClosingBlocker> blockers,
        List<UnresolvedCommute> unclosedCommutes,
        List<PendingCorrectionSummary> pendingCorrections,
        List<OverTimeReportDispatchResponse> dispatches
) {

    public enum ClosingBlocker {
        MONTH_NOT_ENDED,
        ALREADY_CLOSED,
        UNCLOSED_COMMUTES,
        PENDING_CORRECTIONS,
        DELIVERY_UNCERTAIN,
        PREVIOUS_LEGACY_MONTH_PENDING
    }

    public record UnresolvedCommute(Long commuteHistoryId, EmployeeRef employee, LocalDate workDate) {
    }

    public record PendingCorrectionSummary(
            Long requestId,
            Long commuteHistoryId,
            EmployeeRef requester,
            LocalDate workDate
    ) {
    }
}
