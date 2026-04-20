package com.company.officecommute.controller.team;

import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.service.team.TeamService;
import org.junit.jupiter.api.DisplayName;
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

@SpringBootTest
@AutoConfigureMockMvc
class TeamControllerTest {

    @Autowired
    private MockMvcTester mockMvcTest;

    @MockitoBean
    private TeamService teamService;

    @Test
    @DisplayName("유효하지 않은 팀 이름으로 팀 등록 요청 시 예외 발생")
    void register_withInvalidName() {
        String requestBody = """
                {
                    "teamName": ""
                }
                """;
        assertThat(mockMvcTest
                .post()
                .uri("/team")
                .session(managerSession())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo(
                        """
                                {
                                    "code": "VALIDATION_ERROR",
                                    "message": "입력값이 올바르지 않습니다",
                                    "fieldErrorResults": [
                                        {
                                            "field": "teamName",
                                            "message": "팀 이름은 비어있을 수 없습니다"
                                        }
                                    ]
                                }
                                """
                );

    }

    private MockHttpSession managerSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 1L);
        session.setAttribute("currentRole", Role.MANAGER);
        return session;
    }
}
