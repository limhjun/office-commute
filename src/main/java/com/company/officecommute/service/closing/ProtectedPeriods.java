package com.company.officecommute.service.closing;

import com.company.officecommute.domain.closing.CommuteLockReason;
import com.company.officecommute.domain.closing.MonthlyClosing;
import com.company.officecommute.service.overtime.OverTimePeriod;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 근태 쓰기가 막힌 기간. 근무일({@code workDate}) 하나로 판정한다 — 근무 분은 근무일에만 귀속되므로
 * 그 날짜가 어떤 보고서의 입력 기간에도 속하지 않으면 바꿔도 이미 고정된 보고서가 달라지지 않는다.
 */
public record ProtectedPeriods(
        List<MonthlyClosing> closings,
        Set<YearMonth> deliveryUncertainMonths
) {

    public ProtectedPeriods {
        closings = List.copyOf(closings);
        deliveryUncertainMonths = Set.copyOf(deliveryUncertainMonths);
    }

    public Optional<CommuteLockReason> lockReason(LocalDate workDate) {
        if (closings.stream().anyMatch(closing -> closing.protects(workDate))) {
            return Optional.of(CommuteLockReason.MONTH_CLOSED);
        }
        if (deliveryUncertainMonths.stream().anyMatch(month -> inputRangeContains(month, workDate))) {
            return Optional.of(CommuteLockReason.DELIVERY_UNCERTAIN);
        }
        return Optional.empty();
    }

    private static boolean inputRangeContains(YearMonth month, LocalDate workDate) {
        OverTimePeriod period = new OverTimePeriod(month);
        return !workDate.isBefore(period.rangeStart()) && !workDate.isAfter(period.rangeEnd());
    }
}
