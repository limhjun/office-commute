package com.company.officecommute.controller.employee;

import com.company.officecommute.domain.employee.EmployeeAlreadyExistsException;
import com.company.officecommute.domain.employee.EmployeeNotFoundException;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.TeamNotFoundException;
import com.company.officecommute.dto.employee.response.EmployeeFindResponse;
import com.company.officecommute.dto.employee.response.EmployeeRegisterResponse;
import com.company.officecommute.service.employee.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest
@AutoConfigureMockMvc
class EmployeeControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmployeeService employeeService;

    @Nested
    @DisplayName("POST /employee")
    class Register {

        private static final String VALID_BODY = """
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

        @Test
        @DisplayName("MANAGER 권한이 없으면 403")
        void unauthorized() {
            assertThat(mockMvcTester.post().uri("/employee")
                    .session(memberSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("로그인 세션이 없으면 401")
        void unauthenticated() {
            assertThat(mockMvcTester.post().uri("/employee")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("MANAGER가 등록하면 201 Created + employeeId 반환")
        void created() {
            BDDMockito.given(employeeService.registerEmployee(any()))
                    .willReturn(new EmployeeRegisterResponse(42L));

            assertThat(mockMvcTester.post().uri("/employee")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            { "employeeId": 42 }
                            """);
        }

        @Test
        @DisplayName("필수값 누락 / 미래 날짜 등 위반 시 400 + VALIDATION_ERROR")
        void validationError() {
            String invalid = """
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

            assertThat(mockMvcTester.post().uri("/employee")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalid))
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
        @DisplayName("존재하지 않는 Role 값은 INVALID_JSON")
        void invalidEnum() {
            String invalid = """
                    {
                        "name": "John",
                        "role": "CEO",
                        "birthday": "1990-01-01",
                        "workStartDate": "2020-01-01"
                    }
                    """;

            assertThat(mockMvcTester.post().uri("/employee")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalid))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            { "code": "INVALID_JSON", "message": "역할 값이 올바르지 않습니다." }
                            """);
        }

        @Test
        @DisplayName("중복된 employeeCode/email로 등록하면 409 + EMPLOYEE_ALREADY_EXISTS")
        void duplicate() {
            BDDMockito.given(employeeService.registerEmployee(any()))
                    .willThrow(EmployeeAlreadyExistsException.ofEmployeeCode("E00001"));

            assertThat(mockMvcTester.post().uri("/employee")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("EMPLOYEE_ALREADY_EXISTS");
        }

        @Test
        @DisplayName("존재하지 않는 teamId는 404 + TEAM_NOT_FOUND")
        void teamNotFound() {
            BDDMockito.given(employeeService.registerEmployee(any()))
                    .willThrow(new TeamNotFoundException(99L));

            String body = """
                    {
                        "name": "John",
                        "role": "MEMBER",
                        "birthday": "1990-01-01",
                        "workStartDate": "2020-01-01",
                        "employeeCode": "E00001",
                        "email": "john@company.com",
                        "password": "password123",
                        "teamId": 99
                    }
                    """;

            assertThat(mockMvcTester.post().uri("/employee")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("TEAM_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("GET /employee")
    class FindAll {

        @Test
        @DisplayName("MANAGER 권한이 없으면 403")
        void unauthorized() {
            assertThat(mockMvcTester.get().uri("/employee").session(memberSession()))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("응답에 employeeId, teamId, teamName, ISO-8601 날짜를 포함")
        void responseShape() {
            BDDMockito.given(employeeService.findAllEmployee()).willReturn(List.of(
                    new EmployeeFindResponse(1L, 10L, "백엔드팀", "임형준", "MEMBER",
                            LocalDate.of(1998, 8, 18), LocalDate.of(2024, 1, 1), "Asia/Seoul"),
                    new EmployeeFindResponse(2L, null, null, "미배정직원", "MEMBER",
                            LocalDate.of(1990, 1, 1), LocalDate.of(2024, 3, 1), "America/Los_Angeles")
            ));

            assertThat(mockMvcTester.get().uri("/employee").session(managerSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            [
                                {
                                    "employeeId": 1, "teamId": 10, "teamName": "백엔드팀",
                                    "name": "임형준", "role": "MEMBER",
                                    "birthday": "1998-08-18", "workStartDate": "2024-01-01"
                                },
                                {
                                    "employeeId": 2, "teamId": null, "teamName": null,
                                    "name": "미배정직원", "role": "MEMBER",
                                    "birthday": "1990-01-01", "workStartDate": "2024-03-01"
                                }
                            ]
                            """);
        }
    }

    @Nested
    @DisplayName("PUT /employee/{employeeId}/team")
    class ChangeTeam {

        @Test
        @DisplayName("MANAGER 권한이 없으면 403")
        void unauthorized() {
            assertThat(mockMvcTester.put().uri("/employee/1/team")
                    .session(memberSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"teamId\": 2}"))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("teamId로 팀 변경 성공 시 200")
        void changeToTeam() {
            assertThat(mockMvcTester.put().uri("/employee/1/team")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"teamId\": 2}"))
                    .hasStatus(HttpStatus.OK);

            BDDMockito.verify(employeeService).changeTeam(eq(1L), eq(2L));
        }

        @Test
        @DisplayName("teamId가 null이면 미배정 상태로 변경 가능")
        void changeToUnassigned() {
            assertThat(mockMvcTester.put().uri("/employee/1/team")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"teamId\": null}"))
                    .hasStatus(HttpStatus.OK);

            BDDMockito.verify(employeeService).changeTeam(eq(1L), eq(null));
        }

        @Test
        @DisplayName("존재하지 않는 employeeId는 404 + EMPLOYEE_NOT_FOUND")
        void employeeNotFound() {
            BDDMockito.willThrow(new EmployeeNotFoundException(99L))
                    .given(employeeService).changeTeam(eq(99L), eq(2L));

            assertThat(mockMvcTester.put().uri("/employee/99/team")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"teamId\": 2}"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("EMPLOYEE_NOT_FOUND");
        }

        @Test
        @DisplayName("존재하지 않는 teamId는 404 + TEAM_NOT_FOUND")
        void teamNotFound() {
            BDDMockito.willThrow(new TeamNotFoundException(99L))
                    .given(employeeService).changeTeam(eq(1L), eq(99L));

            assertThat(mockMvcTester.put().uri("/employee/1/team")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"teamId\": 99}"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("TEAM_NOT_FOUND");
        }
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
