package com.findatex.validator.report;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.Flag;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QualityScorer {

    private static final double W_MANDATORY = 0.40;
    private static final double W_FORMAT    = 0.20;
    private static final double W_CLOSED    = 0.15;
    private static final double W_CROSS     = 0.15;
    private static final double W_PROFILE   = 0.10;

    private final SpecCatalog catalog;

    public QualityScorer(SpecCatalog catalog) {
        this.catalog = catalog;
    }

    public QualityReport score(TptFile file, Set<ProfileKey> active, List<Finding> findings) {
        Map<ScoreCategory, Double> overall = new EnumMap<>(ScoreCategory.class);
        Map<ProfileKey, Map<ScoreCategory, Double>> perProfile = new LinkedHashMap<>();

        long mandatoryAll = 0, mandatoryMissing = 0;
        for (ProfileKey p : active) {
            long total = countMandatorySlots(file, p);
            long missing = findings.stream()
                    .filter(f -> p.equals(f.profile()) && f.severity() == Severity.ERROR)
                    .filter(f -> f.ruleId().startsWith("PRESENCE/"))
                    .count();
            double score = total == 0 ? 1.0 : 1.0 - (double) missing / total;
            score = clamp(score);

            Map<ScoreCategory, Double> ps = new EnumMap<>(ScoreCategory.class);
            ps.put(ScoreCategory.MANDATORY_COMPLETENESS, score);

            // Profile completeness combines M+C presence findings for the profile.
            long condMissing = findings.stream()
                    .filter(f -> p.equals(f.profile()))
                    .filter(f -> f.ruleId().startsWith("COND_PRESENCE/"))
                    .count();
            long condTotal = countConditionalSlots(file, p);
            double condScore = condTotal == 0 ? 1.0 : 1.0 - (double) condMissing / condTotal;
            condScore = clamp(condScore);
            double profileCompleteness = clamp(0.7 * score + 0.3 * condScore);
            ps.put(ScoreCategory.PROFILE_COMPLETENESS, profileCompleteness);
            perProfile.put(p, ps);

            mandatoryAll += total;
            mandatoryMissing += missing;
        }

        double overallMandatory = mandatoryAll == 0 ? 1.0 : 1.0 - (double) mandatoryMissing / mandatoryAll;
        overall.put(ScoreCategory.MANDATORY_COMPLETENESS, clamp(overallMandatory));

        long nonEmptyCells = countNonEmptyCells(file);
        long formatErrors = findings.stream()
                .filter(f -> f.severity() == Severity.ERROR)
                .filter(f -> f.ruleId().startsWith("FORMAT/")
                        && !isClosedListFinding(f))
                .count();
        double formatScore = nonEmptyCells == 0 ? 1.0 : 1.0 - (double) formatErrors / nonEmptyCells;
        overall.put(ScoreCategory.FORMAT_CONFORMANCE, clamp(formatScore));

        long closedListCells = countNonEmptyClosedListCells(file);
        long closedListErrors = findings.stream()
                .filter(f -> f.severity() == Severity.ERROR)
                .filter(this::isClosedListFinding)
                .count();
        double closedScore = closedListCells == 0 ? 1.0 : 1.0 - (double) closedListErrors / closedListCells;
        overall.put(ScoreCategory.CLOSED_LIST_CONFORMANCE, clamp(closedScore));

        long crossFieldErrors = findings.stream()
                .filter(f -> f.ruleId().startsWith("XF-"))
                .filter(f -> f.severity() != Severity.INFO)
                .count();
        long crossFieldRules = findings.stream()
                .map(Finding::ruleId)
                .filter(s -> s.startsWith("XF-"))
                .distinct()
                .count();
        crossFieldRules = Math.max(crossFieldRules, 12); // approx total XF rules
        double crossScore = crossFieldRules == 0 ? 1.0
                : 1.0 - (double) crossFieldErrors / Math.max(crossFieldRules * Math.max(file.rows().size(), 1), 1);
        overall.put(ScoreCategory.CROSS_FIELD_CONSISTENCY, clamp(crossScore));

        double avgProfileCompleteness = perProfile.isEmpty() ? 1.0
                : perProfile.values().stream()
                    .mapToDouble(m -> m.getOrDefault(ScoreCategory.PROFILE_COMPLETENESS, 1.0))
                    .average().orElse(1.0);

        double overallScore =
                W_MANDATORY * overall.get(ScoreCategory.MANDATORY_COMPLETENESS)
              + W_FORMAT    * overall.get(ScoreCategory.FORMAT_CONFORMANCE)
              + W_CLOSED    * overall.get(ScoreCategory.CLOSED_LIST_CONFORMANCE)
              + W_CROSS     * overall.get(ScoreCategory.CROSS_FIELD_CONSISTENCY)
              + W_PROFILE   * avgProfileCompleteness;
        overall.put(ScoreCategory.OVERALL, clamp(overallScore));

        return new QualityReport(file, active, findings, overall, perProfile, Instant.now());
    }

    private boolean isClosedListFinding(Finding f) {
        if (!f.ruleId().startsWith("FORMAT/")) return false;
        // Heuristic: closed-list errors carry the "is not in the closed list" message.
        return f.message() != null && f.message().contains("closed list");
    }

    private long countMandatorySlots(TptFile file, ProfileKey p) {
        long total = 0;
        for (FieldSpec spec : catalog.fields()) {
            if (spec.flag(p) != Flag.M) continue;
            for (TptRow row : file.rows()) {
                if (applies(spec, row)) total++;
            }
        }
        return total;
    }

    private long countConditionalSlots(TptFile file, ProfileKey p) {
        long total = 0;
        for (FieldSpec spec : catalog.fields()) {
            if (spec.flag(p) != Flag.C) continue;
            for (TptRow row : file.rows()) {
                if (applies(spec, row) && row.cic().isPresent()) total++;
            }
        }
        return total;
    }

    private boolean applies(FieldSpec spec, TptRow row) {
        if (spec.appliesToAllCic()) return true;
        if (row.cic().isEmpty()) return true;
        return spec.appliesToCic(row.cic().get().categoryDigit());
    }

    private long countNonEmptyCells(TptFile file) {
        long n = 0;
        for (TptRow row : file.rows()) n += row.all().values().stream().filter(c -> !c.isEmpty()).count();
        return n;
    }

    private long countNonEmptyClosedListCells(TptFile file) {
        long n = 0;
        for (FieldSpec spec : catalog.fields()) {
            if (!spec.codification().hasClosedList()) continue;
            for (TptRow row : file.rows()) {
                if (row.stringValue(spec).isPresent()) n++;
            }
        }
        return n;
    }

    private static double clamp(double v) {
        if (Double.isNaN(v)) return 0;
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
