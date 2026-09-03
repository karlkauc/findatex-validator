package com.findatex.validator.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findatex.validator.domain.TptFile;
import com.findatex.validator.domain.TptRow;
import com.findatex.validator.ingest.TptFileLoader;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptRuleSet;
import com.findatex.validator.validation.Finding;
import com.findatex.validator.validation.ValidationEngine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotatedSourceJsonTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();
    private static final Set<ProfileKey> PROFILES = new HashSet<>(List.of(
            TptProfiles.SOLVENCY_II, TptProfiles.IORP_EIOPA_ECB, TptProfiles.NW_675, TptProfiles.SST));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void outputIsGzippedJsonMirroringTheModel() throws Exception {
        QualityReport report = buildReportFor("/sample/bad_formats.xlsx");
        AnnotatedSourceModel model = AnnotatedSourceModel.build(report);

        byte[] raw = write(model, report.findings());
        assertThat(raw[0]).isEqualTo((byte) 0x1f);
        assertThat(raw[1]).isEqualTo((byte) 0x8b);

        JsonNode root = parse(raw);
        assertThat(root.get("headerRowIndex").asInt()).isEqualTo(model.headerRowIndex());

        JsonNode headers = root.get("headers");
        assertThat(headers.size()).isEqualTo(model.width());
        for (int c = 0; c < model.width(); c++) {
            assertThat(headers.get(c).asText()).isEqualTo(model.headers().get(c));
        }

        List<Integer> cols = new ArrayList<>();
        root.get("columnsWithFindings").forEach(n -> cols.add(n.asInt()));
        assertThat(cols).containsExactlyElementsOf(model.columnsWithFindings());

        JsonNode rows = root.get("rows");
        assertThat(rows.size()).isEqualTo(model.rows().size());
        for (int r = 0; r < model.rows().size(); r++) {
            AnnotatedSourceModel.Row mr = model.rows().get(r);
            JsonNode jr = rows.get(r);
            if (mr.logicalRow() == null) {
                assertThat(jr.get("r").isNull()).as("row %d has no logical index", r).isTrue();
            } else {
                assertThat(jr.get("r").asInt()).isEqualTo(mr.logicalRow());
            }
            JsonNode cells = jr.get("c");
            assertThat(cells.size()).isEqualTo(model.width());
            for (int c = 0; c < model.width(); c++) {
                assertThat(cells.get(c).asText()).isEqualTo(mr.cells().get(c).text());
            }
            // No per-cell severity in the JSON — the client derives it from findingCells.
            assertThat(jr.has("severity")).isFalse();
        }
        // The header row is one of the mirrored rows; the client hides it and uses headers[].
        assertThat(rows.get(model.headerRowIndex()).get("c").get(0).asText())
                .isEqualTo(model.headers().get(0));
    }

    @Test
    void findingCellsIndexIntoTheFindingsListAndRows() throws Exception {
        QualityReport report = buildReportFor("/sample/bad_formats.xlsx");
        AnnotatedSourceModel model = AnnotatedSourceModel.build(report);
        List<Finding> findings = report.findings();

        JsonNode root = parse(write(model, findings));
        JsonNode rows = root.get("rows");
        JsonNode triples = root.get("findingCells");
        assertThat(triples.size()).isGreaterThan(0);

        int locatable = 0;
        for (Finding f : findings) if (model.locate(f).isPresent()) locatable++;
        assertThat(triples.size()).isEqualTo(locatable);

        boolean sawCellLevel = false;
        for (JsonNode t : triples) {
            assertThat(t.size()).isEqualTo(3);
            int fi = t.get(0).asInt();
            int r = t.get(1).asInt();
            int c = t.get(2).asInt();
            assertThat(fi).isBetween(0, findings.size() - 1);
            Finding f = findings.get(fi);
            assertThat(f.rowIndex()).isNotNull();
            assertThat(rows.get(r).get("r").asInt()).isEqualTo(f.rowIndex());
            if (c > 0) {
                sawCellLevel = true;
                assertThat(f.fieldNum()).isNotNull();
                assertThat(c).isLessThanOrEqualTo(model.width());
            }
            AnnotatedSourceModel.CellRef ref = model.locate(f).orElseThrow();
            assertThat(ref.mirrorRow()).isEqualTo(r);
            assertThat(ref.mirrorCol()).isEqualTo(c);
        }
        assertThat(sawCellLevel).isTrue();
    }

    @Test
    void unlocatableFindingsAreOmittedButKeepTheirIndex() throws Exception {
        QualityReport base = buildReportFor("/sample/clean_v7.xlsx");
        TptRow first = base.file().rows().get(0);
        Finding global = Finding.error("GLOBAL", null, null, null, null, null, "portfolio-level");
        Finding rowLevel = Finding.warn("XF-TEST", null, null, null, first.rowIndex(), null, "row-level");
        Finding orphan = Finding.warn("ORPHAN", null, null, null, 999_999, null, "row never parsed");
        List<Finding> findings = List.of(global, rowLevel, orphan);
        QualityReport report = new QualityReport(base.file(), base.activeProfiles(), findings,
                Map.of(), Map.of(), Map.of(), Instant.now());
        AnnotatedSourceModel model = AnnotatedSourceModel.build(report);

        JsonNode root = parse(write(model, findings));
        JsonNode triples = root.get("findingCells");
        assertThat(triples.size()).isEqualTo(1);
        // Index 1 — the omitted global finding at index 0 must not shift it.
        assertThat(triples.get(0).get(0).asInt()).isEqualTo(1);
        assertThat(triples.get(0).get(2).asInt()).isZero();
        assertThat(root.get("rows").get(triples.get(0).get(1).asInt()).get("r").asInt())
                .isEqualTo(first.rowIndex());
        List<Integer> cols = new ArrayList<>();
        root.get("columnsWithFindings").forEach(n -> cols.add(n.asInt()));
        assertThat(cols).containsExactly(0);
    }

    @Test
    void withinLimitsRejectsOversizedGrids() {
        assertThat(AnnotatedSourceJson.withinLimits(10, 10, 20_000, 2_000_000L)).isTrue();
        assertThat(AnnotatedSourceJson.withinLimits(20_000, 100, 20_000, 2_000_000L)).isTrue();
        assertThat(AnnotatedSourceJson.withinLimits(20_001, 1, 20_000, 2_000_000L)).isFalse();
        assertThat(AnnotatedSourceJson.withinLimits(20_000, 101, 20_000, 2_000_000L)).isFalse();
        // Product must not overflow int arithmetic.
        assertThat(AnnotatedSourceJson.withinLimits(100_000, 100_000, Integer.MAX_VALUE, 2_000_000L)).isFalse();
        assertThat(AnnotatedSourceJson.withinLimits(0, 0, 20_000, 2_000_000L)).isTrue();
        assertThat(AnnotatedSourceJson.withinLimits(1, 1, 0, 2_000_000L)).isFalse();
    }

    private static byte[] write(AnnotatedSourceModel model, List<Finding> findings) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        AnnotatedSourceJson.write(model, findings, bos);
        return bos.toByteArray();
    }

    private static JsonNode parse(byte[] gz) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
            return MAPPER.readTree(in);
        }
    }

    private static QualityReport buildReportFor(String resourcePath) throws Exception {
        URL url = AnnotatedSourceJsonTest.class.getResource(resourcePath);
        assertThat(url).as("missing test resource %s", resourcePath).isNotNull();
        Path p = Path.of(url.toURI());
        TptFile file = new TptFileLoader(CATALOG).load(p);
        List<Finding> findings = new ValidationEngine(CATALOG, new TptRuleSet()).validate(file, PROFILES);
        return new QualityScorer(CATALOG).score(file, PROFILES, findings);
    }
}
