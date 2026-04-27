package com.tpt.validator.validation;

import com.tpt.validator.domain.TptFile;
import com.tpt.validator.template.api.ProfileKey;
import com.tpt.validator.template.tpt.TptProfiles;
import com.tpt.validator.spec.SpecCatalog;
import com.tpt.validator.spec.SpecLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.tpt.validator.validation.TestFileBuilder.values;
import static org.assertj.core.api.Assertions.assertThat;

class FindingEnricherTest {

    private static final SpecCatalog CATALOG = SpecLoader.loadBundled();

    @Test
    void portfolioContextPopulatedFromFirstNonEmptyRow() {
        TptFile file = new TestFileBuilder()
                .row(values(
                        "1", "FR0010000001",
                        "3", "Demo Bond Fund",
                        "6", "2025-12-31",
                        "12", "FR12",
                        "14", "FR0000571085",
                        "17", "Treasury Bond",
                        "26", "0.5"))
                .build();

        Finding raw = Finding.error("FORMAT/21", null, "21", "Currency", 1, "ZZZ", "bad");
        List<Finding> enriched = FindingEnricher.enrich(file, List.of(raw));

        FindingContext ctx = enriched.get(0).context();
        assertThat(ctx).isNotNull();
        assertThat(ctx.portfolioId()).isEqualTo("FR0010000001");
        assertThat(ctx.portfolioName()).isEqualTo("Demo Bond Fund");
        assertThat(ctx.valuationDate()).isEqualTo("2025-12-31");
        assertThat(ctx.instrumentCode()).isEqualTo("FR0000571085");
        assertThat(ctx.instrumentName()).isEqualTo("Treasury Bond");
        assertThat(ctx.valuationWeight()).isEqualTo("0.5");
    }

    @Test
    void portfolioOnlyForFindingsWithoutRow() {
        TptFile file = new TestFileBuilder()
                .row(values("1", "ABC123", "3", "Fund X", "6", "2025-06-30", "12", "FR12"))
                .build();

        Finding global = Finding.warn("XF-04/POSITION_WEIGHT_SUM", null, "26", "Σ weight",
                null, "0.7", "off");
        List<Finding> enriched = FindingEnricher.enrich(file, List.of(global));

        FindingContext ctx = enriched.get(0).context();
        assertThat(ctx.portfolioId()).isEqualTo("ABC123");
        assertThat(ctx.portfolioName()).isEqualTo("Fund X");
        assertThat(ctx.valuationDate()).isEqualTo("2025-06-30");
        assertThat(ctx.instrumentCode()).isNull();
        assertThat(ctx.instrumentName()).isNull();
        assertThat(ctx.valuationWeight()).isNull();
    }

    @Test
    void positionContextLooksUpRowByRowIndex() {
        TptFile file = new TestFileBuilder()
                .row(values("1", "FR1", "3", "F1", "6", "2025-12-31",
                            "12", "FR12", "14", "AAA", "17", "First", "26", "0.5"))
                .row(values("12", "DE31", "14", "BBB", "17", "Second", "26", "0.3"))
                .build();

        Finding f1 = Finding.error("X", null, "21", "x", 1, "v", "msg");
        Finding f2 = Finding.error("Y", null, "21", "x", 2, "v", "msg");
        List<Finding> enriched = FindingEnricher.enrich(file, List.of(f1, f2));

        assertThat(enriched.get(0).context().instrumentCode()).isEqualTo("AAA");
        assertThat(enriched.get(0).context().instrumentName()).isEqualTo("First");
        assertThat(enriched.get(1).context().instrumentCode()).isEqualTo("BBB");
        assertThat(enriched.get(1).context().instrumentName()).isEqualTo("Second");
        assertThat(enriched.get(1).context().valuationWeight()).isEqualTo("0.3");
    }

    @Test
    void unknownRowIndexFallsBackToPortfolioOnlyContext() {
        TptFile file = new TestFileBuilder()
                .row(values("1", "X", "3", "Y", "6", "2025-12-31", "12", "FR12"))
                .build();

        Finding orphan = Finding.error("X", null, "21", "x", 999, "v", "msg");
        FindingContext ctx = FindingEnricher.enrich(file, List.of(orphan)).get(0).context();
        assertThat(ctx.portfolioId()).isEqualTo("X");
        assertThat(ctx.instrumentCode()).isNull();
    }

    @Test
    void emptyFileProducesEmptyButNonNullContext() {
        TptFile file = new TestFileBuilder().build();
        Finding f = Finding.error("X", null, "1", "x", null, null, "msg");
        FindingContext ctx = FindingEnricher.enrich(file, List.of(f)).get(0).context();
        assertThat(ctx).isNotNull();
        assertThat(ctx.portfolioId()).isNull();
        assertThat(ctx.portfolioName()).isNull();
    }

    @Test
    void engineCallsEnricherAutomatically() {
        TptFile file = new TestFileBuilder()
                .row(values(
                        "1", "FR0010000001",
                        "3", "Demo Fund",
                        "6", "2025-12-31",
                        "12", "FR12",
                        "21", "ZZZ"))           // invalid currency → triggers FORMAT/21
                .build();

        List<Finding> findings = new ValidationEngine(CATALOG)
                .validate(file, Set.of(TptProfiles.SOLVENCY_II));

        assertThat(findings).isNotEmpty();
        // Every finding emerging from the engine carries portfolio context.
        for (Finding f : findings) {
            assertThat(f.context()).as("finding %s missing context", f.ruleId()).isNotNull();
            assertThat(f.context().portfolioId()).isEqualTo("FR0010000001");
            assertThat(f.context().portfolioName()).isEqualTo("Demo Fund");
            assertThat(f.context().valuationDate()).isEqualTo("2025-12-31");
        }
    }
}
