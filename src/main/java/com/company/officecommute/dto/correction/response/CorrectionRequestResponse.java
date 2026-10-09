package com.company.officecommute.dto.correction.response;

import com.company.officecommute.domain.correction.CommuteCorrectionRequest;
import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.employee.response.EmployeeRef;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.function.Function;

/**
 * 정정 요청 한 건. 시각은 기록 당시 workZone 오프셋으로 내려가고, 신청·처리 시각만 UTC 다.
 */
public record CorrectionRequestResponse(
        Long requestId,
        CorrectionStatus status,
        Long commuteHistoryId,
        long commuteVersion,
        EmployeeRef requester,
        Role requesterRole,
        EmployeeRef assignedApprover,
        LocalDate workDate,
        String workZone,
        OffsetDateTime workStartTime,
        OffsetDateTime previousWorkEndTime,
        long previousWorkingMinutes,
        OffsetDateTime requestedWorkEndTime,
        long requestedWorkingMinutes,
        String reason,
        Instant requestedAt,
        EmployeeRef processedBy,
        Instant processedAt,
        String reviewComment,
        Actions actions
) {

    public record Actions(boolean canCancel, boolean canReview) {
    }

    public static CorrectionRequestResponse of(
            CommuteCorrectionRequest request,
            Function<Long, EmployeeRef> employeeRefs,
            Actions actions
    ) {
        ZoneId zone = request.getZoneId();
        return new CorrectionRequestResponse(
                request.getCorrectionRequestId(),
                request.getStatus(),
                request.getCommuteHistoryId(),
                request.getCommuteVersion(),
                employeeRefs.apply(request.getRequesterId()),
                request.getRequesterRole(),
                refOrNull(request.getAssignedApproverId(), employeeRefs),
                request.getWorkDate(),
                request.getWorkZone(),
                inZone(request.getWorkStartTime(), zone),
                inZone(request.getPreviousWorkEndTime(), zone),
                request.getPreviousWorkingMinutes(),
                inZone(request.getRequestedWorkEndTime(), zone),
                request.getRequestedWorkingMinutes(),
                request.getReason(),
                request.getRequestedAt(),
                refOrNull(request.getProcessedById(), employeeRefs),
                request.getProcessedAt(),
                request.getReviewComment(),
                actions
        );
    }

    private static EmployeeRef refOrNull(Long employeeId, Function<Long, EmployeeRef> employeeRefs) {
        return employeeId == null ? null : employeeRefs.apply(employeeId);
    }

    private static OffsetDateTime inZone(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toOffsetDateTime();
    }
}
