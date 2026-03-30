package com.company.officecommute.docs;

import com.company.officecommute.controller.employee.EmployeeController;
import com.company.officecommute.dto.employee.request.EmployeeSaveRequest;
import com.company.officecommute.dto.employee.request.EmployeeUpdateTeamNameRequest;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.service.employee.EmployeeService;
import com.company.officecommute.support.RestDocsSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmployeeControllerDocsTest extends RestDocsSupport {

    private final EmployeeService employeeService = mock(EmployeeService.class);

    @Override
    protected Object initController() {
        return new EmployeeController(employeeService);
    }

    @Test
    @DisplayName("직원 등록 API")
    void saveEmployee() throws Exception {
        doNothing().when(employeeService).registerEmployee(any(EmployeeSaveRequest.class));

        String request = """
                {
                    "name": "홍길동",
                    "role": "MEMBER",
                    "birthday": "1990-01-15",
                    "workStartDate": "2024-01-02",
                    "employeeCode": "EMP002",
                    "pin": "1234"
                }
                """;

        mockMvc.perform(post("/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("employee-save",
                        requestFields(
                                fieldWithPath("name").type(JsonFieldType.STRING)
                                        .description("직원 이름"),
                                fieldWithPath("role").type(JsonFieldType.STRING)
                                        .description("역할 (MANAGER, MEMBER)"),
                                fieldWithPath("birthday").type(JsonFieldType.STRING)
                                        .description("생년월일 (yyyy-MM-dd)"),
                                fieldWithPath("workStartDate").type(JsonFieldType.STRING)
                                        .description("입사일 (yyyy-MM-dd)"),
                                fieldWithPath("employeeCode").type(JsonFieldType.STRING)
                                        .description("사번 (대문자+숫자 6-10자리)"),
                                fieldWithPath("pin").type(JsonFieldType.STRING)
                                        .description("PIN (4~6자리 숫자)")
                        )
                ));
    }

    @Test
    @DisplayName("직원 전체 조회 API")
    void findAllEmployee() throws Exception {
        List<EmployeeFindResponse> responses = List.of(
                new EmployeeFindResponse("홍길동", "개발팀", "MANAGER", "1990-01-15", "2020-03-01"),
                new EmployeeFindResponse("김철수", "개발팀", "MEMBER", "1995-05-20", "2023-01-02")
        );
        given(employeeService.findAllEmployee()).willReturn(responses);

        mockMvc.perform(get("/employee")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("employee-find-all",
                        responseFields(
                                fieldWithPath("[].name").type(JsonFieldType.STRING)
                                        .description("직원 이름"),
                                fieldWithPath("[].teamName").type(JsonFieldType.STRING)
                                        .description("소속 팀 이름"),
                                fieldWithPath("[].role").type(JsonFieldType.STRING)
                                        .description("역할"),
                                fieldWithPath("[].birthday").type(JsonFieldType.STRING)
                                        .description("생년월일"),
                                fieldWithPath("[].workStartDate").type(JsonFieldType.STRING)
                                        .description("입사일")
                        )
                ));
    }

    @Test
    @DisplayName("직원 팀 변경 API")
    void updateEmployeeTeamName() throws Exception {
        doNothing().when(employeeService).updateEmployeeTeamName(any(EmployeeUpdateTeamNameRequest.class));

        EmployeeUpdateTeamNameRequest request = new EmployeeUpdateTeamNameRequest(1L, "기획팀");

        mockMvc.perform(put("/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andDo(document("employee-update-team",
                        requestFields(
                                fieldWithPath("employeeId").type(JsonFieldType.NUMBER)
                                        .description("직원 ID"),
                                fieldWithPath("teamName").type(JsonFieldType.STRING)
                                        .description("변경할 팀 이름")
                        )
                ));
    }
}
