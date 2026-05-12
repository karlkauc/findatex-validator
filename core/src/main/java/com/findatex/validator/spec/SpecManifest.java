package com.findatex.validator.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * On-disk description of one bundled spec XLSX layout. Loaded by Jackson from {@code *-info.json}
 * sibling files; consumed by {@code ManifestDrivenSpecLoader} (sub-step 2.3) to build a
 * {@link SpecCatalog} without per-template Java loaders. All column indices are 1-based to match
 * the historical {@code SpecLoader} convention.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpecManifest(
        String templateId,
        String version,
        String releaseDate,
        String sheetName,
        int firstDataRow,
        Columns columns,
        ApplicabilityColumns applicabilityColumns,
        List<ProfileColumn> profileColumns) {

    /**
     * Spec-sheet column layout. All indices are 1-based.
     *
     * <p>{@code numData} (col A) is always present — it holds either the descriptive label (TPT V7)
     * or the bare sequence number (EMT/EET/EPT). {@code name} is optional and points to the column
     * containing the field's display label when {@code numData} is just a number (EMT/EET/EPT use
     * col B for this); omitting it means the loader falls back to {@code numData}. {@code path} is
     * optional and points to the FundsXML element path when one exists (TPT only).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Columns(
            int numData,
            Integer name,
            Integer path,
            int definition,
            int codification,
            int comment,
            int primaryFlag) {
    }

    /**
     * Optional applicability scope description. {@code kind} = {@code "CIC"} for TPT;
     * {@code "none"} (or {@code null}) for templates without an applicability dimension
     * (EET / EMT / EPT).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApplicabilityColumns(
            String kind,
            Integer first,
            Integer last,
            List<String> names) {
    }

    /**
     * One regulatory profile bound to spec column(s). {@code kind} = {@code "flag"} reads a single
     * M/C/O/I/N/A cell; {@code kind} = {@code "presenceMerge"} reads several presence columns and
     * merges to the strictest non-blank flag (used for the IORP/EIOPA/ECB composite on TPT).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfileColumn(
            String code,
            String display,
            Integer column,
            List<Integer> columns,
            String kind) {
    }
}
