package com.findatex.validator.report;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.validation.Finding;

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
