package com.company.officecommute.controller.commute;

import com.company.officecommute.auth.SessionRoleFixture;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import com.company.officecommute.domain.closing.CommuteLockReason;
import com.company.officecommute.domain.commute.CommuteStatus;
import com.company.officecommute.domain.commute.DuplicateWorkOnDateException;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.commute.response.CommuteDetailResponse;
import com.company.officecommute.dto.commute.response.RegularEndTargetResponse;
import com.company.officecommute.dto.commute.response.WorkDurationPerDateResponse;
import com.company.officecommute.service.commute.CommuteHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
class CommuteHistoryControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void stubSessionRoles() {
        SessionRoleFixture.stubSessionRoles(employeeRepository);
    }

    @MockitoBean
    private CommuteHistoryService commuteHistoryService;

    @Test
    @DisplayName("POST /commute — 같은 날 중복 출근이면 409 DUPLICATE_WORK")
    void registerWorkStartTime_duplicateWorkReturns409() {
        doThrow(new DuplicateWorkOnDateException(LocalDate.of(2026, 5, 23)))
                .when(commuteHistoryService).registerWorkStartTime(2L);

        assertThat(mockMvcTester
                .post()
                .uri("/api/commute")
                .session(memberSession()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "code": "DUPLICATE_WORK",
                            "message": "해당 일자에 이미 출근 기록이 존재합니다: 2026-05-23"
                        }
                        """);
    }

    @Test
    @DisplayName("GET /commute — yearMonth로 조회하면 200과 월별 근무 시간을 반환한다")
    void getWorkDurationPerDate_returns200() {
        // given
        ZoneOffset kst = ZoneOffset.ofHours(9);
        given(commuteHistoryService.getWorkDurationPerDate(2L, YearMonth.of(2026, 7)))
                .willReturn(new WorkDurationPerDateResponse(
                        List.of(
                                new CommuteDetailResponse(
                                        11L,
                                        1L,
                                        LocalDate.of(2026, 7, 1),
                                        "Asia/Seoul",
                                        OffsetDateTime.of(2026, 7, 1, 9, 3, 0, 0, kst),
                                        OffsetDateTime.of(2026, 7, 1, 18, 58, 0, 0, kst),
                                        595L,
                                        false,
                                        CommuteStatus.COMPLETED,
                                        null,
                                        CommuteLockReason.MONTH_CLOSED),
                                new CommuteDetailResponse(
                                        12L, 0L, LocalDate.of(2026, 7, 2), "Asia/Seoul", null, null, 0L, true,
                                        CommuteStatus.DAY_OFF, null, null)),
                        595L));

        // when / then — 미퇴근·연차의 null 시각은 non_null 직렬화 정책상 필드 자체가 빠진다
        assertThat(mockMvcTester
                .get()
                .uri("/api/commute?yearMonth=2026-07")
                .session(memberSession()))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "details": [
                                {
                                    "commuteHistoryId": 11,
                                    "version": 1,
                                    "date": "2026-07-01",
                                    "workZone": "Asia/Seoul",
                                    "workStartTime": "2026-07-01T09:03:00+09:00",
                                    "workEndTime": "2026-07-01T18:58:00+09:00",
                                    "workingMinutes": 595,
                                    "usingDayOff": false,
                                    "status": "COMPLETED",
                                    "lockReason": "MONTH_CLOSED"
                                },
                                {
                                    "commuteHistoryId": 12,
                                    "date": "2026-07-02",
                                    "workingMinutes": 0,
                                    "usingDayOff": true,
                                    "status": "DAY_OFF"
                                }
                            ],
                            "sumWorkingMinutes": 595
                        }
                        """);
    }

    @Test
    @DisplayName("GET /commute — yearMonth가 없으면 400 MISSING_PARAMETER")
    void getWorkDurationPerDate_missingYearMonthReturns400() {
        // when / then
        assertThat(mockMvcTester
                .get()
                .uri("/api/commute")
                .session(memberSession()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "code": "MISSING_PARAMETER",
                            "message": "필수 파라미터가 누락되었습니다: yearMonth"
                        }
                        """);

        then(commuteHistoryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET /commute — 일반 퇴근 대상은 조회 월의 details 밖이어도 regularEndTarget 으로 내려간다")
    void getWorkDurationPerDate_exposesRegularEndTarget() {
        ZoneOffset kst = ZoneOffset.ofHours(9);
        given(commuteHistoryService.getWorkDurationPerDate(2L, YearMonth.of(2026, 10)))
                .willReturn(new WorkDurationPerDateResponse(List.of(), 0L, new RegularEndTargetResponse(
                        41L, 0L, LocalDate.of(2026, 9, 30), "Asia/Seoul",
                        OffsetDateTime.of(2026, 9, 30, 22, 0, 0, 0, kst),
                        OffsetDateTime.of(2026, 10, 1, 22, 0, 0, 0, kst),
                        null)));

        assertThat(mockMvcTester
                .get()
                .uri("/api/commute?yearMonth=2026-10")
                .session(memberSession()))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "details": [],
                            "sumWorkingMinutes": 0,
                            "regularEndTarget": {
                                "commuteHistoryId": 41,
                                "version": 0,
                                "workDate": "2026-09-30",
                                "workZone": "Asia/Seoul",
                                "workStartTime": "2026-09-30T22:00:00+09:00",
                                "endableUntil": "2026-10-01T22:00:00+09:00"
                            }
                        }
                        """);
    }

    @Test
    @DisplayName("GET /commute — yearMonth 형식이 잘못되면 400 INVALID_PARAMETER")
    void getWorkDurationPerDate_invalidYearMonthReturns400() {
        // when / then
        assertThat(mockMvcTester
                .get()
                .uri("/api/commute?yearMonth=2026-13")
                .session(memberSession()))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                            "code": "INVALID_PARAMETER",
                            "message": "파라미터 형식이 올바르지 않습니다: yearMonth"
                        }
                        """);

        then(commuteHistoryService).shouldHaveNoInteractions();
    }

    private MockHttpSession memberSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentEmployeeId", 2L);
        session.setAttribute("currentRole", Role.MEMBER);
        return session;
    }
}
