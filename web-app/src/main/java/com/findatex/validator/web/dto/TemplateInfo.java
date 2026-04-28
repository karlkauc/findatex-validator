package com.findatex.validator.web.dto;

import java.util.List;

public record TemplateInfo(
        String id,
        String displayName,
        List<VersionInfo> versions
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
}
