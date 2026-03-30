package com.company.officecommute.docs;

import com.company.officecommute.controller.annual_leave.AnnualLeaveController;
import com.company.officecommute.domain.annual_leave.AnnualLeave;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse;
import com.company.officecommute.service.annual_leave.AnnualLeaveService;
import com.company.officecommute.support.RestDocsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnualLeaveControllerDocsTest extends RestDocsSupport {

    private final AnnualLeaveService annualLeaveService = mock(AnnualLeaveService.class);

    @Override
    protected Object initController() {
        return new AnnualLeaveController(annualLeaveService);
    }

    @Test
    @DisplayName("연차 신청 API")
    void enrollAnnualLeave() throws Exception {
        List<AnnualLeaveEnrollmentResponse> responses = List.of(
                new AnnualLeaveEnrollmentResponse(1L, LocalDate.now().plusDays(30)),
                new AnnualLeaveEnrollmentResponse(2L, LocalDate.now().plusDays(31))
        );
        given(annualLeaveService.enrollAnnualLeave(anyLong(), any())).willReturn(responses);

        String request = String.format("""
                {
                    "wantedDates": ["%s", "%s"]
                }
                """, LocalDate.now().plusDays(30), LocalDate.now().plusDays(31));

        mockMvc.perform(post("/annual-leave")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr("currentEmployeeId", 1L)
                        .content(request))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("annual-leave-enroll",
                        requestFields(
                                fieldWithPath("wantedDates").type(JsonFieldType.ARRAY)
                                        .description("신청할 연차 날짜 목록 (yyyy-MM-dd)")
                        ),
                        responseFields(
                                fieldWithPath("[].annualLeaveId").type(JsonFieldType.NUMBER)
                                        .description("등록된 연차 ID"),
                                fieldWithPath("[].enrolledDate").type(JsonFieldType.STRING)
                                        .description("등록된 연차 날짜")
                        )
                ));
    }

    @Test
    @DisplayName("남은 연차 조회 API")
    void getRemainingAnnualLeaves() throws Exception {
        List<AnnualLeave> remainingLeaves = List.of(
                new AnnualLeave(1L, 1L, LocalDate.now().plusDays(30)),
                new AnnualLeave(2L, 1L, LocalDate.now().plusDays(60))
        );
        AnnualLeaveGetRemainingResponse response = new AnnualLeaveGetRemainingResponse(1L, remainingLeaves);
        given(annualLeaveService.getRemainingAnnualLeaves(anyLong())).willReturn(response);

        mockMvc.perform(get("/annual-leave")
                        .accept(MediaType.APPLICATION_JSON)
                        .requestAttr("currentEmployeeId", 1L))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("annual-leave-remaining",
                        responseFields(
                                fieldWithPath("employeeId").type(JsonFieldType.NUMBER)
                                        .description("직원 ID"),
                                fieldWithPath("remainingLeaves").type(JsonFieldType.ARRAY)
                                        .description("남은 연차 목록"),
                                fieldWithPath("remainingLeaves[].id").type(JsonFieldType.NUMBER)
                                        .description("연차 ID"),
                                fieldWithPath("remainingLeaves[].employeeId").type(JsonFieldType.NUMBER)
                                        .description("직원 ID"),
                                fieldWithPath("remainingLeaves[].wantedDate").type(JsonFieldType.STRING)
                                        .description("연차 날짜"),
                                fieldWithPath("remainingLeaves[].date").type(JsonFieldType.STRING)
                                        .description("연차 날짜 (alias)"),
                                fieldWithPath("remainingLeaves[].remain").type(JsonFieldType.BOOLEAN)
                                        .description("사용 가능 여부")
                        )
                ));
    }
}
