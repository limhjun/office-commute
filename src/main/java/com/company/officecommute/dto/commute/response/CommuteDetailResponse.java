package com.company.officecommute.dto.commute.response;

import com.company.officecommute.domain.closing.CommuteLockReason;
import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.commute.CommuteStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CommuteDetailResponse(
        Long commuteHistoryId,
        long version,
        LocalDate date,
        String workZone,
        OffsetDateTime workStartTime,
        OffsetDateTime workEndTime,
        long workingMinutes,
        boolean usingDayOff,
        CommuteStatus status,
        Long pendingCorrectionRequestId,
        CommuteLockReason lockReason
) {
    public static CommuteDetailResponse of(
            CommuteHistory commuteHistory,
            CommuteStatus status,
            Long pendingCorrectionRequestId,
            CommuteLockReason lockReason
    ) {
        return new CommuteDetailResponse(
                commuteHistory.getCommuteHistoryId(),
                commuteHistory.getVersion(),
                commuteHistory.getWorkDate(),
                commuteHistory.getWorkZone(),
                commuteHistory.zonedWorkStartTime(),
                commuteHistory.zonedWorkEndTime(),
                commuteHistory.getWorkingMinutes(),
                commuteHistory.isUsingDayOff(),
                status,
                pendingCorrectionRequestId,
                lockReason
        );
    }
}
