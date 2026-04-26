package com.tpt.validator.spec;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Loads the bundled TPT V7 spec xlsx and produces a {@link SpecCatalog}.
 * Column layout (1-indexed):
 *   A=NUM_DATA, B=path, C=definition, D=codification, E=comment,
 *   K=Solvency II flag (M/C/O/I/N/A),
 *   L..AA=CIC applicability columns (CIC0..CICF),
 *   AC=NW675, AD=SST (excluded), AE=IORP, AF/AG/AH=EIOPA PF, AI=ECB Addon.
 */
public final class SpecLoader {

    private static final Logger log = LoggerFactory.getLogger(SpecLoader.class);

    private static final String RESOURCE = "/spec/TPT_V7  20241125_updated.xlsx";

    private static final int COL_NUM_DATA   = 1;
    private static final int COL_PATH       = 2;
    private static final int COL_DEFINITION = 3;
    private static final int COL_CODIF      = 4;
    private static final int COL_COMMENT    = 5;
    private static final int COL_FLAG       = 11;
    private static final int FIRST_DATA_ROW = 8; // 1-indexed; rows 1..7 are headers/disclaimer.

    private static final int[] CIC_COLUMNS = {12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27};
    private static final String[] CIC_NAMES = {"CIC0","CIC1","CIC2","CIC3","CIC4","CIC5","CIC6","CIC7","CIC8","CIC9","CICA","CICB","CICC","CICD","CICE","CICF"};

    private static final int COL_NW675 = 29;
    private static final int COL_IORP  = 31;
    private static final int COL_EIOPA_POS = 32;
    private static final int COL_EIOPA_ASS = 33;
    private static final int COL_EIOPA_LT  = 34;
    private static final int COL_ECB       = 35;

    private SpecLoader() {}

    public static SpecCatalog loadBundled() {
        try (InputStream in = Objects.requireNonNull(
                SpecLoader.class.getResourceAsStream(RESOURCE),
                "Missing bundled spec resource " + RESOURCE);
             Workbook wb = new XSSFWorkbook(in)) {
            return load(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load bundled TPT V7 spec", e);
        }
    }

    public static SpecCatalog load(Workbook wb) {
        Sheet sheet = wb.getSheet("TPT V7.0");
        if (sheet == null) sheet = wb.getSheetAt(0);

        List<FieldSpec> fields = new ArrayList<>();
        for (int rIdx = FIRST_DATA_ROW - 1; rIdx <= sheet.getLastRowNum(); rIdx++) {
            Row row = sheet.getRow(rIdx);
            if (row == null) continue;
            String num  = stringValue(row, COL_NUM_DATA);
            String path = stringValue(row, COL_PATH);
            if (isBlank(num) && isBlank(path)) continue;
            // Section headers: NUM_DATA without an underscore-prefixed number AND no path.
            // Genuine fields may have only a NUM_DATA (e.g. "1000_TPT_Version"); keep those.
            if (isBlank(path) && !looksLikeFieldLabel(num)) continue;

            String definition = stringValue(row, COL_DEFINITION);
            String comment    = stringValue(row, COL_COMMENT);
            String codifRaw   = stringValue(row, COL_CODIF);
            CodificationDescriptor codif = CodificationParser.parse(codifRaw);

            Set<String> applicableCic = new LinkedHashSet<>();
            for (int i = 0; i < CIC_COLUMNS.length; i++) {
                if (!isBlank(stringValue(row, CIC_COLUMNS[i]))) {
                    applicableCic.add(CIC_NAMES[i]);
                }
            }

            Map<Profile, Flag> flags = new EnumMap<>(Profile.class);
            flags.put(Profile.SOLVENCY_II, Flag.parse(stringValue(row, COL_FLAG)));
            flags.put(Profile.NW_675, Flag.parse(stringValue(row, COL_NW675)));
            flags.put(Profile.IORP_EIOPA_ECB, mergeFlags(
                    Flag.parse(stringValue(row, COL_IORP)),
                    presenceFlag(row, COL_EIOPA_POS),
                    presenceFlag(row, COL_EIOPA_ASS),
                    presenceFlag(row, COL_EIOPA_LT),
                    presenceFlag(row, COL_ECB)));

            FieldSpec spec = new FieldSpec(num.trim(), path.trim(), definition, comment,
                    codifRaw, codif, flags, applicableCic, rIdx + 1);
            fields.add(spec);
        }
        log.info("Loaded {} TPT V7 spec fields", fields.size());
        return new SpecCatalog(fields);
    }

    /** EIOPA columns contain reference codes, not M/C/O. Treat any non-blank value as Mandatory presence. */
    private static Flag presenceFlag(Row row, int col) {
        return isBlank(stringValue(row, col)) ? Flag.UNKNOWN : Flag.M;
    }

    /** Merge several flags choosing the strictest (M > C > O > I > NA > UNKNOWN). */
    private static Flag mergeFlags(Flag... flags) {
        Flag best = Flag.UNKNOWN;
        for (Flag f : flags) {
            if (rank(f) > rank(best)) best = f;
        }
        return best;
    }

    private static int rank(Flag f) {
        return switch (f) {
            case M -> 5;
            case C -> 4;
            case O -> 3;
            case I -> 2;
            case NA -> 1;
            case UNKNOWN -> 0;
        };
    }

    private static String stringValue(Row row, int col1Indexed) {
        if (row == null) return "";
        Cell cell = row.getCell(col1Indexed - 1);
        if (cell == null) return "";
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> stripTrailingZeros(cell.getNumericCellValue());
                case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                case FORMULA -> formulaValue(cell);
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private static String formulaValue(Cell cell) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> stripTrailingZeros(cell.getNumericCellValue());
                case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private static String stripTrailingZeros(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** True if the NUM_DATA cell looks like a field label ("1000_..." or "12_..."), not a section header. */
    private static boolean looksLikeFieldLabel(String num) {
        if (isBlank(num)) return false;
        String t = num.trim();
        int us = t.indexOf('_');
        if (us <= 0) return false;
        String prefix = t.substring(0, us);
        // Numeric prefix (with optional trailing letter like "8b", "105a") indicates a field row.
        return prefix.chars().anyMatch(Character::isDigit);
    }
}
