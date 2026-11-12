package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OverTimeReportServiceTest {

    @InjectMocks private OverTimeReportService overTimeReportService;

    @Mock private OverTimeService overTimeService;
    @Mock private OverTimeExcelWriter overTimeExcelWriter;

    @Captor private ArgumentCaptor<OverTimeReport> reportCaptor;

    @Test
    @DisplayName("초과근무 보고서 데이터를 정상적으로 생성한다")
    void generateExcelReport_correctReportData() throws IOException {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = Arrays.asList(
                new OverTimeCalculateResponse(1L, "EMP001", "임형준", "백엔드팀", 300L, 0L, 0L), // 5시간 초과근무
                new OverTimeCalculateResponse(2L, "EMP002", "김개발", "프론트엔드팀", 120L, 0L, 0L)  // 2시간 초과근무
        );

        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        overTimeReportService.generateExcelReport(yearMonth, OutputStream.nullOutputStream());

        then(overTimeExcelWriter).should().write(reportCaptor.capture(), any(OutputStream.class));
        List<OverTimeReportData> result = reportCaptor.getValue().rows();

        assertThat(result).hasSize(2);

        OverTimeReportData reportData1 = result.get(0);
        assertThat(reportData1.employeeCode()).isEqualTo("EMP001");
        assertThat(reportData1.employeeName()).isEqualTo("임형준");
        assertThat(reportData1.teamName()).isEqualTo("백엔드팀");
        assertThat(reportData1.overTimeMinutes()).isEqualTo(300L);
        assertThat(reportData1.overTimePay()).isEqualTo(112500L); // 300분 × 15000원 × 1.5 / 60

        OverTimeReportData reportData2 = result.get(1);
        assertThat(reportData2.employeeName()).isEqualTo("김개발");
        assertThat(reportData2.teamName()).isEqualTo("프론트엔드팀");
        assertThat(reportData2.overTimeMinutes()).isEqualTo(120L);
        assertThat(reportData2.overTimePay()).isEqualTo(45000L); // 120분 × 15000원 × 1.5 / 60
    }

    @Test
    @DisplayName("조회 순서와 무관하게 부서명 → 직원명 → 사번 순으로 정렬한다")
    void generateOverTimeReportData_sortedDeterministically() {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        // DB 반환 순서를 흉내낸 뒤섞인 입력 (동명이인 포함)
        List<OverTimeCalculateResponse> mockOverTimeData = Arrays.asList(
                new OverTimeCalculateResponse(1L, "EMP004", "김철수", "프론트엔드팀", 10L, 0L, 0L),
                new OverTimeCalculateResponse(2L, "EMP003", "임형준", "백엔드팀", 20L, 0L, 0L),
                new OverTimeCalculateResponse(3L, "EMP002", "김철수", "백엔드팀", 30L, 0L, 0L),
                new OverTimeCalculateResponse(4L, "EMP001", "김철수", "백엔드팀", 40L, 0L, 0L)
        );

        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        List<OverTimeReportData> result = overTimeReportService.generateReport(yearMonth).rows();

        assertThat(result)
                .extracting(OverTimeReportData::teamName, OverTimeReportData::employeeName, OverTimeReportData::employeeCode)
                .containsExactly(
                        tuple("백엔드팀", "김철수", "EMP001"), // 동명이인은 사번으로 순서가 갈린다
                        tuple("백엔드팀", "김철수", "EMP002"),
                        tuple("백엔드팀", "임형준", "EMP003"),
                        tuple("프론트엔드팀", "김철수", "EMP004")
                );
    }

    @Test
    @DisplayName("퇴근 미마감 건수를 리포트에 함께 실어 과소 집계 가능성을 드러낸다")
    void generateReport_carriesUnclosedCommuteCount() {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(List.of(new OverTimeCalculateResponse(1L, "EMP001", "임형준", "백엔드팀", 300L, 0L, 0L)));
        BDDMockito.given(overTimeService.findUnclosedCommutes(yearMonth)).willReturn(List.of(
                new UnclosedCommute("EMP002", "김개발", LocalDate.of(2024, 8, 1)),
                new UnclosedCommute("EMP003", "박서버", LocalDate.of(2024, 8, 2)),
                new UnclosedCommute("EMP004", "최데브", LocalDate.of(2024, 8, 3))
        ));

        OverTimeReport report = overTimeReportService.generateReport(yearMonth);

        assertThat(report.unclosedCommuteCount()).isEqualTo(3L);
        assertThat(report.hasUnclosedCommutes()).isTrue();
    }

    @Test
    @DisplayName("발송용 리포트의 미마감 건수는 경고에 전달할 같은 목록에서 도출한다")
    void generateReportSnapshot_usesOneUnclosedCommuteRead() {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<UnclosedCommute> unclosed = List.of(
                new UnclosedCommute("EMP001", "임형준", LocalDate.of(2024, 8, 1)),
                new UnclosedCommute("EMP002", "김개발", LocalDate.of(2024, 8, 2))
        );
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth)).willReturn(List.of());
        BDDMockito.given(overTimeService.findUnclosedCommutes(yearMonth)).willReturn(unclosed);

        OverTimeReportSnapshot snapshot = overTimeReportService.generateReportSnapshot(yearMonth);

        assertThat(snapshot.report().unclosedCommuteCount()).isEqualTo(unclosed.size());
        assertThat(snapshot.unclosedCommutes()).containsExactlyElementsOf(unclosed);
    }

    @Test
    @DisplayName("미마감을 집계보다 먼저 읽는다 — 과거 기록은 마감될 수만 있어, 이 순서면 게이트 통과와 과소 집계가 공존할 수 없다")
    void generateReportSnapshot_readsUnclosedBeforeAggregation() {
        // 순서를 뒤집으면: 집계가 미마감 행을 0분으로 읽음 → 관리자가 그 기록을 마감 →
        // 미마감 조회가 빈 목록을 봄 → 게이트가 열려 과소 집계 리포트가 대표에게 간다.
        YearMonth yearMonth = YearMonth.of(2024, 8);
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth)).willReturn(List.of());
        BDDMockito.given(overTimeService.findUnclosedCommutes(yearMonth)).willReturn(List.of());

        overTimeReportService.generateReportSnapshot(yearMonth);

        InOrder inOrder = Mockito.inOrder(overTimeService);
        inOrder.verify(overTimeService).findUnclosedCommutes(yearMonth);
        inOrder.verify(overTimeService).calculateOverTime(yearMonth);
    }

    @Test
    @DisplayName("식별 정보가 비어 있으면 빈 칸을 만드는 대신 실패한다")
    void generateOverTimeReportData_rejectsMissingIdentity() {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(List.of(new OverTimeCalculateResponse(1L, null, "임형준", "백엔드팀", 300L, 0L, 0L)));

        assertThatThrownBy(() -> overTimeReportService.generateReport(yearMonth))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("employeeCode");
    }

    @Test
    @DisplayName("휴일근로는 8시간 이내 1.5배, 초과분 2.0배로 가산한다 (근로기준법 제56조②)")
    void generateExcelReport_holidayWorkPaidAtLegalMultipliers() throws IOException {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        // 연장 1h + 휴일 8h + 휴일 초과 2h
        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(List.of(new OverTimeCalculateResponse(1L, "EMP001", "임형준", "백엔드팀", 60L, 480L, 120L)));

        overTimeReportService.generateExcelReport(yearMonth, OutputStream.nullOutputStream());

        then(overTimeExcelWriter).should().write(reportCaptor.capture(), any(OutputStream.class));
        OverTimeReportData reportData = reportCaptor.getValue().rows().getFirst();
        assertThat(reportData.overTimeMinutes()).isEqualTo(60L);
        assertThat(reportData.holidayWithin8HoursMinutes()).isEqualTo(480L);
        assertThat(reportData.holidayExceeding8HoursMinutes()).isEqualTo(120L);
        // (60+480)분 × 1.5 + 120분 × 2.0 = 1050분 상당 × 15000원/60
        assertThat(reportData.overTimePay()).isEqualTo(262500L);
    }

    @Test
    @DisplayName("초과근무 시간이 0분인 경우 수당도 0원이다")
    void generateExcelReport_zeroOvertime() throws IOException {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = List.of(
                new OverTimeCalculateResponse(1L, "EMP001", "임형준", "백엔드팀", 0L, 0L, 0L)
        );

        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        overTimeReportService.generateExcelReport(yearMonth, OutputStream.nullOutputStream());

        then(overTimeExcelWriter).should().write(reportCaptor.capture(), any(OutputStream.class));
        OverTimeReportData reportData = reportCaptor.getValue().rows().getFirst();
        assertThat(reportData.overTimeMinutes()).isEqualTo(0L);
        assertThat(reportData.overTimePay()).isEqualTo(0L);
    }

    @Test
    @DisplayName("시간 단위 절삭 없이 분 단위로 비례 계산한다 (90분 = 33,750원)")
    void generateExcelReport_minutesProRatedNotTruncatedToHours() throws IOException {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = List.of(
                new OverTimeCalculateResponse(1L, "EMP001", "임형준", "백엔드팀", 90L, 0L, 0L) // 90분 (1.5시간)
        );

        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        overTimeReportService.generateExcelReport(yearMonth, OutputStream.nullOutputStream());

        then(overTimeExcelWriter).should().write(reportCaptor.capture(), any(OutputStream.class));
        OverTimeReportData reportData = reportCaptor.getValue().rows().getFirst();
        assertThat(reportData.overTimeMinutes()).isEqualTo(90L);
        assertThat(reportData.overTimePay()).isEqualTo(33750L); // 90분 × 15000원 × 1.5 / 60
    }

    @Test
    @DisplayName("1시간 미만 초과근무도 분 단위로 비례 지급된다 (59분 = 22,125원)")
    void generateExcelReport_subHourMinutesNotTruncated() throws IOException {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = List.of(
                new OverTimeCalculateResponse(1L, "EMP001", "임형준", "백엔드팀", 59L, 0L, 0L) // 59분 (1시간 미만)
        );

        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        overTimeReportService.generateExcelReport(yearMonth, OutputStream.nullOutputStream());

        then(overTimeExcelWriter).should().write(reportCaptor.capture(), any(OutputStream.class));
        OverTimeReportData reportData = reportCaptor.getValue().rows().getFirst();
        assertThat(reportData.overTimeMinutes()).isEqualTo(59L);
        assertThat(reportData.overTimePay()).isEqualTo(22125L); // 59분 × 15000원 × 1.5 / 60
    }
}
