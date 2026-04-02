package com.company.officecommute.web;

import com.company.officecommute.domain.overtime.Holiday;
import com.company.officecommute.domain.overtime.HolidayResponse;
import com.company.officecommute.domain.overtime.HolidaySyncStatus;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.repository.overtime.HolidayRepository;
import com.company.officecommute.repository.overtime.HolidaySyncStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ApiConvertorFallbackTest {

    @Autowired private ApiConvertor apiConvertor;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private HolidaySyncStatusRepository holidaySyncStatusRepository;

    @MockitoBean private RestTemplate restTemplate;
    @MockitoBean private ApiProperties apiProperties;
    @MockitoBean private Clock clock;

    @Test
    @DisplayName("API 호출 성공 시 공휴일을 DB에 저장하고 근무일수를 계산한다")
    void countStandardWorkingDays_savesToDatabase_whenApiSucceeds() {
        mockCurrentTime(LocalDateTime.of(2025, 11, 10, 9, 0));
        YearMonth yearMonth = YearMonth.of(2025, 11);
        mockSuccessfulApiResponse(yearMonth);

        long workingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);

        assertThat(workingDays).isGreaterThan(0);

        List<LocalDate> savedHolidays = holidayRepository.findHolidayDatesByYearAndMonth(2025, 11);
        assertThat(savedHolidays).hasSize(2);
        assertThat(savedHolidays).containsExactlyInAnyOrder(
                LocalDate.of(2025, 11, 3),
                LocalDate.of(2025, 11, 15)
        );
    }

    @Test
    @DisplayName("API 호출 실패 시 DB 캐시 데이터로 계산한다")
    void countStandardWorkingDays_usesDatabaseCache_whenApiFails() {
        mockCurrentTime(LocalDateTime.of(2026, 1, 5, 9, 0));
        YearMonth yearMonth = YearMonth.of(2025, 12);

        // DB에 캐시 데이터 저장 (12/25 크리스마스는 목요일, 12/31 제외는 수요일)
        Holiday holiday1 = new Holiday(2025, 12, LocalDate.of(2025, 12, 25));
        Holiday holiday2 = new Holiday(2025, 12, LocalDate.of(2025, 12, 31));
        holidayRepository.saveAll(List.of(holiday1, holiday2));
        holidaySyncStatusRepository.save(new HolidaySyncStatus(2025, 12, LocalDateTime.of(2025, 12, 31, 23, 0)));

        // API 호출 실패 시뮬레이션 (403 Forbidden)
        mockFailedApiResponse();

        // 예외 없이 계산 성공 (DB 캐시 사용)
        long workingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);

        // 2025년 12월: 31일 - 8일(주말) - 2일(공휴일) = 21일
        assertThat(workingDays).isEqualTo(21);
    }

    @Test
    @DisplayName("API 호출 실패하고 DB에도 데이터가 없으면 계산을 중단한다")
    void countStandardWorkingDays_throwsException_whenApiFailsAndNoDatabaseData() {
        mockCurrentTime(LocalDateTime.of(2026, 1, 5, 9, 0));
        YearMonth yearMonth = YearMonth.of(2026, 1);

        // API 호출 실패 시뮬레이션
        mockFailedApiResponse();

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(yearMonth))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("공휴일 데이터를 확인할 수 없어 초과근무를 계산할 수 없습니다");
    }

    @Test
    @DisplayName("API 호출 실패 시 캐시가 있어도 최신성이 검증되지 않으면 계산을 중단한다")
    void countStandardWorkingDays_throwsException_whenCacheIsStale() {
        mockCurrentTime(LocalDateTime.of(2026, 1, 5, 9, 0));
        YearMonth yearMonth = YearMonth.of(2025, 12);

        holidayRepository.saveAll(List.of(
                new Holiday(2025, 12, LocalDate.of(2025, 12, 25)),
                new Holiday(2025, 12, LocalDate.of(2025, 12, 31))
        ));
        holidaySyncStatusRepository.save(new HolidaySyncStatus(2025, 12, LocalDateTime.of(2025, 12, 20, 9, 0)));

        mockFailedApiResponse();

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(yearMonth))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("공휴일 캐시가 최신 상태가 아니어서");
    }

    @Test
    @DisplayName("API 호출 성공 시 기존 DB 데이터를 삭제하고 새로운 데이터로 갱신한다")
    void countStandardWorkingDays_updatesDatabase_whenApiSucceeds() {
        mockCurrentTime(LocalDateTime.of(2025, 5, 7, 9, 0));
        YearMonth yearMonth = YearMonth.of(2025, 5);

        // 기존 DB에 오래된 데이터가 있다고 가정
        Holiday oldHoliday1 = new Holiday(2025, 5, LocalDate.of(2025, 5, 1));
        Holiday oldHoliday2 = new Holiday(2025, 5, LocalDate.of(2025, 5, 15));
        holidayRepository.saveAll(List.of(oldHoliday1, oldHoliday2));

        // API 호출 성공 시뮬레이션 (새로운 데이터)
        mockSuccessfulApiResponseForMay();

        apiConvertor.countNumberOfStandardWorkingDays(yearMonth);

        List<LocalDate> savedHolidays = holidayRepository.findHolidayDatesByYearAndMonth(2025, 5);
        assertThat(savedHolidays).hasSize(2);
        assertThat(savedHolidays).containsExactlyInAnyOrder(
                LocalDate.of(2025, 5, 5),
                LocalDate.of(2025, 5, 6)
        );
        // 기존 오래된 데이터는 삭제되었음
        assertThat(savedHolidays).doesNotContain(LocalDate.of(2025, 5, 1));
        assertThat(savedHolidays).doesNotContain(LocalDate.of(2025, 5, 15));
    }

    private void mockSuccessfulApiResponse(YearMonth yearMonth) {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        HolidayResponse response = new HolidayResponse();
        HolidayResponse.Body body = new HolidayResponse.Body();

        HolidayResponse.Item item1 = new HolidayResponse.Item();
        item1.setLocDate("20251103");

        HolidayResponse.Item item2 = new HolidayResponse.Item();
        item2.setLocDate("20251115");

        body.setItems(List.of(item1, item2));
        response.setBody(body);

        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenReturn(response);
    }

    private void mockSuccessfulApiResponseForMay() {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        HolidayResponse response = new HolidayResponse();
        HolidayResponse.Body body = new HolidayResponse.Body();

        HolidayResponse.Item item1 = new HolidayResponse.Item();
        item1.setLocDate("20250505");

        HolidayResponse.Item item2 = new HolidayResponse.Item();
        item2.setLocDate("20250506");

        body.setItems(List.of(item1, item2));
        response.setBody(body);

        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenReturn(response);
    }

    private void mockFailedApiResponse() {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenThrow(HttpClientErrorException.Forbidden.create(
                        "Forbidden",
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Forbidden",
                        null,
                        null,
                        null
                ));
    }

    private void mockCurrentTime(LocalDateTime now) {
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        when(clock.getZone()).thenReturn(zoneId);
        when(clock.instant()).thenReturn(now.atZone(zoneId).toInstant());
    }
}
