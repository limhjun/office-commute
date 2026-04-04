package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.overtime.HolidaySyncStatus;
import com.company.officecommute.dto.overtime.response.HolidayCacheStatusResponse;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.repository.overtime.HolidayRepository;
import com.company.officecommute.repository.overtime.HolidaySyncStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

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

        if (cachedHolidays.isEmpty()) {
            return new HolidayCacheStatusResponse(
                    yearMonth.toString(),
                    0,
                    false,
                    "MISSING_CACHE",
                    buildMissingCacheReason(yearMonth),
                    getLastSuccessfulSyncedAt(syncStatus)
            );
        }

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

    public Set<LocalDate> getUsableCachedHolidaysOrThrow(YearMonth yearMonth) {
        HolidayCacheStatusResponse status = getStatus(yearMonth);

        if (!status.cacheUsable()) {
            throw new HolidayDataUnavailableException(status.reason());
        }

        return Set.copyOf(findCachedHolidays(yearMonth));
    }

    private List<LocalDate> findCachedHolidays(YearMonth yearMonth) {
        return holidayRepository.findHolidayDatesByYearAndMonth(
                yearMonth.getYear(),
                yearMonth.getMonthValue()
        );
    }

    private LocalDateTime getLastSuccessfulSyncedAt(HolidaySyncStatus syncStatus) {
        return syncStatus == null ? null : syncStatus.getLastSuccessfulSyncedAt();
    }

    private String buildMissingCacheReason(YearMonth yearMonth) {
        return "공휴일 데이터를 확인할 수 없어 초과근무를 계산할 수 없습니다: " + yearMonth;
    }

    private String buildMissingSyncStatusReason(YearMonth yearMonth) {
        return "공휴일 캐시의 최신성을 확인할 수 없어 초과근무를 계산할 수 없습니다: " + yearMonth;
    }

    private String buildStaleCacheReason(YearMonth yearMonth) {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        if (yearMonth.isBefore(currentMonth)) {
            return "공휴일 캐시가 최신 상태가 아니어서 초과근무를 계산할 수 없습니다: " + yearMonth
                    + " (월말 기준 최종 검증 필요)";
        }
        return "공휴일 캐시가 최신 상태가 아니어서 초과근무를 계산할 수 없습니다: " + yearMonth
                + " (최근 7일 내 재검증 필요)";
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
