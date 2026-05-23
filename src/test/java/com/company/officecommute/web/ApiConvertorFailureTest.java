package com.company.officecommute.web;

import com.company.officecommute.domain.overtime.HolidayResponse;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ApiConvertorFailureTest {

    @Autowired private ApiConvertor apiConvertor;

    @MockitoBean private RestTemplate restTemplate;
    @MockitoBean private ApiProperties apiProperties;

    @Test
    @DisplayName("API 호출 성공 시 응답 데이터로 근무일수를 계산한다")
    void countStandardWorkingDays_calculatesWorkingDays_whenApiSucceeds() {
        YearMonth yearMonth = YearMonth.of(2025, 11);
        mockSuccessfulApiResponse(yearMonth);

        long workingDays = apiConvertor.countNumberOfStandardWorkingDays(yearMonth);

        assertThat(workingDays).isEqualTo(19);
    }

    @Test
    @DisplayName("API 호출 실패 시 계산을 중단한다")
    void countStandardWorkingDays_throwsException_whenApiFails() {
        YearMonth yearMonth = YearMonth.of(2025, 12);

        mockFailedApiResponse();

        assertThatThrownBy(() -> apiConvertor.countNumberOfStandardWorkingDays(yearMonth))
                .isInstanceOf(HolidayDataUnavailableException.class)
                .hasMessageContaining("공휴일 정보를 확인할 수 없어 초과근무 리포트를 생성할 수 없습니다");
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
}
