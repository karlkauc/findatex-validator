package com.tpt.validator.spec;

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Columns(
            int numData,
            int path,
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
