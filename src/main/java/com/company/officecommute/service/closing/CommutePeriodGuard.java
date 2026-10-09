package com.company.officecommute.service.closing;

import com.company.officecommute.domain.report.DispatchStatus;
import com.company.officecommute.domain.report.ReportDispatch;
import com.company.officecommute.repository.closing.MonthlyClosingRepository;
import com.company.officecommute.repository.report.ReportDispatchRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 모든 근태 쓰기 경로(출근·퇴근·연차·정정 신청/승인)가 거치는 보호 기간 검사.
 * 마감과의 경쟁은 {@link com.company.officecommute.service.commute.CommuteWriteLock}이 직렬화한다.
 * <p>
 * 마감·발송 이력은 월 단위의 작은 테이블이라 매번 전부 읽는다.
 */
@Component
public class CommutePeriodGuard {

    private final MonthlyClosingRepository monthlyClosingRepository;
    private final ReportDispatchRepository reportDispatchRepository;

    public CommutePeriodGuard(
            MonthlyClosingRepository monthlyClosingRepository,
            ReportDispatchRepository reportDispatchRepository
    ) {
        this.monthlyClosingRepository = monthlyClosingRepository;
        this.reportDispatchRepository = reportDispatchRepository;
    }

    public ProtectedPeriods load() {
        Set<YearMonth> uncertainMonths = reportDispatchRepository.findAllByStatus(DispatchStatus.DELIVERY_COMMITTED)
                .stream()
                .map(ReportDispatch::getTargetYearMonth)
                .collect(Collectors.toSet());
        return new ProtectedPeriods(monthlyClosingRepository.findAll(), uncertainMonths);
    }

    public void assertWritable(LocalDate workDate) {
        load().lockReason(workDate).ifPresent(reason -> {
            throw reason.toException();
        });
    }
}
