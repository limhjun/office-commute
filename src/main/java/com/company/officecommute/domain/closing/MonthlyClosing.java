package com.company.officecommute.domain.closing;

import com.company.officecommute.domain.report.YearMonthAttributeConverter;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 월 마감. {@code rangeStart}~{@code rangeEnd}(집계 입력 기간)의 근태 쓰기를 모두 막는다.
 * 마감 해제 경로는 두지 않는다.
 */
@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_monthly_closing_year_month", columnNames = {"target_year_month"})
})
public class MonthlyClosing {

    private static final int MAX_NOTE_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long monthlyClosingId;

    @Convert(converter = YearMonthAttributeConverter.class)
    @Column(name = "target_year_month", nullable = false, length = 7)
    private YearMonth targetYearMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MonthlyClosingType closingType;

    @Column(nullable = false)
    private LocalDate rangeStart;

    @Column(nullable = false)
    private LocalDate rangeEnd;

    @Column(nullable = false)
    private Long closedById;

    @Column(nullable = false)
    private Instant closedAt;

    @Column(length = MAX_NOTE_LENGTH)
    private String note;

    protected MonthlyClosing() {
    }

    public MonthlyClosing(
            YearMonth targetYearMonth,
            MonthlyClosingType closingType,
            LocalDate rangeStart,
            LocalDate rangeEnd,
            Long closedById,
            Instant closedAt,
            String note
    ) {
        this.targetYearMonth = Objects.requireNonNull(targetYearMonth, "targetYearMonth는 null일 수 없습니다.");
        this.closingType = Objects.requireNonNull(closingType, "closingType은 null일 수 없습니다.");
        this.rangeStart = Objects.requireNonNull(rangeStart, "rangeStart는 null일 수 없습니다.");
        this.rangeEnd = Objects.requireNonNull(rangeEnd, "rangeEnd는 null일 수 없습니다.");
        if (rangeEnd.isBefore(rangeStart)) {
            throw new IllegalArgumentException("rangeEnd는 rangeStart 이후여야 합니다.");
        }
        this.closedById = Objects.requireNonNull(closedById, "closedById는 null일 수 없습니다.");
        this.closedAt = Objects.requireNonNull(closedAt, "closedAt은 null일 수 없습니다.");
        this.note = normalizeNote(note);
    }

    private static String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("note는 " + MAX_NOTE_LENGTH + "자 이하여야 합니다.");
        }
        return trimmed;
    }

    public boolean protects(LocalDate workDate) {
        return !workDate.isBefore(rangeStart) && !workDate.isAfter(rangeEnd);
    }

    public Long getMonthlyClosingId() {
        return monthlyClosingId;
    }

    public YearMonth getTargetYearMonth() {
        return targetYearMonth;
    }

    public MonthlyClosingType getClosingType() {
        return closingType;
    }

    public LocalDate getRangeStart() {
        return rangeStart;
    }

    public LocalDate getRangeEnd() {
        return rangeEnd;
    }

    public Long getClosedById() {
        return closedById;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getNote() {
        return note;
    }
}
