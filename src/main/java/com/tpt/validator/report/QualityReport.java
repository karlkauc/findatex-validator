package com.tpt.validator.report;

import com.tpt.validator.domain.TptFile;
import com.tpt.validator.template.api.ProfileKey;
import com.tpt.validator.validation.Finding;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record QualityReport(
        TptFile file,
        Set<ProfileKey> activeProfiles,
        List<Finding> findings,
        Map<ScoreCategory, Double> scores,
        Map<ProfileKey, Map<ScoreCategory, Double>> perProfileScores,
        Instant generatedAt) {
}
