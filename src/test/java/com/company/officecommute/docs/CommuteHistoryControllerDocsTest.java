package com.company.officecommute.docs;

import com.company.officecommute.controller.commute.CommuteHistoryController;
import com.company.officecommute.domain.commute.Detail;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.service.commute.CommuteHistoryService;
import com.company.officecommute.support.RestDocsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommuteHistoryControllerDocsTest extends RestDocsSupport {

    private final CommuteHistoryService commuteHistoryService = mock(CommuteHistoryService.class);

    @Override
    protected Object initController() {
        return new CommuteHistoryController(commuteHistoryService);
    }

    @Test
    @DisplayName("출근 등록 API")
    void registerWorkStartTime() throws Exception {
        doNothing().when(commuteHistoryService).registerWorkStartTime(anyLong());

        mockMvc.perform(post("/commute")
                        .requestAttr("currentEmployeeId", 1L))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("commute-start"));
    }

    @Test
    @DisplayName("퇴근 등록 API")
    void registerWorkEndTime() throws Exception {
        doNothing().when(commuteHistoryService).registerWorkEndTime(anyLong(), any());

        mockMvc.perform(put("/commute")
                        .requestAttr("currentEmployeeId", 1L))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("commute-end"));
    }

    @Test
    @DisplayName("월별 근무시간 조회 API")
    void getWorkDurationPerDate() throws Exception {
        List<Detail> details = List.of(
                new Detail(LocalDate.of(2024, 5, 2), 480, false),
                new Detail(LocalDate.of(2024, 5, 3), 540, false),
                new Detail(LocalDate.of(2024, 5, 6), 0, true)
        );
        WorkDurationPerDateResponse response = new WorkDurationPerDateResponse(details, 1020);
        given(commuteHistoryService.getWorkDurationPerDate(anyLong(), any(YearMonth.class)))
                .willReturn(response);

        mockMvc.perform(get("/commute")
                        .param("yearMonth", "2024-05")
                        .accept(MediaType.APPLICATION_JSON)
                        .requestAttr("currentEmployeeId", 1L))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("commute-duration",
                        queryParameters(
                                parameterWithName("yearMonth").description("조회 년월 (yyyy-MM)")
                        ),
                        responseFields(
                                fieldWithPath("details").type(JsonFieldType.ARRAY)
                                        .description("일별 근무 상세 목록"),
                                fieldWithPath("details[].date").type(JsonFieldType.STRING)
                                        .description("근무 날짜"),
                                fieldWithPath("details[].workingMinutes").type(JsonFieldType.NUMBER)
                                        .description("근무 시간 (분)"),
                                fieldWithPath("details[].usingDayOff").type(JsonFieldType.BOOLEAN)
                                        .description("연차 사용 여부"),
                                fieldWithPath("sumWorkingMinutes").type(JsonFieldType.NUMBER)
                                        .description("총 근무시간 (분)")
                        )
                ));
    }
}
