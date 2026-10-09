package com.company.officecommute.repository.closing;

import com.company.officecommute.domain.closing.MonthlyClosing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface MonthlyClosingRepository extends JpaRepository<MonthlyClosing, Long> {

    Optional<MonthlyClosing> findByTargetYearMonth(YearMonth targetYearMonth);

    boolean existsByTargetYearMonth(YearMonth targetYearMonth);

    List<MonthlyClosing> findAllByOrderByTargetYearMonthDesc();
}
