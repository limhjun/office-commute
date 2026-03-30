package com.company.officecommute.controller.team;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.service.employee.EmployeeBuilder;
import com.company.officecommute.service.employee.EmployeeService;
import com.company.officecommute.service.team.TeamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@AutoConfigureMockMvc
class TeamControllerTest {

    @Autowired
    private MockMvcTester mockMvcTest;

    @MockitoBean
    private TeamService teamService;

    @MockitoBean
    private EmployeeService employeeService;

    private final Employee managerEmployee = new EmployeeBuilder()
            .withId(1L)
            .withName("관리자")
            .withRole(Role.MANAGER)
            .withBirthday(LocalDate.of(1990, 1, 1))
            .withStartDate(LocalDate.of(2020, 1, 1))
            .withEmployeeCode("ADMIN001")
            .withPin("1234")
            .build();

    @Test
    @DisplayName("유효하지 않은 팀 이름으로 팀 등록 요청 시 예외 발생")
    void register_withInvalidName() {
        given(employeeService.authenticate("ADMIN001", "1234"))
                .willReturn(managerEmployee);

        String requestBody = """
                {
                    "teamName": ""
                }
                """;
        assertThat(mockMvcTest
                .post()
                .uri("/team")
                .header("X-Employee-Code", "ADMIN001")
                .header("X-Employee-Pin", "1234")
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
}
