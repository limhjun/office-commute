package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.overtime.HolidaySyncStatus;
import com.company.officecommute.dto.overtime.response.HolidayCacheStatusResponse;
import com.company.officecommute.repository.overtime.HolidayRepository;
import com.company.officecommute.repository.overtime.HolidaySyncStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HolidayCacheStatusServiceTest {

    @InjectMocks private HolidayCacheStatusService holidayCacheStatusService;

    @Mock private HolidayRepository holidayRepository;
    @Mock private HolidaySyncStatusRepository holidaySyncStatusRepository;
    @Mock private Clock clock;

    @Test
    @DisplayName("캐시와 최신성 정보가 모두 있으면 READY 상태를 반환한다")
    void getStatus_ready() {
        mockCurrentTime(LocalDateTime.of(2026, 4, 4, 9, 0));
        YearMonth yearMonth = YearMonth.of(2026, 4);

        given(holidayRepository.findHolidayDatesByYearAndMonth(2026, 4))
                .willReturn(List.of(LocalDate.of(2026, 4, 5)));
        given(holidaySyncStatusRepository.findByYearAndMonth(2026, 4))
                .willReturn(Optional.of(new HolidaySyncStatus(2026, 4, LocalDateTime.of(2026, 4, 2, 9, 0))));

        HolidayCacheStatusResponse status = holidayCacheStatusService.getStatus(yearMonth);

        assertThat(status.cacheUsable()).isTrue();
        assertThat(status.status()).isEqualTo("READY");
        assertThat(status.cachedHolidayCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("공휴일이 0개여도 동기화 정보가 있고 최신성이 유효하면 READY 상태를 반환한다")
    void getStatus_readyWhenNoHolidaysAndSyncStatusExists() {
        mockCurrentTime(LocalDateTime.of(2026, 4, 4, 9, 0));
        YearMonth yearMonth = YearMonth.of(2026, 4);

        given(holidayRepository.findHolidayDatesByYearAndMonth(2026, 4))
                .willReturn(List.of());
        given(holidaySyncStatusRepository.findByYearAndMonth(2026, 4))
                .willReturn(Optional.of(new HolidaySyncStatus(2026, 4, LocalDateTime.of(2026, 4, 2, 9, 0))));

        HolidayCacheStatusResponse status = holidayCacheStatusService.getStatus(yearMonth);

        assertThat(status.cacheUsable()).isTrue();
        assertThat(status.status()).isEqualTo("READY");
        assertThat(status.cachedHolidayCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("공휴일이 0개이고 동기화 정보가 없으면 MISSING_SYNC_STATUS 상태를 반환한다")
    void getStatus_missingSyncStatusWhenNoHolidays() {
        YearMonth yearMonth = YearMonth.of(2026, 4);

        given(holidayRepository.findHolidayDatesByYearAndMonth(2026, 4))
                .willReturn(List.of());
        given(holidaySyncStatusRepository.findByYearAndMonth(2026, 4))
                .willReturn(Optional.empty());

        HolidayCacheStatusResponse status = holidayCacheStatusService.getStatus(yearMonth);

        assertThat(status.cacheUsable()).isFalse();
        assertThat(status.status()).isEqualTo("MISSING_SYNC_STATUS");
    }

    @Test
    @DisplayName("캐시는 있지만 월말 기준으로 오래된 과거월 데이터면 STALE_CACHE 상태를 반환한다")
    void getStatus_stalePastMonthCache() {
        mockCurrentTime(LocalDateTime.of(2026, 4, 4, 9, 0));
        YearMonth yearMonth = YearMonth.of(2026, 3);

        given(holidayRepository.findHolidayDatesByYearAndMonth(2026, 3))
                .willReturn(List.of(LocalDate.of(2026, 3, 1)));
        given(holidaySyncStatusRepository.findByYearAndMonth(2026, 3))
                .willReturn(Optional.of(new HolidaySyncStatus(2026, 3, LocalDateTime.of(2026, 3, 20, 9, 0))));

        HolidayCacheStatusResponse status = holidayCacheStatusService.getStatus(yearMonth);

        assertThat(status.cacheUsable()).isFalse();
        assertThat(status.status()).isEqualTo("STALE_CACHE");
    }

    private void mockCurrentTime(LocalDateTime now) {
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        given(clock.getZone()).willReturn(zoneId);
        given(clock.instant()).willReturn(now.atZone(zoneId).toInstant());
    }
}
