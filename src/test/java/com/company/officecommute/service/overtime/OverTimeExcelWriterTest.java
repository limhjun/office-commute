package com.company.officecommute.service.overtime;

import com.company.officecommute.domain.report.ReportFinality;
import com.company.officecommute.dto.overtime.response.OverTimeReport;
import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OverTimeExcelWriterTest {

    private final OverTimeExcelWriter overTimeExcelWriter = new OverTimeExcelWriter();

    @Test
    @DisplayName("시트 이름에 연도가 포함되어 다른 해의 같은 달과 구분된다")
    void sheetName() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(report(YearMonth.of(2024, 8), List.of()), out);

        ByteArrayOutputStream nextYearOut = new ByteArrayOutputStream();
        overTimeExcelWriter.write(report(YearMonth.of(2025, 8), List.of()), nextYearOut);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()));
             XSSFWorkbook nextYearWorkbook = new XSSFWorkbook(new ByteArrayInputStream(nextYearOut.toByteArray()))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("2024년 8월 초과근무 보고서");
            assertThat(nextYearWorkbook.getSheetName(0)).isEqualTo("2025년 8월 초과근무 보고서");
        }
    }

    @Test
    @DisplayName("헤더 행에 올바른 컬럼명이 존재한다")
    void headerRow() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(report(YearMonth.of(2024, 8), List.of()), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Row header = workbook.getSheetAt(0).getRow(1);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("사번");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("직원명");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("부서명");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("연장근무시간");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("휴일근무(8시간 이내)");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("휴일근무(8시간 초과)");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("초과근무수당");
        }
    }

    @Test
    @DisplayName("데이터 행이 올바르게 생성된다 — 연장·휴일(8h 이내/초과) 트랙이 각자 컬럼에 실린다")
    void dataRows() throws IOException {
        List<OverTimeReportData> data = List.of(
                new OverTimeReportData("EMP001", "임형준", "백엔드팀", 300L, 480L, 120L, 315000L),
                new OverTimeReportData("EMP002", "김개발", "프론트엔드팀", 120L, 0L, 0L, 30000L)
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(report(YearMonth.of(2024, 8), data), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);

            Row row1 = sheet.getRow(2);
            assertThat(row1.getCell(0).getStringCellValue()).isEqualTo("EMP001");
            assertThat(row1.getCell(1).getStringCellValue()).isEqualTo("임형준");
            assertThat(row1.getCell(2).getStringCellValue()).isEqualTo("백엔드팀");
            assertThat(row1.getCell(3).getNumericCellValue()).isCloseTo(300d / (24 * 60), withinPercentage(0.01));
            assertThat(row1.getCell(4).getNumericCellValue()).isCloseTo(480d / (24 * 60), withinPercentage(0.01));
            assertThat(row1.getCell(5).getNumericCellValue()).isCloseTo(120d / (24 * 60), withinPercentage(0.01));
            assertThat(row1.getCell(6).getNumericCellValue()).isEqualTo(315000d);

            Row row2 = sheet.getRow(3);
            assertThat(row2.getCell(0).getStringCellValue()).isEqualTo("EMP002");
            assertThat(row2.getCell(1).getStringCellValue()).isEqualTo("김개발");
            assertThat(row2.getCell(2).getStringCellValue()).isEqualTo("프론트엔드팀");
            assertThat(row2.getCell(6).getNumericCellValue()).isEqualTo(30000d);
        }
    }

    @Test
    @DisplayName("합계 행에 SUM 수식이 존재한다")
    void totalRowFormulas() throws IOException {
        List<OverTimeReportData> data = List.of(
                new OverTimeReportData("EMP001", "임형준", "백엔드팀", 300L, 0L, 0L, 75000L)
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(report(YearMonth.of(2024, 8), data), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row totalRow = sheet.getRow(3); // 알림(0) + 헤더(1) + 데이터 1행(2) + 합계(3)

            assertThat(totalRow.getCell(0).getStringCellValue()).isEqualTo("합계");
            assertThat(totalRow.getCell(3).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(totalRow.getCell(3).getCellFormula()).isEqualTo("SUM(D3:D3)");
            assertThat(totalRow.getCell(4).getCellFormula()).isEqualTo("SUM(E3:E3)");
            assertThat(totalRow.getCell(5).getCellFormula()).isEqualTo("SUM(F3:F3)");
            assertThat(totalRow.getCell(6).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(totalRow.getCell(6).getCellFormula()).isEqualTo("SUM(G3:G3)");
        }
    }

    @Test
    @DisplayName("합계 수식에 계산된 값이 함께 저장된다")
    void totalRowCachedValues() throws IOException {
        List<OverTimeReportData> data = List.of(
                new OverTimeReportData("EMP001", "임형준", "백엔드팀", 300L, 0L, 0L, 75000L),
                new OverTimeReportData("EMP002", "김개발", "프론트엔드팀", 120L, 0L, 0L, 30000L)
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(report(YearMonth.of(2024, 8), data), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Row totalRow = workbook.getSheetAt(0).getRow(4); // 알림(0) + 헤더(1) + 데이터 2행(2,3) + 합계(4)

            // 재계산하지 않는 뷰어도 값을 볼 수 있어야 한다 (getNumericCellValue 는 캐시된 계산값을 읽는다)
            assertThat(totalRow.getCell(3).getNumericCellValue()).isCloseTo(420d / (24 * 60), withinPercentage(0.01));
            assertThat(totalRow.getCell(6).getNumericCellValue()).isEqualTo(105000d);
        }
    }

    @Test
    @DisplayName("데이터가 없는 경우 헤더와 합계 행만 존재하고, 합계는 수식 대신 0이 된다")
    void emptyData() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(report(YearMonth.of(2024, 8), List.of()), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("사번");
            Row totalRow = sheet.getRow(2); // 알림(0) + 헤더(1) + 합계(2)
            assertThat(totalRow.getCell(0).getStringCellValue()).isEqualTo("합계");

            // SUM(D3:D2) 같은 역전 범위를 만들지 않는다
            for (int column = 3; column <= 6; column++) {
                assertThat(totalRow.getCell(column).getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(totalRow.getCell(column).getNumericCellValue()).isZero();
            }
        }
    }

    @Test
    @DisplayName("퇴근 미마감 기록이 있으면 건수와 과소 집계 경고를 첫 행에 남긴다")
    void noticeRowWarnsAboutUnclosedCommutes() throws IOException {
        List<OverTimeReportData> data = List.of(
                new OverTimeReportData("EMP001", "임형준", "백엔드팀", 300L, 0L, 0L, 75000L)
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(new OverTimeReport(YearMonth.of(2024, 8), data, 3), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            String notice = workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue();

            assertThat(notice).contains("3건");
            // 수치를 그대로 믿으면 안 된다는 신호가 파일 안에 있어야 한다
            assertThat(notice).contains("실제보다 적을 수 있습니다");
        }
    }

    @Test
    @DisplayName("퇴근 미마감이 없어도 알림 행은 유지되어 확인 사실이 드러난다")
    void noticeRowKeptWhenNothingUnclosed() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(report(YearMonth.of(2024, 8), List.of()), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            String notice = workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue();

            assertThat(notice).contains("0건");
            assertThat(notice).doesNotContain("[주의]");
        }
    }

    @Test
    @DisplayName("미퇴근 0건이어도 참고용은 확정본이 아니라고 표시하고 승인 대기 정정 건수를 드러낸다")
    void referenceNoticeIsNotFinal() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(new OverTimeReport(YearMonth.of(2024, 8), List.of(), 0, 2, ReportFinality.REFERENCE), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            String notice = workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue();

            assertThat(notice).contains("참고용").contains("확정본 아님").contains("승인 대기 정정 2건");
            assertThat(notice).doesNotContain("마감되었습니다");
        }
    }

    @Test
    @DisplayName("확정본과 정정본은 파일 안에서 서로 구분된다")
    void finalAndCorrectionNotices() throws IOException {
        ByteArrayOutputStream finalOut = new ByteArrayOutputStream();
        overTimeExcelWriter.write(new OverTimeReport(YearMonth.of(2024, 8), List.of(), 0, 0, ReportFinality.FINAL), finalOut);
        ByteArrayOutputStream correctionOut = new ByteArrayOutputStream();
        overTimeExcelWriter.write(new OverTimeReport(YearMonth.of(2024, 8), List.of(), 0, 0, ReportFinality.CORRECTION), correctionOut);

        try (XSSFWorkbook finalBook = new XSSFWorkbook(new ByteArrayInputStream(finalOut.toByteArray()));
             XSSFWorkbook correctionBook = new XSSFWorkbook(new ByteArrayInputStream(correctionOut.toByteArray()))) {
            assertThat(finalBook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .startsWith("[확정본]").contains("월 마감 완료");
            assertThat(correctionBook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .startsWith("[정정본]").contains("대체");
        }
    }

    private static OverTimeReport report(YearMonth yearMonth, List<OverTimeReportData> rows) {
        return new OverTimeReport(yearMonth, rows, 0);
    }

    private static org.assertj.core.data.Percentage withinPercentage(double percentage) {
        return org.assertj.core.data.Percentage.withPercentage(percentage);
    }
}
