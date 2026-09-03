package com.findatex.validator.report;

import com.findatex.validator.domain.RawCell;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptRuleSet;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.Severity;
import com.findatex.validator.validation.ValidationEngine;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotatedSourceModelTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static final Set<ProfileKey> PROFILES = new HashSet<>(List.of(
            TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB, TptProfiles.NW_675, TptProfiles.SST));

    @Test
    void mirrorsEveryRowAndExposesHeaderTexts() throws Exception {
        QualityReport report = buildReportFor("/sample/bad_formats.xlsx");

        AnnotatedSourceModel model = AnnotatedSourceModel.build(report);

        assertThat(model.rows()).isNotEmpty();
        assertThat(model.headerRowIndex()).isEqualTo(0);
        assertThat(model.rows().get(0).header()).isTrue();
        assertThat(model.width()).isGreaterThanOrEqualTo(report.file().rawHeaders().size());
        assertThat(model.headers().subList(0, report.file().rawHeaders().size()))
                .containsExactlyElementsOf(report.file().rawHeaders());
        // Every parsed data row maps to exactly one mirror row carrying its logical index.
        List<Integer> logical = model.rows().stream()
                .map(AnnotatedSourceModel.Row::logicalRow)
                .filter(i -> i != null)
                .toList();
        assertThat(logical).containsExactlyElementsOf(
                report.file().rows().stream().map(TptRow::rowIndex).toList());
    }

    @Test
    void cellLevelFindingLandsOnItsSourceCellShiftedByHelperColumn() throws Exception {
        QualityReport report = buildReportFor("/sample/bad_formats.xlsx");
        Finding target = report.findings().stream()
                .filter(f -> f.severity() == Severity.ERROR)
                .filter(f -> f.fieldNum() != null && f.rowIndex() != null)
                .findFirst().orElseThrow();
        TptRow tptRow = report.file().rows().stream()
                .filter(r -> r.rowIndex() == target.rowIndex()).findFirst().orElseThrow();
        RawCell rc = tptRow.all().get(target.fieldNum());

        AnnotatedSourceModel model = AnnotatedSourceModel.build(report);

        AnnotatedSourceModel.CellRef ref = model.locate(target).orElseThrow();
        assertThat(ref.mirrorRow()).isEqualTo(rc.sourceRow() - 1);
        assertThat(ref.mirrorCol()).isEqualTo(rc.sourceCol());   // sourceCol is 1-based, i.e. already shifted

        AnnotatedSourceModel.Row row = model.rows().get(ref.mirrorRow());
        assertThat(row.logicalRow()).isEqualTo(target.rowIndex());
        assertThat(row.rowSeverity()).isEqualTo(Severity.ERROR);
        AnnotatedSourceModel.Cell cell = row.cells().get(rc.sourceCol() - 1);
        assertThat(cell.severity()).isEqualTo(Severity.ERROR);
        assertThat(cell.findings()).contains(target);
        assertThat(cell.text()).isNotNull();
        assertThat(model.columnsWithFindings()).contains(ref.mirrorCol());
    }

    @Test
    void rowLevelFindingLandsOnHelperColumn() throws Exception {
        QualityReport base = buildReportFor("/sample/clean_v7.xlsx");
        TptRow first = base.file().rows().get(0);
        Finding rowLevel = Finding.warn("XF-TEST", null, null, null, first.rowIndex(), null, "row-level");
        QualityReport report = withFindings(base, List.of(rowLevel));

        AnnotatedSourceModel model = AnnotatedSourceModel.build(report);

        AnnotatedSourceModel.CellRef ref = model.locate(rowLevel).orElseThrow();
        assertThat(ref.mirrorCol()).isZero();
        AnnotatedSourceModel.Row row = model.rows().get(ref.mirrorRow());
        assertThat(row.rowLevelFindings()).containsExactly(rowLevel);
        assertThat(row.rowSeverity()).isEqualTo(Severity.WARNING);
        assertThat(row.cells()).allMatch(c -> c.severity() == null);
        assertThat(model.columnsWithFindings()).containsExactly(0);
    }

    @Test
    void globalFindingsAreNotLocatable() throws Exception {
        QualityReport base = buildReportFor("/sample/clean_v7.xlsx");
        Finding global = Finding.error("GLOBAL", null, null, null, null, null, "portfolio-level");
        QualityReport report = withFindings(base, List.of(global));

        AnnotatedSourceModel model = AnnotatedSourceModel.build(report);

        assertThat(model.locate(global)).isEmpty();
        assertThat(model.columnsWithFindings()).isEmpty();
        assertThat(model.rows()).allMatch(r -> r.rowSeverity() == null);
    }

    @Test
    void worstSeverityWinsPerCellAndPerRow() throws Exception {
        QualityReport base = buildReportFor("/sample/clean_v7.xlsx");
        TptRow first = base.file().rows().get(0);
        String numKey = first.all().keySet().iterator().next();
        Finding info = new Finding(Severity.INFO, "I", null, numKey, null, first.rowIndex(), null, "i");
        Finding warn = Finding.warn("W", null, numKey, null, first.rowIndex(), null, "w");
        QualityReport report = withFindings(base, List.of(info, warn));

        AnnotatedSourceModel model = AnnotatedSourceModel.build(report);

        AnnotatedSourceModel.CellRef ref = model.locate(warn).orElseThrow();
        AnnotatedSourceModel.Row row = model.rows().get(ref.mirrorRow());
        assertThat(row.cells().get(ref.mirrorCol() - 1).severity()).isEqualTo(Severity.WARNING);
        assertThat(row.rowSeverity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void describeOrdersBySeverityThenRuleAndTruncates() {
        Finding info = new Finding(Severity.INFO, "B-INFO", null, "1", null, 1, null, "info msg");
        Finding errB = Finding.error("B-ERR", null, "1", null, 1, null, "err b");
        Finding errA = Finding.error("A-ERR", null, "1", null, 1, null, "x".repeat(500));

        String text = AnnotatedSourceModel.describe(List.of(info, errB, errA));

        assertThat(text).startsWith("[ERROR] A-ERR — " + "x".repeat(400) + "…");
        assertThat(text.indexOf("[ERROR] B-ERR")).isLessThan(text.indexOf("[INFO] B-INFO"));
        assertThat(text).contains("[INFO] B-INFO — info msg");

        String longText = AnnotatedSourceModel.describe(
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(i -> Finding.error("R" + i, null, "1", null, 1, null, "y".repeat(300)))
                        .toList());
        assertThat(longText).endsWith("…(truncated)");
        assertThat(longText.length()).isLessThanOrEqualTo(1500 + "\n…(truncated)".length());
    }

    private static QualityReport withFindings(QualityReport base, List<Finding> findings) {
        return new QualityReport(base.file(), base.activeProfiles(), findings,
                Map.of(), Map.of(), Map.of(), Instant.now());
    }

    private static QualityReport buildReportFor(String resourcePath) throws Exception {
        URL url = AnnotatedSourceModelTest.class.getResource(resourcePath);
        assertThat(url).as("missing test resource %s", resourcePath).isNotNull();
        Path p = Path.of(url.toURI());
        TptFile file = new TptFileLoader(CATALOG).load(p);
        List<Finding> findings = new ValidationEngine(CATALOG, new TptRuleSet()).validate(file, PROFILES);
        return new QualityScorer(CATALOG).score(file, PROFILES, findings);
    }
}
