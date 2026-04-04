package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.HolidayCacheStatusResponse;
import com.company.officecommute.web.ApiConvertor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class HolidaySyncServiceTest {

    @InjectMocks private HolidaySyncService holidaySyncService;

    @Mock private ApiConvertor apiConvertor;
    @Mock private HolidayCacheStatusService holidayCacheStatusService;

    @Test
    @DisplayName("월별 공휴일 재동기화 후 최신 상태를 반환한다")
    void refreshAndGetStatus() {
        YearMonth yearMonth = YearMonth.of(2026, 3);
        HolidayCacheStatusResponse status = new HolidayCacheStatusResponse(
                "2026-03",
                2,
                true,
                "READY",
                "초과근무 계산에 사용할 수 있는 공휴일 캐시입니다.",
                LocalDateTime.of(2026, 4, 4, 9, 0)
        );

        given(holidayCacheStatusService.getStatus(yearMonth)).willReturn(status);

        HolidayCacheStatusResponse result = holidaySyncService.refreshAndGetStatus(yearMonth);

        then(apiConvertor).should().refreshHolidays(yearMonth);
        then(holidayCacheStatusService).should().getStatus(yearMonth);
        assertThat(result).isEqualTo(status);
    }
}
