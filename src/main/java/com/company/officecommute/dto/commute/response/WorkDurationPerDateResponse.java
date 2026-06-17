package com.company.officecommute.dto.commute.response;

import com.company.officecommute.domain.commute.DailyWorkDuration;

import java.util.List;

public record WorkDurationPerDateResponse(
        List<DailyWorkDuration> details,
        long sumWorkingMinutes
) {
}
