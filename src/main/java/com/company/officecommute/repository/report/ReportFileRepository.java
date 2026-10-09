package com.company.officecommute.repository.report;

import com.company.officecommute.domain.report.ReportFile;
import com.company.officecommute.domain.report.ReportKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.Optional;

public interface ReportFileRepository extends JpaRepository<ReportFile, Long> {

    Optional<ReportFile> findByTargetYearMonthAndKind(YearMonth targetYearMonth, ReportKind kind);

    boolean existsByTargetYearMonthAndKind(YearMonth targetYearMonth, ReportKind kind);
}
