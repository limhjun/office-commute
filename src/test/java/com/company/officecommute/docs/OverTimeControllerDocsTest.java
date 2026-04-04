package com.company.officecommute.docs;

import com.company.officecommute.controller.overtime.OverTimeController;
import com.company.officecommute.dto.overtime.response.HolidayCacheStatusResponse;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.service.overtime.HolidayCacheStatusService;
import com.company.officecommute.service.overtime.HolidaySyncService;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import com.company.officecommute.support.RestDocsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import java.time.YearMonth;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OverTimeControllerDocsTest extends RestDocsSupport {

    private final OverTimeService overTimeService = mock(OverTimeService.class);
    private final OverTimeReportService overTimeReportService = mock(OverTimeReportService.class);
    private final HolidayCacheStatusService holidayCacheStatusService = mock(HolidayCacheStatusService.class);
    private final HolidaySyncService holidaySyncService = mock(HolidaySyncService.class);

    @Override
    protected Object initController() {
        return new OverTimeController(overTimeService, overTimeReportService, holidayCacheStatusService, holidaySyncService);
    }

    @Test
    @DisplayName("초과근무 조회 API")
    void calculateOverTime() throws Exception {
        List<OverTimeCalculateResponse> responses = List.of(
                new OverTimeCalculateResponse(1L, "홍길동", "개발팀", 120L),
                new OverTimeCalculateResponse(2L, "김철수", "개발팀", 60L),
                new OverTimeCalculateResponse(3L, "이영희", "기획팀", -30L)
        );
        given(overTimeService.calculateOverTime(any(YearMonth.class))).willReturn(responses);

        mockMvc.perform(get("/overtime")
                        .param("yearMonth", "2024-05")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("overtime-calculate",
                        queryParameters(
                                parameterWithName("yearMonth").description("조회 년월 (yyyy-MM)")
                        ),
                        responseFields(
                                fieldWithPath("[].id").type(JsonFieldType.NUMBER)
                                        .description("직원 ID"),
                                fieldWithPath("[].name").type(JsonFieldType.STRING)
                                        .description("직원 이름"),
                                fieldWithPath("[].teamName").type(JsonFieldType.STRING)
                                        .description("소속 팀"),
                                fieldWithPath("[].overTimeMinutes").type(JsonFieldType.NUMBER)
                                        .description("초과근무 시간 (분, 음수면 부족)")
                        )
                ));
    }

    @Test
    @DisplayName("공휴일 캐시 상태 조회 API")
    void getHolidayStatus() throws Exception {
        HolidayCacheStatusResponse response = new HolidayCacheStatusResponse(
                "2026-03",
                2,
                false,
                "STALE_CACHE",
                "공휴일 캐시가 최신 상태가 아니어서 초과근무를 계산할 수 없습니다: 2026-03 (월말 기준 최종 검증 필요)",
                LocalDateTime.of(2026, 3, 20, 9, 0)
        );
        given(holidayCacheStatusService.getStatus(any(YearMonth.class))).willReturn(response);

        mockMvc.perform(get("/overtime/holiday-status")
                        .param("yearMonth", "2026-03")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("overtime-holiday-status",
                        queryParameters(
                                parameterWithName("yearMonth").description("조회 년월 (yyyy-MM)")
                        ),
                        responseFields(
                                fieldWithPath("yearMonth").type(JsonFieldType.STRING)
                                        .description("조회한 년월"),
                                fieldWithPath("cachedHolidayCount").type(JsonFieldType.NUMBER)
                                        .description("DB에 저장된 공휴일 수"),
                                fieldWithPath("cacheUsable").type(JsonFieldType.BOOLEAN)
                                        .description("초과근무 계산에 사용할 수 있는지 여부"),
                                fieldWithPath("status").type(JsonFieldType.STRING)
                                        .description("캐시 상태 코드 (READY, MISSING_CACHE, MISSING_SYNC_STATUS, STALE_CACHE)"),
                                fieldWithPath("reason").type(JsonFieldType.STRING)
                                        .description("현재 상태에 대한 설명"),
                                fieldWithPath("lastSuccessfulSyncedAt").type(JsonFieldType.STRING)
                                        .description("마지막 성공 동기화 시각")
                        )
                ));
    }

    @Test
    @DisplayName("공휴일 재동기화 API")
    void syncHoliday() throws Exception {
        HolidayCacheStatusResponse response = new HolidayCacheStatusResponse(
                "2026-03",
                2,
                true,
                "READY",
                "초과근무 계산에 사용할 수 있는 공휴일 캐시입니다.",
                LocalDateTime.of(2026, 4, 4, 9, 0)
        );
        given(holidaySyncService.refreshAndGetStatus(any(YearMonth.class))).willReturn(response);

        mockMvc.perform(post("/overtime/holiday-sync?yearMonth={yearMonth}", "2026-03")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("overtime-holiday-sync",
                        queryParameters(
                                parameterWithName("yearMonth").description("재동기화할 년월 (yyyy-MM)")
                        ),
                        responseFields(
                                fieldWithPath("yearMonth").type(JsonFieldType.STRING)
                                        .description("조회한 년월"),
                                fieldWithPath("cachedHolidayCount").type(JsonFieldType.NUMBER)
                                        .description("DB에 저장된 공휴일 수"),
                                fieldWithPath("cacheUsable").type(JsonFieldType.BOOLEAN)
                                        .description("초과근무 계산에 사용할 수 있는지 여부"),
                                fieldWithPath("status").type(JsonFieldType.STRING)
                                        .description("캐시 상태 코드"),
                                fieldWithPath("reason").type(JsonFieldType.STRING)
                                        .description("현재 상태에 대한 설명"),
                                fieldWithPath("lastSuccessfulSyncedAt").type(JsonFieldType.STRING)
                                        .description("마지막 성공 동기화 시각")
                        )
                ));
    }

}
