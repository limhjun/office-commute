package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.overtime.HolidaySyncStatus;
import com.company.officecommute.dto.overtime.response.HolidayCacheStatusResponse;
import com.company.officecommute.repository.overtime.HolidayRepository;
import com.company.officecommute.repository.overtime.HolidaySyncStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class HolidayCacheStatusService {

    private static final long RECENT_CACHE_MAX_AGE_DAYS = 7L;

    private final HolidayRepository holidayRepository;
    private final HolidaySyncStatusRepository holidaySyncStatusRepository;
    private final Clock clock;

    public HolidayCacheStatusService(
            HolidayRepository holidayRepository,
            HolidaySyncStatusRepository holidaySyncStatusRepository,
            Clock clock
    ) {
        this.holidayRepository = holidayRepository;
        this.holidaySyncStatusRepository = holidaySyncStatusRepository;
        this.clock = clock;
    }

    public HolidayCacheStatusResponse getStatus(YearMonth yearMonth) {
        List<LocalDate> cachedHolidays = findCachedHolidays(yearMonth);
        HolidaySyncStatus syncStatus = holidaySyncStatusRepository.findByYearAndMonth(
                yearMonth.getYear(),
                yearMonth.getMonthValue()
        ).orElse(null);

        if (syncStatus == null) {
            return new HolidayCacheStatusResponse(
                    yearMonth.toString(),
                    cachedHolidays.size(),
                    false,
                    "MISSING_SYNC_STATUS",
                    buildMissingSyncStatusReason(yearMonth),
                    null
            );
        }

        if (!isReliableCache(yearMonth, syncStatus)) {
            return new HolidayCacheStatusResponse(
                    yearMonth.toString(),
                    cachedHolidays.size(),
                    false,
                    "STALE_CACHE",
                    buildStaleCacheReason(yearMonth),
                    syncStatus.getLastSuccessfulSyncedAt()
            );
        }

        return new HolidayCacheStatusResponse(
                yearMonth.toString(),
                cachedHolidays.size(),
                true,
                "READY",
                "초과근무 계산에 사용할 수 있는 공휴일 캐시입니다.",
                syncStatus.getLastSuccessfulSyncedAt()
        );
    }

    private List<LocalDate> findCachedHolidays(YearMonth yearMonth) {
        return holidayRepository.findHolidayDatesByYearAndMonth(
                yearMonth.getYear(),
                yearMonth.getMonthValue()
        );
    }

    private String buildMissingSyncStatusReason(YearMonth yearMonth) {
        return "공휴일 캐시 정보를 찾을 수 없습니다: " + yearMonth;
    }

    private String buildStaleCacheReason(YearMonth yearMonth) {
        return "공휴일 캐시가 최신 상태가 아닙니다: " + yearMonth;
    }

    private boolean isReliableCache(YearMonth yearMonth, HolidaySyncStatus syncStatus) {
        LocalDate lastSyncedDate = syncStatus.getLastSuccessfulSyncedAt().toLocalDate();
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);

        if (yearMonth.isBefore(currentMonth)) {
            return !lastSyncedDate.isBefore(yearMonth.atEndOfMonth());
        }
        return !lastSyncedDate.isBefore(today.minusDays(RECENT_CACHE_MAX_AGE_DAYS));
    }
}
