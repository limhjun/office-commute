package com.company.officecommute.controller.overtime;

import com.company.officecommute.domain.employee.Role;
import com.company.officecommute.dto.overtime.response.HolidayCacheStatusResponse;
import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.global.exception.HolidayDataUnavailableException;
import com.company.officecommute.service.overtime.HolidayCacheStatusService;
import com.company.officecommute.service.overtime.HolidaySyncService;
import com.company.officecommute.service.overtime.OverTimeReportService;
import com.company.officecommute.service.overtime.OverTimeService;
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
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;

@SpringBootTest
@AutoConfigureMockMvc
class OverTimeControllerTest {

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private OverTimeService overTimeService;

    @MockitoBean
    private OverTimeReportService overTimeReportService;

    @MockitoBean
    private HolidayCacheStatusService holidayCacheStatusService;

    @MockitoBean
    private HolidaySyncService holidaySyncService;

    @Nested
    @DisplayName("초과근무 조회 API 테스트")
    class CalculateOverTimeTests {

        @Test
        @DisplayName("MANAGER 권한이 없는 경우 초과근무 조회 요청 시 예외 발생")
        void calculateOverTime_unauthorized() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime?yearMonth=2024-08")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("MANAGER 권한이 있는 경우 초과근무 조회 성공")
        void calculateOverTime_authorized() {
            YearMonth yearMonth = YearMonth.of(2024, 8);
            List<OverTimeCalculateResponse> mockData = Arrays.asList(
                    new OverTimeCalculateResponse(1L, "임형준", "팀A", 300L),
                    new OverTimeCalculateResponse(2L, "김개발", "팀B", 120L)
            );

            given(overTimeService.calculateOverTime(yearMonth))
                    .willReturn(mockData);

            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime?yearMonth=2024-08")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            [
                                {
                                    "id": 1,
                                    "name": "임형준",
                                    "overTimeMinutes": 300
                                },
                                {
                                    "id": 2,
                                    "name": "김개발",
                                    "overTimeMinutes": 120
                                }
                            ]
                        """);
        }

        @Test
        @DisplayName("잘못된 yearMonth 형식으로 요청 시 예외 발생")
        void calculateOverTime_invalidYearMonth() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime?yearMonth=invalid-date")
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
                    .uri("/overtime")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("MISSING_PARAMETER");
        }

        @Test
        @DisplayName("신뢰할 수 있는 공휴일 데이터가 없으면 초과근무 계산을 중단한다")
        void calculateOverTime_holidayDataUnavailable() {
            given(overTimeService.calculateOverTime(YearMonth.of(2024, 8)))
                    .willThrow(new HolidayDataUnavailableException("공휴일 데이터를 확인할 수 없어 초과근무를 계산할 수 없습니다: 2024-08"));

            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime?yearMonth=2024-08")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("HOLIDAY_DATA_UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("공휴일 캐시 상태 조회 API 테스트")
    class HolidayStatusTests {

        @Test
        @DisplayName("MANAGER 권한이 있으면 공휴일 캐시 상태를 조회할 수 있다")
        void getHolidayStatus_authorized() {
            YearMonth yearMonth = YearMonth.of(2026, 3);
            given(holidayCacheStatusService.getStatus(yearMonth))
                    .willReturn(new HolidayCacheStatusResponse(
                            "2026-03",
                            2,
                            false,
                            "STALE_CACHE",
                            "공휴일 캐시가 최신 상태가 아니어서 초과근무를 계산할 수 없습니다: 2026-03 (월말 기준 최종 검증 필요)",
                            java.time.LocalDateTime.of(2026, 3, 20, 9, 0)
                    ));

            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime/holiday-status?yearMonth=2026-03")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .extractingPath("$.status").isEqualTo("STALE_CACHE");
        }

        @Test
        @DisplayName("MANAGER 권한이 없으면 공휴일 캐시 상태 조회를 거부한다")
        void getHolidayStatus_unauthorized() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime/holiday-status?yearMonth=2026-03")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("공휴일 재동기화 API 테스트")
    class HolidaySyncTests {

        @Test
        @DisplayName("MANAGER 권한이 있으면 특정 월 공휴일을 재동기화할 수 있다")
        void syncHoliday_authorized() {
            YearMonth yearMonth = YearMonth.of(2026, 3);
            given(holidaySyncService.refreshAndGetStatus(yearMonth))
                    .willReturn(new HolidayCacheStatusResponse(
                            "2026-03",
                            2,
                            true,
                            "READY",
                            "초과근무 계산에 사용할 수 있는 공휴일 캐시입니다.",
                            java.time.LocalDateTime.of(2026, 4, 4, 9, 0)
                    ));

            assertThat(mockMvcTester
                    .post()
                    .uri("/overtime/holiday-sync?yearMonth=2026-03")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson()
                    .extractingPath("$.status").isEqualTo("READY");
        }

        @Test
        @DisplayName("MANAGER 권한이 없으면 공휴일 재동기화를 거부한다")
        void syncHoliday_unauthorized() {
            assertThat(mockMvcTester
                    .post()
                    .uri("/overtime/holiday-sync?yearMonth=2026-03")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.FORBIDDEN);
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
                    .uri("/overtime/report/excel?yearMonth=2024-08")
                    .session(memberSession()))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("MANAGER 권한이 있는 경우 엑셀 다운로드 성공")
        void downloadOverTimeReport_authorized() throws Exception {
            YearMonth yearMonth = YearMonth.of(2024, 8);

            willDoNothing().given(overTimeReportService)
                    .generateExcelReport(eq(yearMonth), any(OutputStream.class));

            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime/report/excel?yearMonth=2024-08")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.OK)
                    .headers()
                    .hasValue("Content-Type", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").toString())
                    .satisfies(headers -> {
                        String cd = headers.getFirst("Content-Disposition");
                        assertThat(cd).startsWith("attachment");
                        assertThat(cd).contains("filename*=");
                        String fileName = "2024년8월_초과근무보고서.xlsx";
                        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
                        assertThat(cd).contains(encoded);
                    });
        }

        @Test
        @DisplayName("잘못된 yearMonth 형식으로 엑셀 다운로드 요청 시 예외 발생")
        void downloadOverTimeReport_invalidYearMonth() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime/report/excel?yearMonth=invalid-date")
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
                    .uri("/overtime/report/excel")
                    .session(managerSession()))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.code").isEqualTo("MISSING_PARAMETER");
        }

        @Test
        @DisplayName("엑셀 생성 중 IOException 발생 시 예외가 전파된다")
        void downloadOverTimeReport_ioException() throws Exception {
            YearMonth yearMonth = YearMonth.of(2024, 8);

            willThrow(new RuntimeException("엑셀 생성 실패")).given(overTimeReportService)
                    .generateExcelReport(eq(yearMonth), any(OutputStream.class));

            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime/report/excel?yearMonth=2024-08")
                    .session(managerSession()))
                    .failure()
                    .hasRootCauseMessage("엑셀 생성 실패");
        }
    }

    @Nested
    @DisplayName("세션 인증 테스트")
    class SessionAuthTests {

        @Test
        @DisplayName("세션이 없는 경우 401 응답")
        void noSession() {
            assertThat(mockMvcTester
                    .get()
                    .uri("/overtime?yearMonth=2024-08"))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
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
