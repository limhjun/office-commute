package com.company.officecommute.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ApiConvertorTest {

    @Autowired
    private ApiConvertor apiConvertor;

    @MockitoBean
    private RestTemplate restTemplate;

    @MockitoBean
    private ApiProperties apiProperties;

    @Test
    void _2024년_5월의_기준_근로_시간을_구하는_메서드를_검증하라() {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        HolidayResponse fakeResponse = new HolidayResponse();
        HolidayResponse.Body body = new HolidayResponse.Body();

        HolidayResponse.Item date_2024_05_05 = new HolidayResponse.Item();
        HolidayResponse.Item date_2024_05_06 = new HolidayResponse.Item();
        HolidayResponse.Item date_2024_05_15 = new HolidayResponse.Item();

        date_2024_05_05.setLocDate("20240505");
        date_2024_05_06.setLocDate("20240506");
        date_2024_05_15.setLocDate("20240515");

        List<HolidayResponse.Item> fakeItems = List.of(date_2024_05_05, date_2024_05_06, date_2024_05_15);
        body.setItems(fakeItems);
        fakeResponse.setBody(body);

        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenReturn(fakeResponse);

        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2024, 5));

        assertThat(numberOfStandardWorkingDays).isEqualTo(21L);
    }

    @Test
    void 공휴일이_없는_달의_API_응답을_처리한다() {
        when(apiProperties.combineURL(any(), any()))
                .thenReturn("http://fake-api.com");

        HolidayResponse fakeResponse = new HolidayResponse();
        fakeResponse.setBody(new HolidayResponse.Body());

        when(restTemplate.getForObject(any(URI.class), eq(HolidayResponse.class)))
                .thenReturn(fakeResponse);

        long numberOfStandardWorkingDays = apiConvertor.countNumberOfStandardWorkingDays(YearMonth.of(2024, 6));

        assertThat(numberOfStandardWorkingDays).isEqualTo(20L);
    }
}
