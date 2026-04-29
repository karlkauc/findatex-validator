package com.findatex.validator.batch;

import com.findatex.validator.report.ScoreCategory;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Severity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Aggregated outcome of one folder-batch run.
 *
 * <p>{@code aggregateOverallScore} is the unweighted arithmetic mean of the per-file
 * {@link ScoreCategory#OVERALL} scores across {@link BatchFileStatus#OK} results only.
 * Files that failed to load are deliberately excluded — and so are non-existent
 * (zero-row) batches, which yield {@link OptionalDouble#empty()}. Mean-of-files
 * (rather than row-weighted) makes a single 50k-row file unable to drown out smaller
 * files: the unit of regulatory accountability is the file, not the cell.
 */
public record BatchSummary(
        List<BatchResult> results,
        long aggregateErrors,
        long aggregateWarnings,
        long aggregateInfos,
        OptionalDouble aggregateOverallScore,
        ProfileSet profileSet,
        TemplateVersion templateVersion,
        Set<ProfileKey> activeProfiles,
        Instant startedAt,
        Duration totalElapsed,
        boolean cancelled) {

    public BatchSummary {
        results = List.copyOf(Objects.requireNonNull(results, "results"));
        Objects.requireNonNull(aggregateOverallScore, "aggregateOverallScore");
        Objects.requireNonNull(profileSet, "profileSet");
        Objects.requireNonNull(templateVersion, "templateVersion");
        activeProfiles = Set.copyOf(Objects.requireNonNull(activeProfiles, "activeProfiles"));
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(totalElapsed, "totalElapsed");
    }

    /** Build a summary from raw results, computing aggregate counts and mean score. */
    public static BatchSummary of(List<BatchResult> results,
                                  ProfileSet profileSet,
                                  TemplateVersion templateVersion,
                                  Set<ProfileKey> activeProfiles,
                                  Instant startedAt,
                                  Duration totalElapsed,
                                  boolean cancelled) {
        long errors = 0, warnings = 0, infos = 0;
        double scoreSum = 0.0;
        int scoreCount = 0;
        for (BatchResult r : results) {
            for (var f : r.findings()) {
                switch (f.severity()) {
                    case ERROR -> errors++;
                    case WARNING -> warnings++;
                    case INFO -> infos++;
                }
            }
            if (r.status() == BatchFileStatus.OK && r.report() != null) {
                Double overall = r.report().scores().get(ScoreCategory.OVERALL);
                if (overall != null) {
                    scoreSum += overall;
                    scoreCount++;
                }
            }
        }
        OptionalDouble mean = scoreCount == 0
                ? OptionalDouble.empty()
                : OptionalDouble.of(scoreSum / scoreCount);
        return new BatchSummary(results, errors, warnings, infos, mean, profileSet,
                templateVersion, activeProfiles, startedAt, totalElapsed, cancelled);
    }

    public long countWithStatus(BatchFileStatus status) {
        return results.stream().filter(r -> r.status() == status).count();
    }

    public long aggregateBySeverity(Severity severity) {
        return switch (severity) {
            case ERROR -> aggregateErrors;
            case WARNING -> aggregateWarnings;
            case INFO -> aggregateInfos;
        };
    }
}
