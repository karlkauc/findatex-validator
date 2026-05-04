package com.findatex.validator.docs;

import com.findatex.validator.external.ExternalValidationConfig;
import com.findatex.validator.external.ExternalValidationConfig.IdentifierRef;
import com.findatex.validator.spec.CodificationDescriptor;
import com.findatex.validator.spec.CodificationKind;
import com.findatex.validator.spec.FieldSpec;
import com.findatex.validator.spec.Flag;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.ProfileSet;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.validation.Rule;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.rules.ConditionalPresenceRule;
import com.findatex.validator.validation.rules.FormatRule;
import com.findatex.validator.validation.rules.IsinRule;
import com.findatex.validator.validation.rules.LeiRule;
import com.findatex.validator.validation.rules.PresenceRule;
import com.findatex.validator.validation.rules.RuleDoc;
import com.findatex.validator.validation.rules.crossfield.CashPercentageRule;
import com.findatex.validator.validation.rules.crossfield.CompleteScrDeliveryRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalAnyFieldPresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalAnyOfRequirement;
import com.findatex.validator.validation.rules.crossfield.ConditionalAnySourceFieldPresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalAnySourceRequirement;
import com.findatex.validator.validation.rules.crossfield.ConditionalFieldAbsenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalFieldPresenceRule;
import com.findatex.validator.validation.rules.crossfield.ConditionalRequirement;
import com.findatex.validator.validation.rules.crossfield.CouponFrequencyRule;
import com.findatex.validator.validation.rules.crossfield.CustodianPairRule;
import com.findatex.validator.validation.rules.crossfield.DateOrderRule;
import com.findatex.validator.validation.rules.crossfield.EetVersionRule;
import com.findatex.validator.validation.rules.crossfield.EmtVersionRule;
import com.findatex.validator.validation.rules.crossfield.EptVersionRule;
import com.findatex.validator.validation.rules.crossfield.InterestRateTypeRule;
import com.findatex.validator.validation.rules.crossfield.MaturityAfterReportingRule;
import com.findatex.validator.validation.rules.crossfield.NavConsistencyRule;
import com.findatex.validator.validation.rules.crossfield.PikRule;
import com.findatex.validator.validation.rules.crossfield.PositionWeightSumRule;
import com.findatex.validator.validation.rules.crossfield.TptVersionRule;
import com.findatex.validator.validation.rules.crossfield.UnderlyingCicRule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Produces, for every (template, version) bundled in {@link TemplateRegistry}, a Markdown file
 * documenting the validator's general rules, cross-field rules, and per-field checks. The
 * document is generated from the live {@link TemplateDefinition#ruleSetFor(TemplateVersion)} so
 * it cannot drift from the rules actually executed at runtime.
 *
 * <p>Run via {@code mvn -pl core -Pdocs exec:java -Dexec.args="docs/rules"} (the {@code docs}
 * profile is wired in the root POM). Output filenames follow {@code <template>-<slug>.md} where
 * the slug is the lowercased version with dots replaced by dashes, e.g. {@code tpt-v7-0.md}.
 */
public final class RuleDocGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public static void main(String[] args) {
        Path out = Path.of(args.length == 0 ? "docs/rules" : args[0]);
        try {
            new RuleDocGenerator().generate(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.out.println("Wrote rule docs to " + out.toAbsolutePath());
    }

    /** Generates docs for every registered template and writes them to {@code outDir}. */
    public void generate(Path outDir) throws IOException {
        TemplateRegistry.init();
        Files.createDirectories(outDir);

        List<String> indexEntries = new ArrayList<>();
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (TemplateDefinition def : TemplateRegistry.all()) {
            for (TemplateVersion v : def.versions()) {
                String slug = slug(def, v);
                Path file = outDir.resolve(slug + ".md");
                String md = renderTemplate(def, v);
                Files.writeString(file, md, StandardCharsets.UTF_8);
                indexEntries.add("- [" + def.displayName() + " " + v.version() + "]("
                        + slug + ".md) — " + v.label());
                if (!first) json.append(",\n");
                json.append("  {")
                        .append("\"slug\": \"").append(slug).append("\", ")
                        .append("\"templateId\": \"").append(def.id().name()).append("\", ")
                        .append("\"templateDisplayName\": \"").append(escapeJson(def.displayName())).append("\", ")
                        .append("\"version\": \"").append(escapeJson(v.version())).append("\", ")
                        .append("\"label\": \"").append(escapeJson(v.label())).append("\"")
                        .append('}');
                first = false;
            }
        }
        json.append("\n]\n");
        Files.writeString(outDir.resolve("index.json"), json.toString(), StandardCharsets.UTF_8);
        Files.writeString(outDir.resolve("README.md"), renderIndex(indexEntries), StandardCharsets.UTF_8);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // -----------------------------------------------------------------------------------------
    // Per-template rendering
    // -----------------------------------------------------------------------------------------

    private String renderTemplate(TemplateDefinition def, TemplateVersion v) {
        SpecCatalog catalog = def.specLoaderFor(v).load();
        ProfileSet profileSet = def.profilesFor(v);
        Set<ProfileKey> activeProfiles = new java.util.LinkedHashSet<>(profileSet.all());
        List<Rule> rules = def.ruleSetFor(v).build(catalog, activeProfiles);
        ExternalValidationConfig ext = def.externalValidationConfigFor(v);

        BucketedRules buckets = bucketRules(rules);

        MarkdownEmitter md = new MarkdownEmitter();
        renderHeader(md, def, v, profileSet);
        renderScoringSection(md);
        renderProfilesSection(md, profileSet, catalog);
        renderGeneralRulesSection(md, def, v, buckets, ext);
        renderCrossFieldSection(md, def, buckets);
        renderPerFieldSection(md, catalog, profileSet, buckets, ext);

        return md.build();
    }

    private void renderHeader(MarkdownEmitter md, TemplateDefinition def, TemplateVersion v,
                              ProfileSet profileSet) {
        md.heading1("FinDatEx " + def.displayName() + " Validation Reference (" + v.version() + ")");
        md.line("Spec: `" + v.resourcePath() + "`");
        md.line("Manifest: `" + v.manifestResource() + "`");
        md.line("Released: " + v.releaseDate().format(DATE) + "  ·  Sheet: `" + v.sheetName() + "`");
        String profiles = profileSet.all().stream()
                .map(p -> p.displayName() + " (`" + p.code() + "`)")
                .reduce((a, b) -> a + ", " + b).orElse("(none)");
        md.line("Profiles: " + profiles);
        md.blank();
        md.blockquote("Generated file — do not edit by hand. Regenerate via "
                + "`mvn -pl core -Pdocs exec:java -Dexec.args=\"docs/rules\"`.");
        md.rule();
    }

    private void renderScoringSection(MarkdownEmitter md) {
        md.heading2("1. How this validator scores your file");
        md.paragraph("The overall quality score is a weighted blend of five sub-scores."
                + " Only `Severity.ERROR` findings lower the score; `WARNING` and `INFO` are reported"
                + " but ignored by the scorer.");
        md.table(
                List.of("Dimension", "Weight", "Computation"),
                List.of(
                        MarkdownEmitter.row("MANDATORY_COMPLETENESS", "40 %",
                                "1 − (missing M-cells / total M-cells) across active profiles"),
                        MarkdownEmitter.row("FORMAT_CONFORMANCE", "20 %",
                                "1 − (format errors / non-empty cells)"),
                        MarkdownEmitter.row("CLOSED_LIST_CONFORMANCE", "15 %",
                                "1 − (closed-list errors / non-empty closed-list cells)"),
                        MarkdownEmitter.row("CROSS_FIELD_CONSISTENCY", "15 %",
                                "1 − (XF errors / max(distinct XF rules × rows, 1))"),
                        MarkdownEmitter.row("PROFILE_COMPLETENESS", "10 %",
                                "mean over profiles of (0.7 × M-completeness + 0.3 × C-completeness)")));
        md.paragraph("Findings are routed to dimensions by their rule-id prefix:");
        md.table(
                List.of("Rule prefix", "Dimension"),
                List.of(
                        MarkdownEmitter.row("`PRESENCE/`", "MANDATORY_COMPLETENESS + PROFILE (M leg)"),
                        MarkdownEmitter.row("`COND_PRESENCE/`", "PROFILE_COMPLETENESS (C leg)"),
                        MarkdownEmitter.row("`FORMAT/` (closed-list message)", "CLOSED_LIST_CONFORMANCE"),
                        MarkdownEmitter.row("`FORMAT/` (other)", "FORMAT_CONFORMANCE"),
                        MarkdownEmitter.row("`ISIN/`, `LEI/`", "FORMAT_CONFORMANCE"),
                        MarkdownEmitter.row("`XF-…`, template `*-XF-*`", "CROSS_FIELD_CONSISTENCY"),
                        MarkdownEmitter.row("`*-ONLINE` (GLEIF / OpenFIGI)", "not scored (advisory)")));
    }

    private void renderProfilesSection(MarkdownEmitter md, ProfileSet profileSet, SpecCatalog catalog) {
        md.heading2("2. Profiles");
        md.paragraph("Selecting a profile in the UI tells the validator to enforce the M (mandatory)"
                + " and C (conditional) flags from that profile's column in the spec. Multiple profiles"
                + " can be selected simultaneously — a field is mandatory if any selected profile says so.");
        List<List<String>> rows = new ArrayList<>();
        for (ProfileKey p : profileSet.all()) {
            int mCount = 0, cCount = 0;
            for (FieldSpec f : catalog.fields()) {
                Flag fl = f.flag(p);
                if (fl == Flag.M) mCount++;
                if (fl == Flag.C) cCount++;
            }
            rows.add(MarkdownEmitter.row("`" + p.code() + "`", p.displayName(),
                    Integer.toString(mCount), Integer.toString(cCount)));
        }
        md.table(List.of("Code", "Display name", "Mandatory fields", "Conditional fields"), rows);
    }

    // -----------------------------------------------------------------------------------------
    // Section 3 — general rules
    // -----------------------------------------------------------------------------------------

    private void renderGeneralRulesSection(MarkdownEmitter md, TemplateDefinition def,
                                           TemplateVersion v, BucketedRules buckets,
                                           ExternalValidationConfig ext) {
        md.heading2("3. General rules");
        md.paragraph("The engines below run on every applicable field/row independently of the"
                + " template-specific cross-field block in §4.");

        md.heading3("Version rule");
        if (buckets.versionRule != null) {
            md.bullet("**Rule ID:** `" + buckets.versionRule.id() + "`");
            md.bullet("**Severity:** ERROR (INFO if the version cell is absent)");
            md.bullet("**Expected token:** `" + buckets.versionRule.expectedToken + "`");
            md.bullet("**Trigger:** the version cell of the file does not contain the expected token.");
            md.bullet("**Score impact:** " + ScoreImpactDescriber.forCrossField(Severity.ERROR));
            md.blank();
        } else {
            md.paragraph("(no version rule registered — should not happen)");
        }

        md.heading3("Presence engine (`PRESENCE/{numKey}/{profile}`)");
        md.bullet("**What it checks:** for every `FieldSpec` flagged M for an active profile and"
                + " applicable to the row's CIC, the cell value must be non-empty.");
        md.bullet("**Severity:** ERROR.");
        md.bullet("**Score impact:** " + ScoreImpactDescriber.forPresence());
        md.bullet("**Active rule instances:** " + buckets.presenceCount + " (one per field × profile).");
        md.blank();

        md.heading3("Conditional-presence engine (`COND_PRESENCE/{numKey}/{profile}`)");
        md.bullet("**What it checks:** for every `FieldSpec` flagged C for an active profile and"
                + " whose CIC applicability matches the row's CIC, the cell value must be non-empty.");
        md.bullet("**Severity:** WARNING.");
        md.bullet("**Score impact:** " + ScoreImpactDescriber.forConditionalPresence());
        md.bullet("**Active rule instances:** " + buckets.conditionalPresenceCount
                + " (one per CIC-restricted field × profile).");
        md.blank();

        md.heading3("Format engine (`FORMAT/{numKey}`)");
        md.bullet("**What it checks:** every populated cell is validated against its codification kind:"
                + " ISO 4217 currency, ISO 3166-A2 country, ISO 8601 date, NACE, CIC 4-char, alphanumeric"
                + " length, numeric, closed-list membership.");
        md.bullet("**Severity:** ERROR.");
        md.bullet("**Score impact:** " + ScoreImpactDescriber.forFormat());
        md.bullet("**Active rule instances:** " + buckets.formatCount + " (one per field).");
        md.blank();

        if (!buckets.isinRules.isEmpty()) {
            md.heading3("ISIN engine (`ISIN/{code}/{type}`)");
            md.bullet("**What it checks:** when the type cell is `1` (ISIN), the code cell must be a"
                    + " 12-character alphanumeric value with a valid ISO 6166 Luhn checksum.");
            md.bullet("**Severity:** ERROR.");
            md.bullet("**Score impact:** " + ScoreImpactDescriber.forIdentifier());
            for (IsinRule r : buckets.isinRules) {
                md.bullet("`" + r.id() + "` — code field `" + r.codeNumKey()
                        + "`, type field `" + r.typeNumKey() + "`");
            }
            md.blank();
        }

        if (!buckets.leiRules.isEmpty()) {
            md.heading3("LEI engine (`LEI/{code}/{type}`)");
            md.bullet("**What it checks:** when the type cell is `1` (LEI), the code cell must be a"
                    + " 20-character ISO 17442 LEI with the mod-97 checksum.");
            md.bullet("**Severity:** ERROR.");
            md.bullet("**Score impact:** " + ScoreImpactDescriber.forIdentifier());
            for (LeiRule r : buckets.leiRules) {
                md.bullet("`" + r.id() + "` — code field `" + r.codeNumKey()
                        + "`, type field `" + r.typeNumKey() + "`");
            }
            md.blank();
        }

        md.heading3("External validation (opt-in)");
        if (ext == null || ext.isEmpty()) {
            md.paragraph("This template does not declare any external-validation columns.");
        } else {
            md.bullet("**Off by default.** Operators enable it via the Settings dialog (desktop)"
                    + " or the `FINDATEX_WEB_EXTERNAL_ENABLED` env var (web).");
            md.bullet("**ISIN lookup (OpenFIGI):** "
                    + (ext.isinFields().isEmpty() ? "—"
                    : ext.isinFields().stream().map(RuleDocGenerator::describeIdentifierRef)
                        .reduce((a, b) -> a + "; " + b).orElse("")));
            md.bullet("**LEI lookup (GLEIF):** "
                    + (ext.leiFields().isEmpty() ? "—"
                    : ext.leiFields().stream().map(RuleDocGenerator::describeIdentifierRef)
                        .reduce((a, b) -> a + "; " + b).orElse("")));
            md.bullet("**Score impact:** " + ScoreImpactDescriber.forExternal());
            md.blank();
        }
    }

    private static String describeIdentifierRef(IdentifierRef r) {
        if (r.hasTypeFlag()) {
            return "field `" + r.codeKey() + "` (when type field `" + r.typeKey()
                    + "` = `" + r.expectedTypeFlag() + "`)";
        }
        return "field `" + r.codeKey() + "` (single-purpose, no type flag)";
    }

    // -----------------------------------------------------------------------------------------
    // Section 4 — cross-field rules
    // -----------------------------------------------------------------------------------------

    private void renderCrossFieldSection(MarkdownEmitter md, TemplateDefinition def,
                                         BucketedRules buckets) {
        md.heading2("4. Cross-field rules");
        if (buckets.crossField.isEmpty()) {
            md.paragraph("No cross-field rules are wired for this template/version. Regulatory"
                    + " cross-field logic for " + def.displayName() + " is deferred — see"
                    + " `docs/SME_QUESTIONS/" + def.id().name().toLowerCase(Locale.ROOT)
                    + "-cross-field-rules.md` for the open SME briefs.");
            return;
        }
        md.paragraph("Each rule below fires per row when its trigger condition holds and the"
                + " expected target field state is violated. The score impact column applies to"
                + " every individual finding the rule emits.");
        for (CrossFieldEntry e : buckets.crossField) {
            md.heading3(e.ruleId + " — " + e.summary);
            md.bullet("**Severity:** " + e.severity);
            md.bullet("**Trigger:** " + e.triggerText);
            md.bullet("**Required:** " + e.requirementText);
            if (!e.sourceFields.isEmpty()) {
                md.bullet("**Source field(s):** " + joinFieldRefs(e.sourceFields));
            }
            if (!e.targetFields.isEmpty()) {
                md.bullet("**Target field(s):** " + joinFieldRefs(e.targetFields));
            }
            md.bullet("**Score impact:** " + ScoreImpactDescriber.forCrossField(e.severity));
            md.blank();
        }
    }

    // -----------------------------------------------------------------------------------------
    // Section 5 — per-field catalog
    // -----------------------------------------------------------------------------------------

    private void renderPerFieldSection(MarkdownEmitter md, SpecCatalog catalog, ProfileSet profileSet,
                                       BucketedRules buckets, ExternalValidationConfig ext) {
        md.heading2("5. Per-field catalog");
        md.paragraph("One entry per `FieldSpec` in spec order. Each entry lists every check that"
                + " can fire on the field, with the profile scope, severity, trigger condition,"
                + " and quantified score impact.");

        Map<String, Set<String>> extByField = externalCoverageByField(ext);

        for (FieldSpec spec : catalog.fields()) {
            renderFieldEntry(md, spec, profileSet, buckets, extByField.get(spec.numKey()));
        }
    }

    private void renderFieldEntry(MarkdownEmitter md, FieldSpec spec, ProfileSet profileSet,
                                  BucketedRules buckets, Set<String> extCoverage) {
        md.heading3("Field " + spec.numKey() + " — " + spec.numData());
        if (spec.fundXmlPath() != null && !spec.fundXmlPath().isBlank()) {
            md.line("Path: `" + spec.fundXmlPath() + "`");
        }
        md.line("Codification: " + describeCodification(spec.codification()));
        md.line("Applicability: " + describeApplicability(spec));
        if (spec.definition() != null && !spec.definition().isBlank()) {
            md.line("Definition: " + flattenWhitespace(spec.definition()));
        }
        md.blank();

        md.heading4("Flag per profile");
        List<List<String>> flagRows = new ArrayList<>();
        for (ProfileKey p : profileSet.all()) {
            Flag f = spec.flag(p);
            flagRows.add(MarkdownEmitter.row("`" + p.code() + "`", p.displayName(),
                    f == Flag.UNKNOWN ? "—" : f.name(), describeFlag(f)));
        }
        md.table(List.of("Code", "Display name", "Flag", "Meaning"), flagRows);

        md.heading4("Checks");
        List<List<String>> checkRows = new ArrayList<>();
        // Presence rules
        for (PresenceRule r : buckets.presenceByField.getOrDefault(spec.numKey(), List.of())) {
            checkRows.add(MarkdownEmitter.row(
                    "`" + r.id() + "`",
                    r.profile().displayName(),
                    "ERROR",
                    "Cell is empty for an active row of profile " + r.profile().displayName(),
                    "The mandatory cell is missing — file is incomplete for this profile.",
                    "MANDATORY_COMPLETENESS −1/N, PROFILE_COMPLETENESS (M leg) −1/N"));
        }
        // Conditional-presence rules
        for (ConditionalPresenceRule r : buckets.conditionalPresenceByField.getOrDefault(spec.numKey(), List.of())) {
            checkRows.add(MarkdownEmitter.row(
                    "`" + r.id() + "`",
                    r.profile().displayName(),
                    "WARNING",
                    "Row's CIC matches the field's applicability list and the cell is empty",
                    "Conditionally-required cell missing for this profile.",
                    "PROFILE_COMPLETENESS (C leg) −1/N"));
        }
        // Format rule (always one per field)
        FormatRule fmt = buckets.formatByField.get(spec.numKey());
        if (fmt != null) {
            checkRows.add(MarkdownEmitter.row(
                    "`" + fmt.id() + "`",
                    "(all)",
                    "ERROR",
                    "Populated cell does not match the codification (" + spec.codification().kind() + ")",
                    "Value cannot be parsed/used downstream.",
                    "FORMAT_CONFORMANCE −1/M (or CLOSED_LIST_CONFORMANCE −1/M for closed-list mismatches)"));
        }
        // ISIN rules where this field is the code field
        for (IsinRule r : buckets.isinRules) {
            if (r.codeNumKey().equals(spec.numKey())) {
                checkRows.add(MarkdownEmitter.row(
                        "`" + r.id() + "`",
                        "(all)",
                        "ERROR",
                        "Type field `" + r.typeNumKey() + "` = `1` and this cell is not a valid 12-char ISIN with Luhn checksum",
                        "Identifier cannot be resolved against ISO 6166.",
                        "FORMAT_CONFORMANCE −1/M"));
            }
        }
        // LEI rules where this field is the code field
        for (LeiRule r : buckets.leiRules) {
            if (r.codeNumKey().equals(spec.numKey())) {
                checkRows.add(MarkdownEmitter.row(
                        "`" + r.id() + "`",
                        "(all)",
                        "ERROR",
                        "Type field `" + r.typeNumKey() + "` = `1` and this cell is not a valid 20-char LEI with mod-97 checksum",
                        "Identifier cannot be resolved against ISO 17442 (GLEIF).",
                        "FORMAT_CONFORMANCE −1/M"));
            }
        }
        // Cross-field rules where this field is the target
        for (CrossFieldEntry e : buckets.crossField) {
            if (!e.targetFields.contains(spec.numKey())) continue;
            checkRows.add(MarkdownEmitter.row(
                    "`" + e.ruleId + "`",
                    "(all)",
                    e.severity.name(),
                    e.triggerText,
                    e.requirementText,
                    ScoreImpactDescriber.forCrossField(e.severity)));
        }

        if (checkRows.isEmpty()) {
            md.paragraph("_No active checks — the field has no profile flag of M or C and no"
                    + " cross-field rule targets it. Format errors will still be reported when"
                    + " the cell is populated._");
        } else {
            md.table(
                    List.of("Rule ID", "Profile(s)", "Severity", "Triggers when",
                            "Failure consequence", "Score impact"),
                    checkRows);
        }

        // Cross-field references (this field as source)
        List<String> sourceRefs = new ArrayList<>();
        for (CrossFieldEntry e : buckets.crossField) {
            if (e.sourceFields.contains(spec.numKey())) sourceRefs.add(e.ruleId);
        }
        if (!sourceRefs.isEmpty()) {
            md.line("**Referenced as source by:** "
                    + sourceRefs.stream().map(s -> "`" + s + "`")
                            .reduce((a, b) -> a + ", " + b).orElse(""));
        }

        // External validation note for this field
        if (extCoverage != null && !extCoverage.isEmpty()) {
            md.line("**External validation:** " + String.join(", ", extCoverage));
        }

        md.blank();
        md.rule();
    }

    // -----------------------------------------------------------------------------------------
    // Bucketing — splits the rule list into per-field buckets and a cross-field section
    // -----------------------------------------------------------------------------------------

    /** All metadata extracted from one cross-field rule, normalised for rendering. */
    private static final class CrossFieldEntry {
        final String ruleId;
        final String summary;
        final Severity severity;
        final String triggerText;
        final String requirementText;
        final List<String> sourceFields;
        final List<String> targetFields;

        CrossFieldEntry(String ruleId, String summary, Severity severity,
                        String triggerText, String requirementText,
                        List<String> sourceFields, List<String> targetFields) {
            this.ruleId = ruleId;
            this.summary = summary;
            this.severity = severity;
            this.triggerText = triggerText;
            this.requirementText = requirementText;
            this.sourceFields = sourceFields;
            this.targetFields = targetFields;
        }
    }

    /** Result of one rule-list traversal: per-field buckets + global cross-field list. */
    private static final class BucketedRules {
        final Map<String, List<PresenceRule>> presenceByField = new LinkedHashMap<>();
        final Map<String, List<ConditionalPresenceRule>> conditionalPresenceByField = new LinkedHashMap<>();
        final Map<String, FormatRule> formatByField = new LinkedHashMap<>();
        final List<IsinRule> isinRules = new ArrayList<>();
        final List<LeiRule> leiRules = new ArrayList<>();
        final List<CrossFieldEntry> crossField = new ArrayList<>();
        VersionRuleInfo versionRule;
        int presenceCount;
        int conditionalPresenceCount;
        int formatCount;
    }

    private static final class VersionRuleInfo {
        final String id;
        final String expectedToken;

        VersionRuleInfo(String id, String expectedToken) {
            this.id = id;
            this.expectedToken = expectedToken;
        }

        String id() { return id; }
    }

    private BucketedRules bucketRules(List<Rule> rules) {
        BucketedRules out = new BucketedRules();
        for (Rule r : rules) {
            if (r instanceof PresenceRule p) {
                out.presenceByField.computeIfAbsent(p.spec().numKey(), k -> new ArrayList<>()).add(p);
                out.presenceCount++;
            } else if (r instanceof ConditionalPresenceRule cp) {
                out.conditionalPresenceByField.computeIfAbsent(cp.spec().numKey(), k -> new ArrayList<>()).add(cp);
                out.conditionalPresenceCount++;
            } else if (r instanceof FormatRule fr) {
                out.formatByField.put(fr.spec().numKey(), fr);
                out.formatCount++;
            } else if (r instanceof IsinRule i) {
                out.isinRules.add(i);
            } else if (r instanceof LeiRule l) {
                out.leiRules.add(l);
            } else if (r instanceof TptVersionRule v) {
                out.versionRule = new VersionRuleInfo(v.id(), v.expectedVersionToken());
            } else if (r instanceof EetVersionRule v) {
                out.versionRule = new VersionRuleInfo(v.id(), v.expectedVersionToken());
            } else if (r instanceof EmtVersionRule v) {
                out.versionRule = new VersionRuleInfo(v.id(), v.expectedVersionToken());
            } else if (r instanceof EptVersionRule v) {
                out.versionRule = new VersionRuleInfo(v.id(), v.expectedVersionToken());
            } else if (r instanceof ConditionalFieldPresenceRule c) {
                ConditionalRequirement req = c.requirement();
                out.crossField.add(new CrossFieldEntry(
                        req.ruleId(),
                        "Conditional presence of field " + req.targetFieldNum(),
                        req.severityWhenMissing(),
                        "Field `" + req.sourceFieldNum() + "` " + req.condition().describe(),
                        "Field `" + req.targetFieldNum() + "` must be non-empty.",
                        List.of(req.sourceFieldNum()),
                        List.of(req.targetFieldNum())));
            } else if (r instanceof ConditionalFieldAbsenceRule c) {
                ConditionalRequirement req = c.requirement();
                out.crossField.add(new CrossFieldEntry(
                        req.ruleId(),
                        "Conditional absence of field " + req.targetFieldNum(),
                        req.severityWhenMissing(),
                        "Field `" + req.sourceFieldNum() + "` " + req.condition().describe(),
                        "Field `" + req.targetFieldNum() + "` must be empty.",
                        List.of(req.sourceFieldNum()),
                        List.of(req.targetFieldNum())));
            } else if (r instanceof ConditionalAnyFieldPresenceRule c) {
                ConditionalAnyOfRequirement req = c.requirement();
                out.crossField.add(new CrossFieldEntry(
                        req.ruleId(),
                        "At least one of fields " + req.targetFieldNums() + " must be present",
                        req.severityWhenAllMissing(),
                        "Field `" + req.sourceFieldNum() + "` " + req.condition().describe(),
                        "At least one of fields `" + String.join("`, `", req.targetFieldNums())
                                + "` must be non-empty.",
                        List.of(req.sourceFieldNum()),
                        new ArrayList<>(req.targetFieldNums())));
            } else if (r instanceof ConditionalAnySourceFieldPresenceRule c) {
                ConditionalAnySourceRequirement req = c.requirement();
                out.crossField.add(new CrossFieldEntry(
                        req.ruleId(),
                        "Conditional presence of field " + req.targetFieldNum()
                                + " (any-source trigger)",
                        req.severityWhenMissing(),
                        "Any of fields `" + String.join("`, `", req.sourceFieldNums()) + "` "
                                + req.condition().describe(),
                        "Field `" + req.targetFieldNum() + "` must be non-empty.",
                        new ArrayList<>(req.sourceFieldNums()),
                        List.of(req.targetFieldNum())));
            } else if (r instanceof CashPercentageRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "Σ MarketValuePC of CIC xx7x positions / TotalNetAssets",
                        "Field 9 must match within ±0.05.");
            } else if (r instanceof CompleteScrDeliveryRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "Field 11 (CompleteSCRDelivery) = Y",
                        "All SCR contribution fields 97..105b must be present.");
            } else if (r instanceof CouponFrequencyRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "Field 38 (Coupon payment frequency) is populated",
                        "Value must be one of {0, 1, 2, 4, 12, 52}.");
            } else if (r instanceof CustodianPairRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "Field 140 or field 141 is populated",
                        "Both fields 140 and 141 must be populated as a pair.");
            } else if (r instanceof DateOrderRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "Fields 6 and 7 are both populated",
                        "Field 7 (Reporting date) must not precede field 6 (Valuation date).");
            } else if (r instanceof InterestRateTypeRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "Field 32 is Floating/Variable, or Fixed",
                        "Floating/Variable → fields 34..37 mandatory; Fixed → field 33 mandatory.");
            } else if (r instanceof MaturityAfterReportingRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "Row CIC matches the field-39 applicability scope (CIC 1, 2, 5, 6,"
                                + " 7 sub-codes 3/4/5, 8, A, B, C, D, E, F) and field 39 is populated",
                        "Field 39 (Maturity date) must not precede field 7 (Reporting date).");
            } else if (r instanceof NavConsistencyRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "Fields 5, 8, 8b are all populated",
                        "Field 5 must equal field 8 × field 8b within ±0.01 relative tolerance.");
            } else if (r instanceof PikRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "Field 146 (PIK) is populated",
                        "Value must be one of {0,1,2,3,4} and case-specific fields must be present.");
            } else if (r instanceof PositionWeightSumRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "At least one position has a populated field 26",
                        "Σ field 26 (Position weight) must be ≈ 1.0 within ±0.02.");
            } else if (r instanceof UnderlyingCicRule c) {
                addRuleDoc(out, r.id(), c.describe(),
                        "Row CIC matches the field-67 applicability scope (CIC 22, A, B, C, D4, D5, F)",
                        "Field 67 (Underlying CIC) must be populated.");
            }
        }
        return out;
    }

    private static void addRuleDoc(BucketedRules out, String ruleId, RuleDoc doc,
                                   String triggerText, String requirementText) {
        out.crossField.add(new CrossFieldEntry(
                ruleId, doc.summary(), doc.severity(),
                triggerText, requirementText,
                doc.sourceFieldNums(), doc.targetFieldNums()));
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /** Map from field numKey → set of human-readable external-check labels covering that field. */
    private Map<String, Set<String>> externalCoverageByField(ExternalValidationConfig ext) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        if (ext == null || ext.isEmpty()) return out;
        for (IdentifierRef r : ext.isinFields()) {
            out.computeIfAbsent(r.codeKey(), k -> new TreeSet<>()).add(
                    "OpenFIGI ISIN lookup" + (r.hasTypeFlag()
                            ? " (active when field `" + r.typeKey() + "` = `" + r.expectedTypeFlag() + "`)"
                            : ""));
        }
        for (IdentifierRef r : ext.leiFields()) {
            out.computeIfAbsent(r.codeKey(), k -> new TreeSet<>()).add(
                    "GLEIF LEI lookup" + (r.hasTypeFlag()
                            ? " (active when field `" + r.typeKey() + "` = `" + r.expectedTypeFlag() + "`)"
                            : ""));
        }
        return out;
    }

    private static String slug(TemplateDefinition def, TemplateVersion v) {
        String tpl = def.id().name().toLowerCase(Locale.ROOT);
        String ver = v.version().toLowerCase(Locale.ROOT)
                .replace('.', '-')
                .replace(' ', '-');
        return tpl + "-" + ver;
    }

    private static String describeCodification(CodificationDescriptor c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.kind());
        if (c.kind() == CodificationKind.ALPHANUMERIC || c.kind() == CodificationKind.ALPHA) {
            c.maxLength().ifPresent(n -> sb.append(" (max ").append(n).append(")"));
        }
        if (c.hasClosedList()) {
            sb.append(", closed list of ").append(c.closedList().size()).append(" entries");
        }
        return sb.toString();
    }

    private static String describeApplicability(FieldSpec spec) {
        if (spec.appliesToAllCic()) return "all rows";
        Set<String> cics = spec.applicableCic();
        if (cics.isEmpty()) return "all rows";
        StringBuilder sb = new StringBuilder("CIC categories ");
        sb.append(String.join(", ", new TreeSet<>(cics)));
        Map<String, Set<String>> sub = spec.applicableSubcategories();
        if (!sub.isEmpty()) {
            List<String> bits = new ArrayList<>();
            for (Map.Entry<String, Set<String>> e : sub.entrySet()) {
                bits.add(e.getKey() + " sub-categories " + new TreeSet<>(e.getValue()));
            }
            sb.append("; ").append(String.join("; ", bits));
        }
        return sb.toString();
    }

    private static String describeFlag(Flag f) {
        return switch (f) {
            case M -> "Mandatory — must always be present.";
            case C -> "Conditional — required when the spec's applicability/condition holds.";
            case O -> "Optional — populate when applicable.";
            case I -> "Informational — populate if available, no enforcement.";
            case NA -> "Not applicable to this profile.";
            case UNKNOWN -> "Profile column not present in this version.";
        };
    }

    private static String joinFieldRefs(List<String> nums) {
        return nums.stream().map(n -> "`" + n + "`")
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String flattenWhitespace(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim();
    }

    // -----------------------------------------------------------------------------------------
    // README index
    // -----------------------------------------------------------------------------------------

    private static String renderIndex(List<String> entries) {
        MarkdownEmitter md = new MarkdownEmitter();
        md.heading1("Validation rules reference");
        md.paragraph("One file per (template, version) bundled with the validator. Generated from"
                + " the live `TemplateRegistry` — do not edit by hand. Regenerate via"
                + " `mvn -pl core -Pdocs exec:java -Dexec.args=\"docs/rules\"`.");
        md.heading2("Documents");
        for (String e : entries) md.line(e);
        md.blank();
        md.heading2("How to read these documents");
        md.paragraph("Each per-template document follows the same five-part structure: scoring,"
                + " profiles, general rules, cross-field rules, and the per-field catalog."
                + " The general-rules section lists the engines that run on every applicable"
                + " field; the per-field catalog enumerates, for each spec row, which checks"
                + " can fire on it and what each costs you in the score.");
        return md.build();
    }

}
