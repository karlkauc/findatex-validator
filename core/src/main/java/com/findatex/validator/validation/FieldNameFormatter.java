package com.findatex.validator.validation;

import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.SpecCatalog;

import java.util.regex.Pattern;

/**
 * Normalizes the {@code fieldName} carried by a {@link Finding} to a uniform
 * {@code "Field N (Description)"} shape, so the UI tables and XLSX reports
 * never mix bare {@code "Field 34"} with descriptive {@code "Field 33 (Coupon rate)"}.
 *
 * <p>The description is derived from the catalog's {@link FieldSpec#name()},
 * which holds either TPT's labelled {@code numData} ({@code "33_Coupon_rate"})
 * or EET/EMT/EPT's column-B label ({@code "00010_EET_Version"}). The leading
 * {@code "\d+_"} numeric prefix is stripped and remaining underscores are
 * replaced by spaces. When the catalog has no entry for the {@code fieldNum},
 * a description is recovered from the raw fieldName the rule emitted —
 * stripping any redundant {@code "Field N"} / {@code "Field N (...)"} prefix.
 */
public final class FieldNameFormatter {

    private static final Pattern LEADING_NUMERIC_PREFIX = Pattern.compile("^\\d+_");
    private static final Pattern REDUNDANT_FIELD_PREFIX =
            Pattern.compile("^\\s*[Ff]ield\\s+\\S+\\s*(?:\\((.*)\\))?\\s*$");

    private FieldNameFormatter() {}

    /**
     * Returns the canonical display string for a finding's "Field name" column.
     * @param fieldNum the finding's field number (e.g. {@code "33"}); may be null
     *                 for portfolio-level / global findings.
     * @param rawName  the finding's existing fieldName (whatever the rule chose).
     * @param catalog  spec catalog used to resolve descriptive names.
     */
    public static String format(String fieldNum, String rawName, SpecCatalog catalog) {
        if (fieldNum == null || fieldNum.isBlank()) {
            return rawName == null ? "" : rawName;
        }
        String description = descriptionFor(fieldNum, rawName, catalog);
        if (description == null || description.isBlank()) {
            return "Field " + fieldNum;
        }
        return "Field " + fieldNum + " (" + description + ")";
    }

    private static String descriptionFor(String fieldNum, String rawName, SpecCatalog catalog) {
        if (catalog != null) {
            FieldSpec spec = catalog.byNumKey(fieldNum).orElse(null);
            if (spec != null) {
                String fromCatalog = prettifyCatalogName(spec.name());
                if (fromCatalog != null && !fromCatalog.isBlank()) return fromCatalog;
            }
        }
        return descriptionFromRaw(rawName);
    }

    /** "33_Coupon_rate" → "Coupon rate"; "00010_EET_Version" → "EET Version". */
    private static String prettifyCatalogName(String name) {
        if (name == null) return null;
        String stripped = LEADING_NUMERIC_PREFIX.matcher(name.trim()).replaceFirst("");
        return stripped.replace('_', ' ').trim();
    }

    /**
     * Extracts a usable description from whatever the rule put into fieldName.
     * Handles the common shapes emitted across the codebase:
     * <ul>
     *   <li>{@code "Field 33 (Coupon rate)"} → {@code "Coupon rate"}</li>
     *   <li>{@code "Field 34"}               → {@code ""} (no embedded description)</li>
     *   <li>{@code "33_Coupon_rate"}         → {@code "Coupon rate"}</li>
     *   <li>any other plain label            → returned as-is</li>
     * </ul>
     */
    private static String descriptionFromRaw(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        var m = REDUNDANT_FIELD_PREFIX.matcher(trimmed);
        if (m.matches()) {
            String inside = m.group(1);
            return inside == null ? "" : inside.trim();
        }
        if (LEADING_NUMERIC_PREFIX.matcher(trimmed).find()) {
            return prettifyCatalogName(trimmed);
        }
        return trimmed;
    }
}
