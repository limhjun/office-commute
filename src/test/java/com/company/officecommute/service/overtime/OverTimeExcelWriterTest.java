package com.company.officecommute.service.overtime;

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
    @DisplayName("시트 이름이 '월 초과근무 보고서' 형식으로 생성된다")
    void sheetName() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(YearMonth.of(2024, 8), List.of(), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("8월 초과근무 보고서");
        }
    }

    @Test
    @DisplayName("헤더 행에 올바른 컬럼명이 존재한다")
    void headerRow() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(YearMonth.of(2024, 8), List.of(), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("직원명");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("부서명");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("초과근무시간");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("초과근무수당");
        }
    }

    @Test
    @DisplayName("데이터 행이 올바르게 생성된다")
    void dataRows() throws IOException {
        List<OverTimeReportData> data = List.of(
                new OverTimeReportData("임형준", "백엔드팀", 300L, 75000L),
                new OverTimeReportData("김개발", "프론트엔드팀", 120L, 30000L)
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(YearMonth.of(2024, 8), data, out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);

            Row row1 = sheet.getRow(1);
            assertThat(row1.getCell(0).getStringCellValue()).isEqualTo("임형준");
            assertThat(row1.getCell(1).getStringCellValue()).isEqualTo("백엔드팀");
            assertThat(row1.getCell(2).getNumericCellValue()).isCloseTo(300d / (24 * 60), withinPercentage(0.01));
            assertThat(row1.getCell(3).getNumericCellValue()).isEqualTo(75000d);

            Row row2 = sheet.getRow(2);
            assertThat(row2.getCell(0).getStringCellValue()).isEqualTo("김개발");
            assertThat(row2.getCell(1).getStringCellValue()).isEqualTo("프론트엔드팀");
            assertThat(row2.getCell(3).getNumericCellValue()).isEqualTo(30000d);
        }
    }

    @Test
    @DisplayName("합계 행에 SUM 수식이 존재한다")
    void totalRowFormulas() throws IOException {
        List<OverTimeReportData> data = List.of(
                new OverTimeReportData("임형준", "백엔드팀", 300L, 75000L)
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(YearMonth.of(2024, 8), data, out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row totalRow = sheet.getRow(2); // header(0) + 1 data row(1) + total(2)

            assertThat(totalRow.getCell(0).getStringCellValue()).isEqualTo("합계");
            assertThat(totalRow.getCell(2).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(totalRow.getCell(2).getCellFormula()).isEqualTo("SUM(C2:C2)");
            assertThat(totalRow.getCell(3).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(totalRow.getCell(3).getCellFormula()).isEqualTo("SUM(D2:D2)");
        }
    }

    @Test
    @DisplayName("데이터가 없는 경우 헤더와 합계 행만 존재한다")
    void emptyData() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        overTimeExcelWriter.write(YearMonth.of(2024, 8), List.of(), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("직원명");
            Row totalRow = sheet.getRow(1); // header(0) + total(1)
            assertThat(totalRow.getCell(0).getStringCellValue()).isEqualTo("합계");
        }
    }

    private static org.assertj.core.data.Percentage withinPercentage(double percentage) {
        return org.assertj.core.data.Percentage.withPercentage(percentage);
    }
}
