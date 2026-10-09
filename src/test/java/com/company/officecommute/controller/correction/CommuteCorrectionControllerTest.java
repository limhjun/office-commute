package com.company.officecommute.controller.correction;

import com.company.officecommute.auth.SessionRoleFixture;
import com.company.officecommute.domain.correction.CorrectionErrorCode;
import com.company.officecommute.domain.correction.CorrectionException;
import com.company.officecommute.domain.correction.CorrectionStatus;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.correction.response.CorrectionRequestResponse;
import com.company.officecommute.dto.employee.response.EmployeeRef;
import com.company.officecommute.repository.employee.EmployeeRepository;
import com.company.officecommute.service.correction.CommuteCorrectionService;
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
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@SpringBootTest
@AutoConfigureMockMvc
class CommuteCorrectionControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private CommuteCorrectionService commuteCorrectionService;

    @BeforeEach
    void stubSessionRoles() {
        SessionRoleFixture.stubSessionRoles(employeeRepository);
    }

    @Test
    @DisplayName("POST /api/commute-corrections — 201 과 요청 상태를 돌려준다")
    void submit_returns201() {
        given(commuteCorrectionService.submit(eq(2L), any())).willReturn(pendingResponse());

        assertThat(mockMvcTester.post()
                .uri("/api/commute-corrections")
                .session(session(2L, Role.MEMBER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "commuteHistoryId": 41,
                          "commuteVersion": 0,
                          "requestedWorkEndTime": "2026-09-02T19:30:00+09:00",
                          "reason": "퇴근 버튼 누락"
                        }
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "requestId": 7,
                          "status": "PENDING",
                          "requestedWorkEndTime": "2026-09-02T19:30:00+09:00",
                          "actions": { "canCancel": true, "canReview": false }
                        }
                        """);
    }

    @Test
    @DisplayName("사유가 비면 VALIDATION_ERROR 와 reason 필드 오류")
    void submit_blankReasonReturnsFieldError() {
        assertThat(mockMvcTester.post()
                .uri("/api/commute-corrections")
                .session(session(2L, Role.MEMBER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "commuteHistoryId": 41,
                          "commuteVersion": 0,
                          "requestedWorkEndTime": "2026-09-02T19:30:00+09:00",
                          "reason": "   "
                        }
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "code": "VALIDATION_ERROR",
                          "fieldErrorResults": [ { "field": "reason" } ]
                        }
                        """);
        then(commuteCorrectionService).should(never()).submit(any(), any());
    }

    @Test
    @DisplayName("오프셋 없는 종료 시각은 INVALID_JSON — 브라우저 시간대로 추측하지 않는다")
    void submit_requiresOffset() {
        assertThat(mockMvcTester.post()
                .uri("/api/commute-corrections")
                .session(session(2L, Role.MEMBER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "commuteHistoryId": 41,
                          "commuteVersion": 0,
                          "requestedWorkEndTime": "2026-09-02T19:30:00",
                          "reason": "사유"
                        }
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "code": "INVALID_JSON" }
                        """);
    }

    @Test
    @DisplayName("정정 도메인 오류는 코드별 HTTP 상태로 내려간다 — 버전 충돌 409")
    void approve_versionConflictReturns409() {
        given(commuteCorrectionService.approve(1L, 7L, null))
                .willThrow(new CorrectionException(CorrectionErrorCode.CORRECTION_VERSION_CONFLICT));

        assertThat(mockMvcTester.post()
                .uri("/api/commute-corrections/7/approve")
                .session(session(1L, Role.MANAGER)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "code": "CORRECTION_VERSION_CONFLICT",
                          "message": "근태 기록이 변경되었습니다. 최신 내용을 확인해 주세요."
                        }
                        """);
    }

    @Test
    @DisplayName("자기 승인은 403 CORRECTION_SELF_APPROVAL")
    void approve_selfApprovalReturns403() {
        given(commuteCorrectionService.approve(eq(1L), eq(7L), any()))
                .willThrow(new CorrectionException(CorrectionErrorCode.CORRECTION_SELF_APPROVAL));

        assertThat(mockMvcTester.post()
                .uri("/api/commute-corrections/7/approve")
                .session(session(1L, Role.MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "comment": "ok" }
                        """))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "code": "CORRECTION_SELF_APPROVAL" }
                        """);
    }

    @Test
    @DisplayName("반려 사유가 없으면 VALIDATION_ERROR")
    void reject_requiresReason() {
        assertThat(mockMvcTester.post()
                .uri("/api/commute-corrections/7/reject")
                .session(session(1L, Role.MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "code": "VALIDATION_ERROR", "fieldErrorResults": [ { "field": "reason" } ] }
                        """);
    }

    @Test
    @DisplayName("비로그인은 401")
    void unauthenticated() {
        assertThat(mockMvcTester.get().uri("/api/commute-corrections/mine"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("다른 출처의 상태 변경 요청은 인증보다 먼저 403 CSRF_ORIGIN_REJECTED")
    void crossOriginWriteRejected() {
        assertThat(mockMvcTester.post()
                .uri("/api/commute-corrections/7/approve")
                .session(session(1L, Role.MANAGER))
                .header("Origin", "https://evil.example.com"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .isLenientlyEqualTo("""
                        { "code": "CSRF_ORIGIN_REJECTED" }
                        """);
        then(commuteCorrectionService).should(never()).approve(any(), any(), any());
    }

    @Test
    @DisplayName("같은 출처의 상태 변경 요청은 통과한다")
    void sameOriginWriteAllowed() {
        given(commuteCorrectionService.cancel(2L, 7L)).willReturn(pendingResponse());

        assertThat(mockMvcTester.post()
                .uri("/api/commute-corrections/7/cancel")
                .session(session(2L, Role.MEMBER))
                .header("Host", "office-commute.com")
                .header("Origin", "https://office-commute.com"))
                .hasStatus(HttpStatus.OK);
    }

    private static MockHttpSession session(long employeeId, Role role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", employeeId);
        session.setAttribute("currentRole", role);
        return session;
    }

    private static CorrectionRequestResponse pendingResponse() {
        return new CorrectionRequestResponse(
                7L, CorrectionStatus.PENDING, 41L, 0L,
                new EmployeeRef(2L, "홍길동", "EMP0002"), Role.MEMBER, null,
                LocalDate.of(2026, 9, 2), "Asia/Seoul",
                OffsetDateTime.parse("2026-09-02T09:01:00+09:00"), null, 0L,
                OffsetDateTime.parse("2026-09-02T19:30:00+09:00"), 629L,
                "퇴근 버튼 누락", Instant.parse("2026-10-09T01:00:00Z"),
                null, null, null,
                new CorrectionRequestResponse.Actions(true, false));
    }
}
