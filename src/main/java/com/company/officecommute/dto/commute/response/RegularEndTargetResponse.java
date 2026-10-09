package com.company.officecommute.dto.commute.response;

import com.company.officecommute.domain.closing.CommuteLockReason;
import com.company.officecommute.domain.commute.CommuteHistory;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 지금 일반 퇴근하면 종료되는 기록. 조회 월과 무관하게 같은 값이라, 월 경계를 넘는 야간근무도
 * 화면이 실제 퇴근 대상을 식별할 수 있다.
 */
public record RegularEndTargetResponse(
        Long commuteHistoryId,
        long version,
        LocalDate workDate,
        String workZone,
        OffsetDateTime workStartTime,
        OffsetDateTime endableUntil,
        CommuteLockReason lockReason
) {
    public static RegularEndTargetResponse of(CommuteHistory commuteHistory, CommuteLockReason lockReason) {
        return new RegularEndTargetResponse(
                commuteHistory.getCommuteHistoryId(),
                commuteHistory.getVersion(),
                commuteHistory.getWorkDate(),
                commuteHistory.getWorkZone(),
                commuteHistory.zonedWorkStartTime(),
                commuteHistory.zonedRegularEndDeadline(),
                lockReason
        );
    }
}
