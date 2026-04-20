package com.company.officecommute.controller.auth;

import com.company.officecommute.domain.employee.Employee;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.service.employee.EmployeeBuilder;
import com.company.officecommute.service.employee.EmployeeService;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmployeeService employeeService;

    private final Employee managerEmployee = new EmployeeBuilder()
            .withId(1L)
            .withName("관리자")
            .withRole(Role.MANAGER)
            .withBirthday(LocalDate.of(1990, 1, 1))
            .withStartDate(LocalDate.of(2020, 1, 1))
            .withEmployeeCode("ADMIN001")
            .withEmail("admin@company.com")
            .withPassword("password123")
            .build();

    @Test
    @DisplayName("로그인 성공 시 세션에 직원 정보가 저장된다")
    void login_success() {
        given(employeeService.authenticate("admin@company.com", "password123"))
                .willReturn(managerEmployee);

        MockHttpSession session = new MockHttpSession();

        assertThat(mockMvcTester
                .post()
                .uri("/api/auth/login")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "admin@company.com",
                            "password": "password123"
                        }
                        """))
                .hasStatus(HttpStatus.OK);

        assertThat(session.getAttribute("currentEmployeeId")).isEqualTo(1L);
        assertThat(session.getAttribute("currentRole")).isEqualTo(Role.MANAGER);
    }

    @Test
    @DisplayName("잘못된 자격 증명으로 로그인 시 401 응답")
    void login_invalidCredentials() {
        when(employeeService.authenticate("admin@company.com", "wrongpassword"))
                .thenThrow(new IllegalArgumentException("비밀번호가 일치하지 않습니다."));

        assertThat(mockMvcTester
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "admin@company.com",
                            "password": "wrongpassword"
                        }
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("email이 빈 값이면 400 응답")
    void login_missingEmail() {
        assertThat(mockMvcTester
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "email": "",
                            "password": "password123"
                        }
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("로그아웃 시 세션이 무효화된다")
    void logout_invalidatesSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 1L);
        session.setAttribute("currentRole", Role.MANAGER);

        assertThat(mockMvcTester
                .post()
                .uri("/api/auth/logout")
                .session(session))
                .hasStatus(HttpStatus.OK);

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("세션이 없어도 로그아웃은 성공한다")
    void logout_withoutSession() {
        assertThat(mockMvcTester
                .post()
                .uri("/api/auth/logout"))
                .hasStatus(HttpStatus.OK);
    }
}
