package com.company.officecommute.controller.annual_leave;

import com.company.officecommute.auth.SessionRoleFixture;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveEnrollmentResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse;
import com.company.officecommute.dto.annual_leave.response.AnnualLeaveGetRemainingResponse.RemainingLeave;
import com.company.officecommute.service.annual_leave.AnnualLeaveService;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@SpringBootTest
@AutoConfigureMockMvc
class AnnualLeaveControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void stubSessionRoles() {
        SessionRoleFixture.stubSessionRoles(employeeRepository);
    }

    @MockitoBean
    private AnnualLeaveService annualLeaveService;

    @Nested
    @DisplayName("POST /annual-leave")
    class EnrollAnnualLeave {

        private static final String VALID_BODY = """
                {
                    "wantedDates": ["2099-01-05", "2099-01-06"]
                }
                """;

        @Test
        @DisplayName("정상 신청이면 200과 등록된 연차 목록을 반환한다")
        void enrollReturns200() {
            // given
            given(annualLeaveService.enrollAnnualLeave(eq(2L), eq(List.of(
                    LocalDate.of(2099, 1, 5), LocalDate.of(2099, 1, 6)))))
                    .willReturn(List.of(
                            new AnnualLeaveEnrollmentResponse(1L, LocalDate.of(2099, 1, 5)),
                            new AnnualLeaveEnrollmentResponse(2L, LocalDate.of(2099, 1, 6))
                    ));

            // when / then
            assertThat(mockMvcTester.post().uri("/api/annual-leave")
                    .session(memberSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            [
                                { "annualLeaveId": 1, "enrolledDate": "2099-01-05" },
                                { "annualLeaveId": 2, "enrolledDate": "2099-01-06" }
                            ]
                            """);
        }

        @Test
        @DisplayName("wantedDates가 없으면 400 VALIDATION_ERROR")
        void missingWantedDatesReturns400() {
            // when / then
            assertThat(mockMvcTester.post().uri("/api/annual-leave")
                    .session(memberSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "VALIDATION_ERROR",
                                "fieldErrorResults": [
                                    { "field": "wantedDates", "message": "신청할 연차 일자는 필수입니다." }
                                ]
                            }
                            """);

            then(annualLeaveService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("wantedDates가 빈 배열이면 400 VALIDATION_ERROR")
        void emptyWantedDatesReturns400() {
            // when / then
            assertThat(mockMvcTester.post().uri("/api/annual-leave")
                    .session(memberSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            { "wantedDates": [] }
                            """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "VALIDATION_ERROR",
                                "fieldErrorResults": [
                                    { "field": "wantedDates", "message": "신청할 연차 일자는 필수입니다." }
                                ]
                            }
                            """);

            then(annualLeaveService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("wantedDates 원소가 null이면 400 VALIDATION_ERROR")
        void nullElementReturns400() {
            // when / then
            assertThat(mockMvcTester.post().uri("/api/annual-leave")
                    .session(memberSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            { "wantedDates": [null] }
                            """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "VALIDATION_ERROR",
                                "fieldErrorResults": [
                                    { "field": "wantedDates[0]", "message": "연차 일자는 null일 수 없습니다." }
                                ]
                            }
                            """);

            then(annualLeaveService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("잘못된 날짜 형식은 해당 필드를 안내하는 INVALID_JSON을 반환한다")
        void invalidDateReturns400() {
            assertThat(mockMvcTester.post().uri("/api/annual-leave")
                    .session(memberSession())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            { "wantedDates": ["2026-13-99"] }
                            """))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "INVALID_JSON",
                                "message": "필드 wantedDates[0]의 값이 올바르지 않습니다."
                            }
                            """);

            then(annualLeaveService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("GET /annual-leave")
    class GetRemainingAnnualLeaves {

        @Test
        @DisplayName("남은 연차를 스펙에 정의된 필드만 포함해 반환한다")
        void getRemainingAnnualLeavesReturns200() {
            // given
            given(annualLeaveService.getRemainingAnnualLeaves(2L))
                    .willReturn(new AnnualLeaveGetRemainingResponse(2L, List.of(
                            new RemainingLeave(1L, 2L, LocalDate.of(2099, 1, 5))
                    )));

            // when / then
            assertThat(mockMvcTester.get().uri("/api/annual-leave")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isEqualTo("""
                            {
                                "employeeId": 2,
                                "remainingLeaves": [
                                    {
                                        "id": 1,
                                        "employeeId": 2,
                                        "wantedDate": "2099-01-05"
                                    }
                                ]
                            }
                            """);
        }
    }

    private MockHttpSession memberSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 2L);
        session.setAttribute("currentRole", Role.MEMBER);
        return session;
    }
}
