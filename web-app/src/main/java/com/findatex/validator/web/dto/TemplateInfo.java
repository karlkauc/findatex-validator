package com.findatex.validator.web.dto;

import java.util.List;

public record TemplateInfo(
        String id,
        String displayName,
        List<VersionInfo> versions,
        boolean externalAvailable,
        List<SampleInfo> samples
) {

    public record VersionInfo(
            String version,
            String label,
            String releaseDate,
            List<ProfileInfo> profiles
    ) {
    }

    public record ProfileInfo(
            String code,
            String displayName
    ) {
    }

    /**
     * One demo file offered for this template; {@link TemplateInfo#samples()} is
     * empty when none ships with the build (the UI then hides the action).
     * {@code version} is the spec version the fixture was generated for — the UI
     * picks the sample matching the selected version, otherwise the first one,
     * and must then switch to that version, since validating the file against
     * another version reports findings that are artefacts of the mismatch.
     */
    public record SampleInfo(
            String version,
            String url,
            String filename
    ) {
    }
}
