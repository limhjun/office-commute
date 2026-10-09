package com.company.officecommute.domain.report;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 한 대상 월의 발송 이력. 상태 전이를 엔티티가 소유해, "어디선가 status 만 바꿔치기"가
 * 생기지 않게 한다.
 * <p>
 * 최초 선점은 {@code UNIQUE(target_year_month, kind)}, 기존 이력의 재선점은 낙관적 락이
 * 중복 발송을 막는다.
 */
@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_dispatch_year_month_kind", columnNames = {"target_year_month", "kind"})
})
public class ReportDispatch {

    /** 실패 사유 컬럼 길이. 넘치면 저장 시점에 터지므로 자른다. */
    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportDispatchId;

    /** 재시도/리스 회수의 read-check-write 경합을 DB update 시점에 판정한다. */
    @Version
    @Column(nullable = false)
    private long version;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "target_year_month", nullable = false, length = 7)
    private YearMonth targetYearMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DispatchStatus status;

    /** 결과가 기록된 시도 횟수. 진행 중인 시도는 아직 세지 않는다. */
    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private Instant lastAttemptedAt;

    private Instant sentAt;

    @Column(length = MAX_FAILURE_REASON_LENGTH)
    private String lastFailureReason;

    private Long deliveryConfirmedById;

    private Instant deliveryConfirmedAt;

    @Column(length = MAX_FAILURE_REASON_LENGTH)
    private String deliveryConfirmationNote;

    protected ReportDispatch() {
    }

    private ReportDispatch(YearMonth targetYearMonth, ReportKind kind, Instant now) {
        this.targetYearMonth = Objects.requireNonNull(targetYearMonth, "targetYearMonth는 null일 수 없습니다.");
        this.kind = Objects.requireNonNull(kind, "kind는 null일 수 없습니다.");
        this.status = DispatchStatus.IN_PROGRESS;
        this.attemptCount = 0;
        this.lastAttemptedAt = Objects.requireNonNull(now, "now는 null일 수 없습니다.");
    }

    /** 이 달을 선점한다. 동시에 두 실행이 부르면 유니크 제약이 한쪽을 떨어뜨린다. */
    public static ReportDispatch claim(YearMonth targetYearMonth, Instant now) {
        return claim(targetYearMonth, ReportKind.ORIGINAL, now);
    }

    public static ReportDispatch claim(YearMonth targetYearMonth, ReportKind kind, Instant now) {
        return new ReportDispatch(targetYearMonth, kind, now);
    }

    /**
     * 이미 있는 이력으로 새 시도를 시작한다(재시도 또는 리스 회수).
     * 실패 사유는 남겨 둔다 — 이번 시도가 또 실패할 때까지는 마지막으로 알려진 이유가 유효하다.
     */
    public void beginAttempt(Instant now) {
        this.status = DispatchStatus.IN_PROGRESS;
        this.lastAttemptedAt = now;
    }

    /**
     * SMTP 호출 전에 발송 결정을 내구적으로 기록한다.
     * 이 상태는 SENT 후처리 저장이 실패해도 동일 메일을 다시 보내지 않게 한다.
     */
    public void commitDelivery(Instant now) {
        this.status = DispatchStatus.DELIVERY_COMMITTED;
        this.lastAttemptedAt = now;
    }

    public void markSent(Instant now) {
        this.status = DispatchStatus.SENT;
        this.sentAt = now;
        this.lastAttemptedAt = now;
        this.attemptCount++;
        this.lastFailureReason = null;
    }

    public void markFailed(String reason, Instant now) {
        this.status = DispatchStatus.FAILED;
        this.lastAttemptedAt = now;
        this.attemptCount++;
        this.lastFailureReason = truncate(reason);
    }

    /**
     * 수신 여부 불명(DELIVERY_COMMITTED)을 운영자가 "수신됨"으로 확인했다. 실제 수신 시각은 모르므로
     * 발송 결정 시각을 발송 시각으로 남긴다.
     */
    public void confirmDelivered(Long confirmedById, String note, Instant now) {
        ensureDeliveryUncertain();
        this.status = DispatchStatus.SENT;
        this.sentAt = this.lastAttemptedAt;
        this.attemptCount++;
        this.lastFailureReason = null;
        recordConfirmation(confirmedById, note, now);
    }

    /** 운영자가 "수신되지 않음"을 확인했다. FAILED 로 돌려 보관 파일로 다시 보낼 수 있게 한다. */
    public void confirmNotDelivered(Long confirmedById, String note, Instant now) {
        ensureDeliveryUncertain();
        recordConfirmation(confirmedById, note, now);
        markFailed(DispatchFailureReason.UNEXPECTED.name() + ": 운영자 확인 — 미수신. " + note, now);
    }

    public boolean isDeliveryUncertain() {
        return status == DispatchStatus.DELIVERY_COMMITTED;
    }

    private void ensureDeliveryUncertain() {
        if (!isDeliveryUncertain()) {
            throw new IllegalStateException("DELIVERY_COMMITTED 상태에서만 수신 여부를 확인할 수 있다: " + status);
        }
    }

    private void recordConfirmation(Long confirmedById, String note, Instant now) {
        this.deliveryConfirmedById = Objects.requireNonNull(confirmedById, "confirmedById는 null일 수 없습니다.");
        this.deliveryConfirmedAt = Objects.requireNonNull(now, "now는 null일 수 없습니다.");
        this.deliveryConfirmationNote = truncate(note);
    }

    public boolean isSent() {
        return status == DispatchStatus.SENT;
    }

    /** SMTP 발송을 이미 시작했으므로 자동 재시도하면 안 되는가. */
    public boolean isDeliveryFinalized() {
        return status == DispatchStatus.DELIVERY_COMMITTED || status == DispatchStatus.SENT;
    }

    /**
     * 다른 실행이 선점한 채 아직 살아 있는가.
     * 발송 도중 프로세스가 죽으면 {@code IN_PROGRESS}가 남는데, 리스가 없으면 그 달이
     * 영원히 잠긴다 — 리스 시간이 지난 선점은 회수한다.
     */
    public boolean isLeaseHeld(Instant now, Duration lease) {
        return status == DispatchStatus.IN_PROGRESS && lastAttemptedAt.plus(lease).isAfter(now);
    }

    private String truncate(String reason) {
        if (reason == null || reason.length() <= MAX_FAILURE_REASON_LENGTH) {
            return reason;
        }
        return reason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    public Long getReportDispatchId() {
        return reportDispatchId;
    }

    public YearMonth getTargetYearMonth() {
        return targetYearMonth;
    }

    public ReportKind getKind() {
        return kind;
    }

    public Long getDeliveryConfirmedById() {
        return deliveryConfirmedById;
    }

    public Instant getDeliveryConfirmedAt() {
        return deliveryConfirmedAt;
    }

    public String getDeliveryConfirmationNote() {
        return deliveryConfirmationNote;
    }

    public DispatchStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getLastAttemptedAt() {
        return lastAttemptedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }
}
