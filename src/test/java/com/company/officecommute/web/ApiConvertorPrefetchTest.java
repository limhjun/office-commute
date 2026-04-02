package com.company.officecommute.web;

import com.company.officecommute.domain.overtime.HolidayResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ApiConvertorPrefetchTest {

    @Autowired private ApiConvertor apiConvertor;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private HolidaySyncStatusRepository holidaySyncStatusRepository;

    @MockitoBean private RestTemplate restTemplate;
    @MockitoBean private ApiProperties apiProperties;
    @MockitoBean private Clock clock;

    @Test
    @DisplayName("다음 달 공휴일 선제적 저장 성공 시 DB에 저장된다")
    void prefetchNextMonthHolidays_savesToDatabase_whenApiSucceeds() {
        mockCurrentTime(LocalDateTime.of(2025, 5, 27, 9, 0));
        // given
        YearMonth currentMonth = YearMonth.of(2025, 5);

        // 다음 달(6월) API 응답 모킹
        mockSuccessfulApiResponseForJune();

        // when
        apiConvertor.prefetchNextMonthHolidays(currentMonth);

        // then
        List<LocalDate> savedHolidays = holidayRepository.findHolidayDatesByYearAndMonth(2025, 6);
        assertThat(savedHolidays).hasSize(1);
        assertThat(savedHolidays).containsExactly(LocalDate.of(2025, 6, 6));
        assertThat(holidaySyncStatusRepository.findByYearAndMonth(2025, 6)).isPresent();
    }

    @Test
    @DisplayName("다음 달 공휴일 선제적 저장 실패 시 예외를 던지지 않는다")
    void prefetchNextMonthHolidays_doesNotThrowException_whenApiFails() {
        mockCurrentTime(LocalDateTime.of(2025, 5, 27, 9, 0));
        // given
        YearMonth currentMonth = YearMonth.of(2025, 5);

        mockFailedApiResponse();

        // when & then
        // 예외가 발생하지 않아야 함
        apiConvertor.prefetchNextMonthHolidays(currentMonth);

        // DB에 저장되지 않았는지 확인
        List<LocalDate> savedHolidays = holidayRepository.findHolidayDatesByYearAndMonth(2025, 6);
        assertThat(savedHolidays).isEmpty();
        assertThat(holidaySyncStatusRepository.findByYearAndMonth(2025, 6)).isEmpty();
    }

    @Test
    @DisplayName("선제적 저장이 있으면 API 실패 시 캐시 데이터로 근무일수를 계산한다")
    void countStandardWorkingDays_usesCachedData_whenApiFailsButPrefetchExists() {
        mockCurrentTime(LocalDateTime.of(2025, 6, 2, 9, 0));
        // given
        YearMonth mayMonth = YearMonth.of(2025, 5);
        YearMonth juneMonth = YearMonth.of(2025, 6);

        // 1단계: 5월에 6월 공휴일 미리 저장 (선제적 캐싱)
        mockSuccessfulApiResponseForJune();
        apiConvertor.prefetchNextMonthHolidays(mayMonth);

        // 2단계: 6월에 API 실패 시뮬레이션
        mockFailedApiResponse();

        // when: 6월에 근무일수 계산 (API 실패 → DB 캐시 사용)
        long workingDays = apiConvertor.countNumberOfStandardWorkingDays(juneMonth);

        // then: 2025년 6월: 30일 - 9일(주말) - 1일(현충일, 금요일) = 20일
        assertThat(workingDays).isEqualTo(20);
    }

    private void mockSuccessfulApiResponseForJune() {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        HolidayResponse response = new HolidayResponse();
        HolidayResponse.Body body = new HolidayResponse.Body();

        HolidayResponse.Item item1 = new HolidayResponse.Item();
        item1.setLocDate("20250606"); // 현충일

        body.setItems(List.of(item1));
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
