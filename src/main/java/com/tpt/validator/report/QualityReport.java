package com.tpt.validator.report;

import com.tpt.validator.domain.TptFile;
import com.tpt.validator.spec.Profile;
import com.tpt.validator.validation.Finding;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record QualityReport(
        TptFile file,
        Set<Profile> activeProfiles,
        List<Finding> findings,
        Map<ScoreCategory, Double> scores,
        Map<Profile, Map<ScoreCategory, Double>> perProfileScores,
        Instant generatedAt) {
}
