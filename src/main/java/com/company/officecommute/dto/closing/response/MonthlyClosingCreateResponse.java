package com.company.officecommute.dto.closing.response;

import com.company.officecommute.dto.report.response.OverTimeReportDispatchResponse;

public record MonthlyClosingCreateResponse(
        MonthlyClosingResponse closing,
        OverTimeReportDispatchResponse dispatch
) {
}
