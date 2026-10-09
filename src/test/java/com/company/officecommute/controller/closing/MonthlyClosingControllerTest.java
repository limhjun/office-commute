package com.company.officecommute.controller.closing;

import com.company.officecommute.auth.SessionRoleFixture;
import com.company.officecommute.domain.closing.ClosingErrorCode;
import com.company.officecommute.domain.closing.ClosingException;
import com.company.officecommute.domain.closing.MonthlyClosingType;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.domain.report.DispatchStatus;
import com.company.officecommute.domain.report.ReportKind;
import com.company.officecommute.dto.closing.response.MonthlyClosingResponse;
import com.company.officecommute.dto.employee.response.EmployeeRef;
import com.company.officecommute.dto.report.response.OverTimeReportDispatchResponse;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.closing.MonthlyClosingService;
import com.company.officecommute.service.report.OverTimeReportDispatchService;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@SpringBootTest
@AutoConfigureMockMvc
class MonthlyClosingControllerTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private MonthlyClosingService monthlyClosingService;

    @MockitoBean
    private OverTimeReportDispatchService dispatchService;

    @BeforeEach
    void stubSessionRoles() {
        SessionRoleFixture.stubSessionRoles(employeeRepository);
        given(employeeRepository.findRoleById(3L)).willReturn(Optional.of(Role.COMMUTE_APPROVER));
    }

    @Test
    @DisplayName("마감을 커밋한 뒤 발송 결과를 함께 돌려준다")
    void close_thenDispatch() {
        given(monthlyClosingService.close(eq(1L), any())).willReturn(closing(MonthlyClosingType.MANUAL));
        given(dispatchService.dispatchAndDescribe(SEPTEMBER)).willReturn(new OverTimeReportDispatchResponse(
                SEPTEMBER, ReportKind.ORIGINAL, DispatchStatus.SENT, 1, Instant.parse("2026-10-09T03:00:00Z"),
                Instant.parse("2026-10-09T03:00:00Z"), null, true, null, null, null));

        assertThat(mockMvcTester.post()
                .uri("/api/monthly-closings")
                .session(session(1L, Role.MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "yearMonth": "2026-09" }
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "closing": { "yearMonth": "2026-09", "type": "MANUAL", "rangeStart": "2026-08-31" },
                          "dispatch": { "kind": "ORIGINAL", "status": "SENT", "finalFileAvailable": true }
                        }
                        """);
    }

    @Test
    @DisplayName("기존 발송 월을 정정 없이 등록하면 다시 발송하지 않는다")
    void legacyConfirmed_doesNotDispatch() {
        given(monthlyClosingService.close(eq(1L), any())).willReturn(closing(MonthlyClosingType.LEGACY_CONFIRMED));

        assertThat(mockMvcTester.post()
                .uri("/api/monthly-closings")
                .session(session(1L, Role.MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "yearMonth": "2026-09", "legacyResolution": "NO_CORRECTION_NEEDED", "note": "확인" }
                        """))
                .hasStatus(HttpStatus.CREATED);
        then(dispatchService).should(never()).dispatchAndDescribe(any());
    }

    @Test
    @DisplayName("미해결이 남은 마감은 409 CLOSING_HAS_UNRESOLVED 이고 발송을 시도하지 않는다")
    void close_unresolvedReturns409() {
        given(monthlyClosingService.close(eq(1L), any()))
                .willThrow(new ClosingException(ClosingErrorCode.CLOSING_HAS_UNRESOLVED));

        assertThat(mockMvcTester.post()
                .uri("/api/monthly-closings")
                .session(session(1L, Role.MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "yearMonth": "2026-09" }
                        """))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "code": "CLOSING_HAS_UNRESOLVED" }
                        """);
        then(dispatchService).should(never()).dispatchAndDescribe(any());
    }

    @Test
    @DisplayName("상위 승인자는 월 마감·관리자 기능에 접근할 수 없다")
    void approverCannotUseManagerFeatures() {
        assertThat(mockMvcTester.post()
                .uri("/api/monthly-closings")
                .session(session(3L, Role.COMMUTE_APPROVER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "yearMonth": "2026-09" }
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mockMvcTester.get().uri("/api/overtime?yearMonth=2026-09").session(session(3L, Role.COMMUTE_APPROVER)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mockMvcTester.get().uri("/api/employee").session(session(3L, Role.COMMUTE_APPROVER)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("세션의 역할이 아니라 현재 DB 역할로 판정한다 — 강등된 관리자의 이전 세션은 마감할 수 없다")
    void staleSessionRoleIsNotTrusted() {
        given(employeeRepository.findRoleById(9L)).willReturn(Optional.of(Role.MEMBER));

        assertThat(mockMvcTester.post()
                .uri("/api/monthly-closings")
                .session(session(9L, Role.MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "yearMonth": "2026-09" }
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);
        then(monthlyClosingService).should(never()).close(any(), any());
    }

    @Test
    @DisplayName("직원이 사라진 세션은 401")
    void sessionForDeletedEmployeeIsUnauthorized() {
        given(employeeRepository.findRoleById(99L)).willReturn(Optional.empty());

        assertThat(mockMvcTester.get().uri("/api/monthly-closings").session(session(99L, Role.MANAGER)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private static MonthlyClosingResponse closing(MonthlyClosingType type) {
        return new MonthlyClosingResponse(SEPTEMBER, type, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30),
                new EmployeeRef(1L, "관리자", "ADMIN001"), Instant.parse("2026-10-09T03:00:00Z"), null);
    }

    private static MockHttpSession session(long employeeId, Role role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", employeeId);
        session.setAttribute("currentRole", role);
        return session;
    }
}
