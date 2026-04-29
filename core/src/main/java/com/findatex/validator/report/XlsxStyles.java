package com.findatex.validator.report;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/** Shared cell-style + row-write helpers used by both report writers. */
final class XlsxStyles {

    private XlsxStyles() {}

    static CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.LEFT);
        return s;
    }

    static CellStyle percentStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
        return s;
    }

    static CellStyle colourStyle(Workbook wb, short colour) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(colour);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    static void addRow(Sheet s, int rowIdx, CellStyle style, String... values) {
        Row r = s.createRow(rowIdx);
        for (int c = 0; c < values.length; c++) {
            Cell cell = r.createCell(c);
            cell.setCellValue(values[c]);
            if (style != null) cell.setCellStyle(style);
        }
    }
}
