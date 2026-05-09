package com.company.officecommute.controller.team;

import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.team.TeamAlreadyExistsException;
import com.company.officecommute.dto.team.response.TeamFindResponse;
import com.company.officecommute.dto.team.response.TeamRegisterResponse;
import com.company.officecommute.service.team.TeamService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
class TeamControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private TeamService teamService;

    @Nested
    @DisplayName("팀 등록 권한")
    class RegisterAuthorization {

        @Test
        @DisplayName("비인증 요청은 401")
        void unauthenticated() {
            String body = """
                    { "teamName": "개발팀", "managerName": "홍길동" }
                    """;
            assertThat(mockMvcTester
                    .post()
                    .uri("/team")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("MEMBER 권한 요청은 403")
        void memberForbidden() {
            String body = """
                    { "teamName": "개발팀", "managerName": "홍길동" }
                    """;
            assertThat(mockMvcTester
                    .post()
                    .uri("/team")
                    .session(memberSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("MANAGER 권한 요청은 201과 팀 ID를 반환")
        void managerSucceeds() {
            given(teamService.registerTeam(any()))
                    .willReturn(new TeamRegisterResponse(1L));

            String body = """
                    { "teamName": "개발팀", "managerName": "홍길동" }
                    """;
            assertThat(mockMvcTester
                    .post()
                    .uri("/team")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            { "teamId": 1 }
                            """);
        }
    }

    @Nested
    @DisplayName("팀 등록 입력 검증")
    class RegisterValidation {

        @Test
        @DisplayName("매니저 없이도 등록 성공")
        void registerWithoutManager() {
            given(teamService.registerTeam(any()))
                    .willReturn(new TeamRegisterResponse(1L));

            String body = """
                    { "teamName": "개발팀" }
                    """;
            assertThat(mockMvcTester
                    .post()
                    .uri("/team")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .hasStatus(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("매니저가 빈 문자열이어도 등록 성공")
        void registerWithBlankManager() {
            given(teamService.registerTeam(any()))
                    .willReturn(new TeamRegisterResponse(1L));

            String body = """
                    { "teamName": "개발팀", "managerName": "" }
                    """;
            assertThat(mockMvcTester
                    .post()
                    .uri("/team")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .hasStatus(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("연차 등록 기준이 음수이면 400 VALIDATION_ERROR")
        void negativeAnnualLeaveCriteriaReturns400() {
            String body = """
                    { "teamName": "개발팀", "annualLeaveCriteria": -1 }
                    """;
            assertThat(mockMvcTester
                    .post()
                    .uri("/team")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "VALIDATION_ERROR",
                                "message": "입력값이 올바르지 않습니다",
                                "fieldErrorResults": [
                                    { "field": "annualLeaveCriteria", "message": "팀 연차 등록 기준은 0 이상이어야 합니다." }
                                ]
                            }
                            """);
        }

        @Test
        @DisplayName("팀 이름이 빈 문자열이면 400 VALIDATION_ERROR")
        void blankTeamNameReturns400() {
            String body = """
                    { "teamName": "", "managerName": "홍길동" }
                    """;
            assertThat(mockMvcTester
                    .post()
                    .uri("/team")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "VALIDATION_ERROR",
                                "message": "입력값이 올바르지 않습니다",
                                "fieldErrorResults": [
                                    { "field": "teamName", "message": "팀 이름은 비어있을 수 없습니다" }
                                ]
                            }
                            """);
        }
    }

    @Nested
    @DisplayName("팀 등록 충돌")
    class RegisterConflict {

        @Test
        @DisplayName("이미 존재하는 팀 이름이면 409 TEAM_ALREADY_EXISTS")
        void duplicateNameReturns409() {
            doThrow(new TeamAlreadyExistsException("개발팀"))
                    .when(teamService).registerTeam(any());

            String body = """
                    { "teamName": "개발팀", "managerName": "홍길동" }
                    """;
            assertThat(mockMvcTester
                    .post()
                    .uri("/team")
                    .session(managerSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "TEAM_ALREADY_EXISTS",
                                "message": "이미 존재하는 팀입니다: 개발팀"
                            }
                            """);
        }
    }

    @Nested
    @DisplayName("팀 조회")
    class FindTeams {

        @Test
        @DisplayName("비인증 요청은 401")
        void unauthenticated() {
            assertThat(mockMvcTester.get().uri("/team"))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("응답에 teamId/name/managerName/annualLeaveCriteria/memberCount 모두 포함, 매니저 미배정은 null")
        void returnsAllFieldsIncludingNullManager() {
            given(teamService.findTeam()).willReturn(List.of(
                    new TeamFindResponse(1L, "개발팀", "홍길동", 2, 5),
                    new TeamFindResponse(2L, "기획팀", null, 0, 3)
            ));

            assertThat(mockMvcTester
                    .get()
                    .uri("/team")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            [
                              { "teamId": 1, "name": "개발팀", "managerName": "홍길동", "annualLeaveCriteria": 2, "memberCount": 5 },
                              { "teamId": 2, "name": "기획팀", "managerName": null,    "annualLeaveCriteria": 0, "memberCount": 3 }
                            ]
                            """);
        }

        @Test
        @DisplayName("팀이 없으면 빈 배열 반환")
        void emptyList() {
            given(teamService.findTeam()).willReturn(List.of());

            assertThat(mockMvcTester
                    .get()
                    .uri("/team")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isLenientlyEqualTo("[]");
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
