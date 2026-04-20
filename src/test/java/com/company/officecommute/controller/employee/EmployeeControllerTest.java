package com.company.officecommute.controller.employee;

import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.service.employee.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
class EmployeeControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmployeeService employeeService;

    @Nested
    @DisplayName("권한 테스트")
    class AuthorizationTests {

        @Test
        @DisplayName("MANAGER 권한이 없는 경우 직원 등록 요청 시 예외 발생")
        void testUnauthorizedAccess() {
            String requestBody = """
                        {
                            "name": "John",
                            "role": "MEMBER",
                            "birthday": "1990-01-01",
                            "workStartDate": "2020-01-01",
                            "employeeCode": "E00001",
                            "email": "john@company.com",
                            "password": "password123"
                        }
                    """;

            assertThat(mockMvcTester
                    .post()
                    .uri("/employee")
                    .session(memberSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("MANAGER 권한이 있는 경우 직원 등록 요청 성공")
        void testAuthorizedAccess() {
            String requestBody = """
                        {
                            "name": "John",
                            "role": "MEMBER",
                            "birthday": "1990-01-01",
                            "workStartDate": "2020-01-01",
                            "employeeCode": "E00001",
                            "email": "john@company.com",
                            "password": "password123"
                        }
                    """;

            assertThat(mockMvcTester
                    .post()
                    .uri("/employee")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
                    .hasStatus(HttpStatus.OK);
        }
    }

    @Test
    @DisplayName("유효하지 않은 값들로 직원 등록 요청 시 예외 발생")
    void testValidInputFailsValidation() {
        String invalidRequest = """
                    {
                        "name": "",
                        "role": "MEMBER",
                        "birthday": "2030-01-01",
                        "workStartDate": "2099-12-31",
                        "employeeCode": "E00001",
                        "email": "john@company.com",
                        "password": "password123"
                    }
            """;

        assertThat(mockMvcTester
                .post()
                .uri("/employee")
                .session(managerSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                            {
                                "code": "VALIDATION_ERROR",
                                "message": "입력값이 올바르지 않습니다",
                                "fieldErrorResults": [
                                    { "field": "name", "message": "직원 이름은 필수입니다." },
                                    { "field": "birthday", "message": "생일은 과거 날짜여야 합니다." },
                                    { "field": "workStartDate", "message": "입사일은 오늘이거나 과거 날짜여야 합니다." }
                                ]
                            }
                        """);
    }

    @Test
    @DisplayName("존재하지 않는 역할 값 입력 시 예외 발생")
    void testInvalidEnumFailsJsonParsing() {
        String invalidEnumRequest = """
                    {
                        "name": "John",
                        "role": "CEO",
                        "birthday": "1990-01-01",
                        "workStartDate": "2020-01-01"
                    }
                """;

        assertThat(mockMvcTester
                .post()
                .uri("/employee")
                .session(managerSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidEnumRequest))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                            {
                                "code": "INVALID_JSON",
                                "message": "역할 값이 올바르지 않습니다."
                            }
                        """);
    }

    @Test
    @DisplayName("존재하지 않는 팀 배정시 예외 발생")
    void update_nonExistTeam() {
        doThrow(new IllegalArgumentException("해당하는 팀명(없는팀)이 없습니다."))
                .when(employeeService).updateEmployeeTeamName(any());

        assertThat(mockMvcTester.put().uri("/employee")
                .session(managerSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "employeeId": 1,
                            "teamName": "없는팀"
                        }
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.message").isEqualTo("해당하는 팀명(없는팀)이 없습니다.");
    }

    private MockHttpSession managerSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 1L);
        session.setAttribute("currentRole", Role.MANAGER);
        return session;
    }

    private MockHttpSession memberSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 2L);
        session.setAttribute("currentRole", Role.MEMBER);
        return session;
    }
}
