package com.company.officecommute.controller.overtime;

import com.company.officecommute.auth.SessionRoleFixture;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import com.company.officecommute.dto.report.response.OverTimeReportDispatchResponse;
import com.company.officecommute.domain.report.DispatchStatus;
import com.company.officecommute.domain.report.ReportKind;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
import com.company.officecommute.service.report.OverTimeReportDispatchService;
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

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@SpringBootTest
@AutoConfigureMockMvc
class OverTimeControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void stubSessionRoles() {
        SessionRoleFixture.stubSessionRoles(employeeRepository);
    }

    @MockitoBean
    private OverTimeService overTimeService;

    @MockitoBean
    private OverTimeReportService overTimeReportService;

    @MockitoBean
    private OverTimeReportDispatchService overTimeReportDispatchService;

    @Nested
    @DisplayName("초과근무 조회 API 테스트")
    class CalculateOverTimeTests {

        @Test
        @DisplayName("MEMBER가 초과근무를 조회하면 403 에러 봉투 반환")
        void calculateOverTime_unauthorized() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime?yearMonth=2024-08")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "FORBIDDEN",
                                "message": "접근 권한이 없습니다."
                            }
                            """);
        }

        @Test
        @DisplayName("MANAGER 권한이 있는 경우 초과근무 조회 성공")
        void calculateOverTime_authorized() {
            YearMonth yearMonth = YearMonth.of(2024, 8);
            List<OverTimeCalculateResponse> mockData = Arrays.asList(
                    new OverTimeCalculateResponse(1L, "EMP001", "임형준", "팀A", 300L, 480L, 120L),
                    new OverTimeCalculateResponse(2L, "EMP002", "김개발", "팀B", 120L, 0L, 0L)
            );

            given(overTimeService.calculateOverTime(yearMonth))
                    .willReturn(mockData);

            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime?yearMonth=2024-08")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            [
                                {
                                    "id": 1,
                                    "employeeCode": "EMP001",
                                    "name": "임형준",
                                    "overTimeMinutes": 300,
                                    "holidayWithin8HoursMinutes": 480,
                                    "holidayExceeding8HoursMinutes": 120
                                },
                                {
                                    "id": 2,
                                    "employeeCode": "EMP002",
                                    "name": "김개발",
                                    "overTimeMinutes": 120,
                                    "holidayWithin8HoursMinutes": 0,
                                    "holidayExceeding8HoursMinutes": 0
                                }
                            ]
                        """);
        }

        @Test
        @DisplayName("잘못된 yearMonth 형식으로 요청 시 예외 발생")
        void calculateOverTime_invalidYearMonth() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime?yearMonth=invalid-date")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("INVALID_PARAMETER");
        }

        @Test
        @DisplayName("yearMonth 파라미터가 누락된 경우 예외 발생")
        void calculateOverTime_missingYearMonth() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("MISSING_PARAMETER");
        }

        @Test
        @DisplayName("신뢰할 수 있는 공휴일 데이터가 없으면 초과근무 계산을 중단한다")
        void calculateOverTime_holidayDataUnavailable() {
            given(overTimeService.calculateOverTime(YearMonth.of(2024, 8)))
                    .willThrow(new HolidayDataUnavailableException("공휴일 정보를 확인할 수 없어 초과근무 리포트를 생성할 수 없습니다. 잠시 후 다시 시도해 주세요."));

            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime?yearMonth=2024-08")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("HOLIDAY_DATA_UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("초과근무 엑셀 다운로드 API 테스트")
    class DownloadOverTimeReportTests {

        @Test
        @DisplayName("MANAGER 권한이 없는 경우 엑셀 다운로드 요청 시 예외 발생")
        void downloadOverTimeReport_unauthorized() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime/report/excel?yearMonth=2024-08")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("MANAGER 권한이 있는 경우 엑셀 다운로드 성공")
        void downloadOverTimeReport_authorized() throws Exception {
            YearMonth yearMonth = YearMonth.of(2024, 8);

            given(overTimeReportService.generateReport(yearMonth))
                    .willReturn(new OverTimeReport(yearMonth, List.of(new OverTimeReportData("EMP001", "임형준", "팀A", 300L, 0L, 0L, 75000L)), 0));

            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime/report/excel?yearMonth=2024-08")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.OK)
                    .headers()
                    .hasValue("Content-Type", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").toString())
                    .satisfies(headers -> {
                        String cd = headers.getFirst("Content-Disposition");
                        assertThat(cd).startsWith("attachment");
                        assertThat(cd).contains("filename*=");
                        String fileName = "2024년8월_초과근무보고서_참고용.xlsx";
                        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
                        assertThat(cd).contains(encoded);
                    });
        }

        @Test
        @DisplayName("잘못된 yearMonth 형식으로 엑셀 다운로드 요청 시 예외 발생")
        void downloadOverTimeReport_invalidYearMonth() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime/report/excel?yearMonth=invalid-date")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("INVALID_PARAMETER");
        }

        @Test
        @DisplayName("yearMonth 파라미터가 누락된 경우 엑셀 다운로드 예외 발생")
        void downloadOverTimeReport_missingYearMonth() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime/report/excel")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("MISSING_PARAMETER");
        }

        @Test
        @DisplayName("공휴일 정보를 확인할 수 없으면 엑셀 다운로드를 중단한다")
        void downloadOverTimeReport_holidayDataUnavailable() {
            YearMonth yearMonth = YearMonth.of(2024, 8);
            given(overTimeReportService.generateReport(yearMonth))
                    .willThrow(new HolidayDataUnavailableException("공휴일 정보를 확인할 수 없어 초과근무 리포트를 생성할 수 없습니다. 잠시 후 다시 시도해 주세요."));

            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime/report/excel?yearMonth=2024-08")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("HOLIDAY_DATA_UNAVAILABLE");
        }

        @Test
        @DisplayName("엑셀 생성 중 IOException 발생 시 예외가 전파된다")
        void downloadOverTimeReport_ioException() throws Exception {
            YearMonth yearMonth = YearMonth.of(2024, 8);

            given(overTimeReportService.generateReport(yearMonth))
                    .willReturn(new OverTimeReport(yearMonth, List.of(new OverTimeReportData("EMP001", "임형준", "팀A", 300L, 0L, 0L, 75000L)), 0));
            willThrow(new RuntimeException("엑셀 생성 실패")).given(overTimeReportService)
                    .writeExcelReport(any(OverTimeReport.class), any(OutputStream.class));

            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime/report/excel?yearMonth=2024-08")
                    .session(managerSession()))
                    .failure()
                    .hasRootCauseMessage("엑셀 생성 실패");
        }
    }

    @Nested
    @DisplayName("리포트 수동 발송 API 테스트")
    class DispatchReportTests {

        @Test
        @DisplayName("MANAGER가 수동 발송하면 실행 후 현재 발송 상태를 돌려준다")
        void dispatch_returnsCurrentStatus() {
            given(overTimeReportDispatchService.dispatchAndDescribe(YearMonth.of(2026, 7)))
                    .willReturn(new OverTimeReportDispatchResponse(
                            YearMonth.of(2026, 7), ReportKind.ORIGINAL, DispatchStatus.SENT, 1,
                            Instant.parse("2026-08-01T06:00:00Z"), Instant.parse("2026-08-01T06:00:00Z"), null,
                            true, null, null, null));

            assertThat(mockMvcTester
                    .post()
                    .uri("/api/overtime/report/dispatch?yearMonth=2026-07")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "yearMonth": "2026-07",
                                "kind": "ORIGINAL",
                                "status": "SENT",
                                "attemptCount": 1,
                                "finalFileAvailable": true
                            }
                            """);
        }

        @Test
        @DisplayName("미마감으로 보류되면 FAILED와 사유가 그대로 노출된다 — 관리자가 즉시 확인할 수 있어야 한다")
        void dispatch_exposesFailureReason() {
            given(overTimeReportDispatchService.dispatchAndDescribe(YearMonth.of(2026, 7)))
                    .willReturn(new OverTimeReportDispatchResponse(
                            YearMonth.of(2026, 7), ReportKind.ORIGINAL, DispatchStatus.FAILED, 2,
                            Instant.parse("2026-08-01T06:00:00Z"), null, "UNCLOSED_COMMUTES: 미마감 3건",
                            false, null, null, null));

            assertThat(mockMvcTester
                    .post()
                    .uri("/api/overtime/report/dispatch?yearMonth=2026-07")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "status": "FAILED",
                                "attemptCount": 2,
                                "lastFailureReason": "UNCLOSED_COMMUTES: 미마감 3건"
                            }
                            """);
        }

        @Test
        @DisplayName("MEMBER는 수동 발송할 수 없다 — 대표에게 메일을 보내는 경로다")
        void dispatch_forbiddenForMember() {
            assertThat(mockMvcTester
                    .post()
                    .uri("/api/overtime/report/dispatch?yearMonth=2026-07")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.FORBIDDEN);

            then(overTimeReportDispatchService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("세션 인증 테스트")
    class SessionAuthTests {

        @Test
        @DisplayName("세션이 없는 경우 401 에러 봉투 반환")
        void noSession() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/api/overtime?yearMonth=2024-08"))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                                "code": "UNAUTHORIZED",
                                "message": "로그인이 필요합니다."
                            }
                            """);
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
