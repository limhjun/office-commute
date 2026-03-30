package com.company.officecommute.docs;

import com.company.officecommute.controller.overtime.OverTimeController;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import com.company.officecommute.support.RestDocsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OverTimeControllerDocsTest extends RestDocsSupport {

    private final OverTimeService overTimeService = mock(OverTimeService.class);
    private final OverTimeReportService overTimeReportService = mock(OverTimeReportService.class);

    @Override
    protected Object initController() {
        return new OverTimeController(overTimeService, overTimeReportService);
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

}
