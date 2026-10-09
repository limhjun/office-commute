package com.company.officecommute.domain.correction;

import com.company.officecommute.domain.commute.CommuteHistory;
import com.company.officecommute.domain.employee.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 퇴근 시각 정정 요청. 요청 행이 이력을 겸한다 — PENDING 에서 한 번만 전이하고 종착 상태는 바뀌지 않는다.
 * <p>
 * previous* 는 신청 당시 원본 스냅샷이다. 승인은 {@code commuteVersion}이 같을 때만 적용되므로
 * 승인 직전 원본 값과도 같다.
 */
@Entity
@Table(name = "commute_correction_request", uniqueConstraints = {
        @UniqueConstraint(name = "uk_correction_request_pending", columnNames = {"pending_commute_history_id"})
})
public class CommuteCorrectionRequest {

    private static final int MAX_TEXT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "correction_request_id")
    private Long correctionRequestId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private Long commuteHistoryId;

    // PENDING 동안만 commuteHistoryId 를 담는다. UNIQUE 가 "기록당 대기 요청 1건"의 최종 경계다.
    @Column(name = "pending_commute_history_id")
    private Long pendingCommuteHistoryId;

    @Column(nullable = false)
    private Long requesterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role requesterRole;

    private Long assignedApproverId;

    @Column(nullable = false)
    private long commuteVersion;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false, length = 64)
    private String workZone;

    @Column(nullable = false)
    private Instant workStartTime;

    private Instant previousWorkEndTime;

    @Column(nullable = false)
    private long previousWorkingMinutes;

    @Column(nullable = false)
    private Instant requestedWorkEndTime;

    @Column(nullable = false)
    private long requestedWorkingMinutes;

    @Column(nullable = false, length = MAX_TEXT_LENGTH)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CorrectionStatus status;

    @Column(nullable = false)
    private Instant requestedAt;

    private Long processedById;

    private Instant processedAt;

    @Column(length = MAX_TEXT_LENGTH)
    private String reviewComment;

    protected CommuteCorrectionRequest() {
    }

    private CommuteCorrectionRequest(
            CommuteHistory commute,
            Role requesterRole,
            Long assignedApproverId,
            Instant requestedWorkEndTime,
            long requestedWorkingMinutes,
            String reason,
            Instant requestedAt
    ) {
        this.commuteHistoryId = Objects.requireNonNull(commute.getCommuteHistoryId(), "저장된 근태 기록이어야 합니다.");
        this.pendingCommuteHistoryId = this.commuteHistoryId;
        this.requesterId = commute.getEmployeeId();
        this.requesterRole = Objects.requireNonNull(requesterRole, "requesterRole은 null일 수 없습니다.");
        this.assignedApproverId = assignedApproverId;
        this.commuteVersion = commute.getVersion();
        this.workDate = commute.getWorkDate();
        this.workZone = commute.getWorkZone();
        this.workStartTime = commute.getWorkStartTime();
        this.previousWorkEndTime = commute.getWorkEndTime();
        this.previousWorkingMinutes = commute.getWorkingMinutes();
        this.requestedWorkEndTime = Objects.requireNonNull(requestedWorkEndTime, "requestedWorkEndTime은 null일 수 없습니다.");
        this.requestedWorkingMinutes = requestedWorkingMinutes;
        this.reason = requireText(reason, "정정 사유");
        this.status = CorrectionStatus.PENDING;
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt은 null일 수 없습니다.");
    }

    /**
     * 신청. 시각 검증과 근무 분 계산은 {@link CommuteHistory#calculateCorrectedWorkingMinutes}가 먼저 끝낸다.
     */
    public static CommuteCorrectionRequest submit(
            CommuteHistory commute,
            Role requesterRole,
            Long assignedApproverId,
            Instant requestedWorkEndTime,
            long requestedWorkingMinutes,
            String reason,
            Instant now
    ) {
        return new CommuteCorrectionRequest(
                commute, requesterRole, assignedApproverId, requestedWorkEndTime, requestedWorkingMinutes, reason, now);
    }

    public void approve(Long approverId, String comment, Instant now) {
        finish(CorrectionStatus.APPROVED, approverId, now);
        this.reviewComment = optionalText(comment);
    }

    public void reject(Long approverId, String rejectReason, Instant now) {
        String validatedReason = requireText(rejectReason, "반려 사유");
        finish(CorrectionStatus.REJECTED, approverId, now);
        this.reviewComment = validatedReason;
    }

    public void cancel(Long requesterId, Instant now) {
        finish(CorrectionStatus.CANCELLED, requesterId, now);
    }

    private void finish(CorrectionStatus target, Long processedById, Instant now) {
        ensurePending();
        this.status = target;
        this.pendingCommuteHistoryId = null;
        this.processedById = Objects.requireNonNull(processedById, "processedById는 null일 수 없습니다.");
        this.processedAt = Objects.requireNonNull(now, "now는 null일 수 없습니다.");
    }

    public void ensurePending() {
        if (status != CorrectionStatus.PENDING) {
            throw new CorrectionException(CorrectionErrorCode.CORRECTION_ALREADY_PROCESSED);
        }
    }

    public boolean isPending() {
        return status == CorrectionStatus.PENDING;
    }

    public boolean isRequestedBy(Long employeeId) {
        return Objects.equals(requesterId, employeeId);
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + "는 " + MAX_TEXT_LENGTH + "자 이하여야 합니다.");
        }
        return trimmed;
    }

    private static String optionalText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return requireText(text, "승인 의견");
    }

    public Long getCorrectionRequestId() {
        return correctionRequestId;
    }

    public Long getCommuteHistoryId() {
        return commuteHistoryId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public Role getRequesterRole() {
        return requesterRole;
    }

    public Long getAssignedApproverId() {
        return assignedApproverId;
    }

    public long getCommuteVersion() {
        return commuteVersion;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public String getWorkZone() {
        return workZone;
    }

    public ZoneId getZoneId() {
        return ZoneId.of(workZone);
    }

    public Instant getWorkStartTime() {
        return workStartTime;
    }

    public Instant getPreviousWorkEndTime() {
        return previousWorkEndTime;
    }

    public long getPreviousWorkingMinutes() {
        return previousWorkingMinutes;
    }

    public Instant getRequestedWorkEndTime() {
        return requestedWorkEndTime;
    }

    public long getRequestedWorkingMinutes() {
        return requestedWorkingMinutes;
    }

    public String getReason() {
        return reason;
    }

    public CorrectionStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Long getProcessedById() {
        return processedById;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getReviewComment() {
        return reviewComment;
    }
}
