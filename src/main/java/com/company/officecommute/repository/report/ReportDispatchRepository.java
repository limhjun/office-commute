package com.company.officecommute.repository.report;

import com.company.officecommute.domain.report.DispatchStatus;
import com.company.officecommute.domain.report.ReportDispatch;
import com.company.officecommute.domain.report.ReportKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface ReportDispatchRepository extends JpaRepository<ReportDispatch, Long> {

    default Optional<ReportDispatch> findByTargetYearMonth(YearMonth targetYearMonth) {
        return findByTargetYearMonthAndKind(targetYearMonth, ReportKind.ORIGINAL);
    }

    Optional<ReportDispatch> findByTargetYearMonthAndKind(YearMonth targetYearMonth, ReportKind kind);

    List<ReportDispatch> findAllByTargetYearMonthOrderByKindAsc(YearMonth targetYearMonth);

    List<ReportDispatch> findAllByStatus(DispatchStatus status);
}
