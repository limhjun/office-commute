package com.company.officecommute.dto.commute.response;

import java.util.List;

public record WorkDurationPerDateResponse(
        List<CommuteDetailResponse> details,
        long sumWorkingMinutes,
        RegularEndTargetResponse regularEndTarget
) {
    public WorkDurationPerDateResponse(List<CommuteDetailResponse> details, long sumWorkingMinutes) {
        this(details, sumWorkingMinutes, null);
    }

    public WorkDurationPerDateResponse withRegularEndTarget(RegularEndTargetResponse regularEndTarget) {
        return new WorkDurationPerDateResponse(details, sumWorkingMinutes, regularEndTarget);
    }
}
