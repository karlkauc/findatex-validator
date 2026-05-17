package com.findatex.validator.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Wire shape of an anonymous usage event posted by the desktop app. Mirrors
 * {@code com.findatex.validator.stats.UsageEvent}. There is deliberately no
 * IP/country field — {@code country_code} is derived server-side from the
 * request source IP and the raw IP is never stored or logged.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageStatsDto(
        String installId,
        String source,
        String appVersion,
        String osName,
        String templateId,
        String templateVersion,
        List<String> profiles,
        String mode,
        Integer fileCount,
        Integer rowCount,
        Integer errorCount,
        Integer warningCount,
        Integer infoCount,
        Double overallScore,
        Integer durationMs,
        Boolean externalEnabled,
        List<String> ruleIds,
        String clientEventAt) {
}
