package com.findatex.validator.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Wire shape of an anonymous usage event posted by the desktop app. Mirrors
 * {@code com.findatex.validator.stats.UsageEvent}. There is deliberately no
 * IP/country field — {@code country_code} is derived server-side from the
 * request source IP and the raw IP is never stored or logged.
 *
 * <p>Backward compatible: desktop builds from before 2026-09 omit
 * {@code eventType}, {@code status}, {@code javaMajor}, {@code input},
 * {@code external}, {@code exportKind}; the service defaults them to
 * {@code validate} / {@code ok} / null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageStatsDto(
        String eventType,
        String status,
        String installId,
        String source,
        String appVersion,
        String osName,
        Integer javaMajor,
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
        Input input,
        External external,
        Boolean isSample,
        String exportKind,
        String clientEventAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Input(String format, Long bytes, String namePattern) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record External(Integer lookups, Integer cacheHits, Integer durationMs, Integer errors) {
    }
}
