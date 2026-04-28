package com.findatex.validator.spec;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final String RESOURCE = "/spec/tpt/TPT_V7_20241125.xlsx";

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
    private static final int COL_SST   = 30;
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
            Map<String, Set<String>> applicableSubs = new HashMap<>();
            for (int i = 0; i < CIC_COLUMNS.length; i++) {
                String cellText = stringValue(row, CIC_COLUMNS[i]);
                if (isBlank(cellText)) continue;
                String cicName = CIC_NAMES[i];
                applicableCic.add(cicName);
                Set<String> subs = parseSubcategoryQualifier(cellText, cicName);
                if (!subs.isEmpty()) applicableSubs.put(cicName, subs);
            }

            // Profile codes match com.findatex.validator.template.tpt.TptProfiles.* and the legacy
            // Profile enum's name() values. Hard-coded here to keep the spec package free of
            // template-package imports.
            Map<String, Flag> flags = new LinkedHashMap<>();
            flags.put("SOLVENCY_II",     Flag.parse(stringValue(row, COL_FLAG)));
            flags.put("NW_675",          Flag.parse(stringValue(row, COL_NW675)));
            flags.put("SST",             Flag.parse(stringValue(row, COL_SST)));
            flags.put("IORP_EIOPA_ECB",  mergeFlags(
                    Flag.parse(stringValue(row, COL_IORP)),
                    presenceFlag(row, COL_EIOPA_POS),
                    presenceFlag(row, COL_EIOPA_ASS),
                    presenceFlag(row, COL_EIOPA_LT),
                    presenceFlag(row, COL_ECB)));

            ApplicabilityScope scope = applicableCic.isEmpty() && applicableSubs.isEmpty()
                    ? EmptyApplicabilityScope.INSTANCE
                    : new CicApplicabilityScope(applicableCic, applicableSubs);

            FieldSpec spec = new FieldSpec(num.trim(), path.trim(), definition, comment,
                    codifRaw, codif, flags, scope, rIdx + 1);
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

    /** Package-private so {@link ManifestDrivenSpecLoader} can reuse the cell-reading helper. */
    static String stringValue(Row row, int col1Indexed) {
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

    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Quoted CIC sub-codes inside qualifier text, e.g. {@code "22"}, {@code "A1"}, {@code "B4"}.
     * The first char identifies the CIC class (digit or A..F), the second is the sub-category.
     */
    private static final Pattern QUOTED_CIC_SUBCODE = Pattern.compile("\"([0-9A-Fa-f][0-9A-Za-z])\"");

    /** A standalone "for" keyword — anchors the unquoted sub-code list. */
    private static final Pattern FOR_KEYWORD = Pattern.compile("\\bfor\\b", Pattern.CASE_INSENSITIVE);

    /** Splits the post-{@code for} tail at any non-alphanumeric run (commas, whitespace, etc.). */
    private static final Pattern NON_ALNUM_SPLITTER = Pattern.compile("[^A-Za-z0-9]+");

    /**
     * Extract the sub-category whitelist from a per-CIC qualifier string. For example:
     * <ul>
     *   <li>{@code 'x for convertible bonds "22" or other corporate bonds "29" quoted in units'}
     *       paired with {@code CIC2} → {@code {"2", "9"}} (quoted style).</li>
     *   <li>{@code 'x for D4, D5'} paired with {@code CICD} → {@code {"4", "5"}} (unquoted style,
     *       the dominant variant in the spec).</li>
     *   <li>{@code 'x\nif item 32 set to "Floating"'} paired with any CIC → {@code {}} (cross-field
     *       conditional text — handled by separate XF rules, not by sub-category restriction).</li>
     * </ul>
     *
     * <p>The parser runs two passes that may both contribute:
     * <ol>
     *   <li>Quoted 2-char tokens whose first char matches the CIC class.</li>
     *   <li>Unquoted 2-char tokens appearing <strong>after</strong> a standalone {@code for}
     *       keyword, again filtered by class. Restricting unquoted detection to text that follows
     *       {@code for} keeps cross-field clauses ({@code if item X set to ...}) from contributing
     *       false positives.</li>
     * </ol>
     */
    static Set<String> parseSubcategoryQualifier(String cellText, String cicName) {
        if (cellText == null || cellText.isBlank()) return Set.of();
        if (cicName == null || !cicName.startsWith("CIC") || cicName.length() != 4) return Set.of();
        char expectedClass = Character.toUpperCase(cicName.charAt(3));

        Set<String> subs = new LinkedHashSet<>();

        // Pass 1: quoted 2-char tokens — robust against any prose around them.
        Matcher q = QUOTED_CIC_SUBCODE.matcher(cellText);
        while (q.find()) {
            String token = q.group(1).toUpperCase(Locale.ROOT);
            if (token.charAt(0) == expectedClass) {
                subs.add(String.valueOf(token.charAt(1)));
            }
        }

        // Pass 2: unquoted tokens after a "for" keyword (e.g. "x for 22", "x for D4, D5").
        Matcher forM = FOR_KEYWORD.matcher(cellText);
        if (forM.find()) {
            String tail = cellText.substring(forM.end());
            for (String tok : NON_ALNUM_SPLITTER.split(tail)) {
                if (tok.length() != 2) continue;
                char a = Character.toUpperCase(tok.charAt(0));
                char b = Character.toUpperCase(tok.charAt(1));
                if (a == expectedClass && Character.isLetterOrDigit(b)) {
                    subs.add(String.valueOf(b));
                }
            }
        }
        return subs;
    }

    /** True if the NUM_DATA cell looks like a field label ("1000_..." or "12_..."), not a section header. */
    static boolean looksLikeFieldLabel(String num) {
        if (isBlank(num)) return false;
        String t = num.trim();
        int us = t.indexOf('_');
        if (us <= 0) return false;
        String prefix = t.substring(0, us);
        // Numeric prefix (with optional trailing letter like "8b", "105a") indicates a field row.
        return prefix.chars().anyMatch(Character::isDigit);
    }
}
