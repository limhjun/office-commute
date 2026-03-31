package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeCalculateResponse;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import com.company.officecommute.web.ApiConvertor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OverTimeReportServiceTest {

    @InjectMocks private OverTimeReportService overTimeReportService;

    @Mock private OverTimeService overTimeService;
    @Mock private OverTimeExcelWriter overTimeExcelWriter;
    @Mock private ApiConvertor apiConvertor;

    @Captor private ArgumentCaptor<List<OverTimeReportData>> reportDataCaptor;

    @Test
    @DisplayName("초과근무 보고서 데이터를 정상적으로 생성한다")
    void generateExcelReport_correctReportData() throws IOException {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = Arrays.asList(
                new OverTimeCalculateResponse(1L, "임형준", "백엔드팀", 300L), // 5시간 초과근무
                new OverTimeCalculateResponse(2L, "김개발", "프론트엔드팀", 120L)  // 2시간 초과근무
        );

        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        overTimeReportService.generateExcelReport(yearMonth, OutputStream.nullOutputStream());

        then(overTimeExcelWriter).should().write(eq(yearMonth), reportDataCaptor.capture(), any(OutputStream.class));
        List<OverTimeReportData> result = reportDataCaptor.getValue();

        assertThat(result).hasSize(2);

        OverTimeReportData reportData1 = result.get(0);
        assertThat(reportData1.employeeName()).isEqualTo("임형준");
        assertThat(reportData1.teamName()).isEqualTo("백엔드팀");
        assertThat(reportData1.overTimeMinutes()).isEqualTo(300L);
        assertThat(reportData1.overTimePay()).isEqualTo(75000L); // 5시간(300L분) * 15000원

        OverTimeReportData reportData2 = result.get(1);
        assertThat(reportData2.employeeName()).isEqualTo("김개발");
        assertThat(reportData2.teamName()).isEqualTo("프론트엔드팀");
        assertThat(reportData2.overTimeMinutes()).isEqualTo(120L);
        assertThat(reportData2.overTimePay()).isEqualTo(30000L); // 2시간(120L분) * 15000원
    }

    @Test
    @DisplayName("엑셀 리포트 생성 시 ExcelWriter에 위임하고 다음 달 공휴일을 선제 저장한다")
    void generateExcelReport_delegatesAndPrefetches() throws IOException {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = Arrays.asList(
                new OverTimeCalculateResponse(1L, "임형준", "백엔드팀", 300L),
                new OverTimeCalculateResponse(2L, "김개발", "프론트엔드팀", 120L)
        );

        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        overTimeReportService.generateExcelReport(yearMonth, OutputStream.nullOutputStream());

        then(overTimeExcelWriter).should().write(eq(yearMonth), any(), any(OutputStream.class));
        then(apiConvertor).should().prefetchNextMonthHolidays(yearMonth);
    }

    @Test
    @DisplayName("초과근무 시간이 0분인 경우 수당도 0원이다")
    void generateExcelReport_zeroOvertime() throws IOException {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = List.of(
                new OverTimeCalculateResponse(1L, "임형준", "백엔드팀", 0L)
        );

        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        overTimeReportService.generateExcelReport(yearMonth, OutputStream.nullOutputStream());

        then(overTimeExcelWriter).should().write(eq(yearMonth), reportDataCaptor.capture(), any(OutputStream.class));
        OverTimeReportData reportData = reportDataCaptor.getValue().getFirst();
        assertThat(reportData.overTimeMinutes()).isEqualTo(0L);
        assertThat(reportData.overTimePay()).isEqualTo(0L);
    }

    @Test
    @DisplayName("59분 초과근무는 0시간으로 계산되어 수당이 0원이다")
    void generateExcelReport_lessThanOneHour() throws IOException {
        YearMonth yearMonth = YearMonth.of(2024, 8);
        List<OverTimeCalculateResponse> mockOverTimeData = List.of(
                new OverTimeCalculateResponse(1L, "임형준", "백엔드팀", 59L) // 59분
        );

        BDDMockito.given(overTimeService.calculateOverTime(yearMonth))
                .willReturn(mockOverTimeData);

        overTimeReportService.generateExcelReport(yearMonth, OutputStream.nullOutputStream());

        then(overTimeExcelWriter).should().write(eq(yearMonth), reportDataCaptor.capture(), any(OutputStream.class));
        OverTimeReportData reportData = reportDataCaptor.getValue().getFirst();
        assertThat(reportData.overTimeMinutes()).isEqualTo(59L);
        assertThat(reportData.overTimePay()).isEqualTo(0L);
    }
}
