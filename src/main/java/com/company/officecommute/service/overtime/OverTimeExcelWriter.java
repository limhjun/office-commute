package com.company.officecommute.service.overtime;

import com.company.officecommute.dto.overtime.response.OverTimeReportData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.time.YearMonth;
import java.util.List;

@Component
public class OverTimeExcelWriter {

    public void write(YearMonth yearMonth, List<OverTimeReportData> reportData, OutputStream outputStream) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try {
            Sheet sheet = workbook.createSheet(yearMonth.getMonthValue() + "월 초과근무 보고서");
            setColumnWidths(sheet);
            createHeader(sheet);

            CellStyle timeCellStyle = createTimeCellStyle(workbook);
            CellStyle currencyCellStyle = createCurrencyCellStyle(workbook);
            createDataRows(sheet, reportData, timeCellStyle, currencyCellStyle);
            createTotalRow(sheet, reportData.size(), timeCellStyle, currencyCellStyle);

            workbook.write(outputStream);
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private void setColumnWidths(Sheet sheet) {
        sheet.setColumnWidth(0, 4000);  // 직원명
        sheet.setColumnWidth(1, 4000);  // 부서명
        sheet.setColumnWidth(2, 5000);  // 초과근무시간
        sheet.setColumnWidth(3, 6000);  // 초과근무수당
    }

    private void createHeader(Sheet sheet) {
        Row headerRow = sheet.createRow(0);

        CellStyle headerStyle = sheet.getWorkbook().createCellStyle();
        Font headerFont = sheet.getWorkbook().createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        String[] headers = {"직원명", "부서명", "초과근무시간", "초과근무수당"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createDataRows(Sheet sheet, List<OverTimeReportData> reportData, CellStyle timeCellStyle, CellStyle currencyCellStyle) {

        int rowNum = 1;
        for (OverTimeReportData data : reportData) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(data.employeeName());
            row.createCell(1).setCellValue(data.teamName());

            // 분 단위를 엑셀 시간으로 변환
            // excel에서는 1이 하루(24시간)이다.
            // excelTime을 x라고 할 때,
            // 24 * 60 : date.overTimeMinutes() = 1 : x
            double excelTime = data.overTimeMinutes() / (24d * 60d);
            Cell timeCell = row.createCell(2);
            timeCell.setCellValue(excelTime);
            timeCell.setCellStyle(timeCellStyle);

            Cell payCell = row.createCell(3);
            payCell.setCellValue(data.overTimePay());
            payCell.setCellStyle(currencyCellStyle);
        }
    }

    private void createTotalRow(Sheet sheet, int dataRowCount, CellStyle timeCellStyle, CellStyle currencyCellStyle) {
        int totalRowIdx = dataRowCount + 1;
        Row totalRow = sheet.createRow(totalRowIdx);

        Cell totalLabel = totalRow.createCell(0);
        totalLabel.setCellValue("합계");

        // 총 시간: C2 ~ C{dataRowCount+1} (열 인덱스 2는 엑셀의 C열)
        Cell totalTime = totalRow.createCell(2);
        String timeRange = String.format("C2:C%d", dataRowCount + 1);
        totalTime.setCellFormula("SUM(" + timeRange + ")");
        totalTime.setCellStyle(timeCellStyle);

        // 총 수당: D2 ~ D{dataRowCount+1} (열 인덱스 3은 엑셀의 D열)
        Cell totalPay = totalRow.createCell(3);
        String payRange = String.format("D2:D%d", dataRowCount + 1);
        totalPay.setCellFormula("SUM(" + payRange + ")");
        totalPay.setCellStyle(currencyCellStyle);
    }

    private CellStyle createTimeCellStyle(SXSSFWorkbook workbook) {
        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(dataFormat.getFormat("[h]:mm"));
        return style;
    }

    private CellStyle createCurrencyCellStyle(SXSSFWorkbook workbook) {
        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(dataFormat.getFormat("₩#,##0"));
        return style;
    }
}
