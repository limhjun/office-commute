package com.company.officecommute.repository.overtime;

import com.company.officecommute.domain.overtime.HolidaySyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HolidaySyncStatusRepository extends JpaRepository<HolidaySyncStatus, Long> {

    Optional<HolidaySyncStatus> findByYearAndMonth(int year, int month);
}
