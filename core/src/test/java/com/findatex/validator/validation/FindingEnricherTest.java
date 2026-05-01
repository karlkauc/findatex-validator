package com.findatex.validator.validation;

import com.findatex.validator.domain.TptFile;
import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.tpt.TptProfiles;
import com.findatex.validator.template.tpt.TptRuleSet;
import com.findatex.validator.spec.SpecCatalog;
import com.findatex.validator.spec.SpecLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.findatex.validator.validation.TestFileBuilder.values;
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
    void emtSpecPullsContextFromIssuerAndInstrumentColumns() {
        // EMT carries the issuer LEI in NUM 3 (not portfolio id), the issuer name in NUM 2,
        // a per-instrument reference date in NUM 8, and instrument code/name in NUM 9/11.
        TptFile file = new TestFileBuilder()
                .row(values(
                        "1", "V4.2",
                        "2", "WisdomTree Commodity Securities Limited",
                        "3", "21380068Q1JSIAN4FO63",
                        "8", "2026-04-09",
                        "9", "JE00B2NFT427",
                        "11", "WisdomTree Agriculture 2x Daily Leveraged"))
                .build();

        Finding f = Finding.error("FORMAT/55", null, "55", "55", 1, "L", "Expected numeric");
        List<Finding> enriched = FindingEnricher.enrich(file, List.of(f),
                com.findatex.validator.template.emt.EmtTemplate.FINDING_CONTEXT);

        FindingContext ctx = enriched.get(0).context();
        assertThat(ctx.portfolioId()).isEqualTo("21380068Q1JSIAN4FO63");
        assertThat(ctx.portfolioName()).isEqualTo("WisdomTree Commodity Securities Limited");
        assertThat(ctx.valuationDate()).isEqualTo("2026-04-09");
        assertThat(ctx.instrumentCode()).isEqualTo("JE00B2NFT427");
        assertThat(ctx.instrumentName()).isEqualTo("WisdomTree Agriculture 2x Daily Leveraged");
        assertThat(ctx.valuationWeight()).isNull();   // EMT has no weight equivalent
    }

    @Test
    void eetSpecPullsContextFromManufacturerAndInstrumentColumns() {
        // EET context: NUM 13 = Manufacturer_Code (portfolio id), NUM 11 = Manufacturer_Name,
        // NUM 15 = General_Reference_Date, NUM 23/25 = instrument id/name. No weight.
        TptFile file = new TestFileBuilder()
                .row(values(
                        "1", "V1.1.3",
                        "11", "Some Manufacturer",
                        "13", "529900T8BM49AURSDO55",
                        "15", "2025-12-31",
                        "23", "FR0010315770",
                        "25", "Demo PRIIP"))
                .build();

        Finding f = Finding.error("FORMAT/X", null, "23", "23", 1, "FR0010315770", "msg");
        List<Finding> enriched = FindingEnricher.enrich(file, List.of(f),
                com.findatex.validator.template.eet.EetTemplate.FINDING_CONTEXT);

        FindingContext ctx = enriched.get(0).context();
        assertThat(ctx.portfolioId()).isEqualTo("529900T8BM49AURSDO55");
        assertThat(ctx.portfolioName()).isEqualTo("Some Manufacturer");
        assertThat(ctx.valuationDate()).isEqualTo("2025-12-31");
        assertThat(ctx.instrumentCode()).isEqualTo("FR0010315770");
        assertThat(ctx.instrumentName()).isEqualTo("Demo PRIIP");
        assertThat(ctx.valuationWeight()).isNull();
    }

    @Test
    void eptSpecOmitsInstrumentSlots() {
        // EPT is per-portfolio: NUM 14 = Portfolio_Identifying_Data, NUM 16 = Portfolio_Name,
        // NUM 18 = PRIIPs_KID_Publication_Date. No instrument/weight (the row IS the product).
        TptFile file = new TestFileBuilder()
                .row(values(
                        "1", "V2.1",
                        "14", "FR0010315770",
                        "16", "Demo PRIIP",
                        "18", "2025-12-31"))
                .build();

        Finding f = Finding.error("FORMAT/X", null, "16", "16", 1, "Demo PRIIP", "msg");
        List<Finding> enriched = FindingEnricher.enrich(file, List.of(f),
                com.findatex.validator.template.ept.EptTemplate.FINDING_CONTEXT);

        FindingContext ctx = enriched.get(0).context();
        assertThat(ctx.portfolioId()).isEqualTo("FR0010315770");
        assertThat(ctx.portfolioName()).isEqualTo("Demo PRIIP");
        assertThat(ctx.valuationDate()).isEqualTo("2025-12-31");
        assertThat(ctx.instrumentCode()).isNull();
        assertThat(ctx.instrumentName()).isNull();
        assertThat(ctx.valuationWeight()).isNull();
    }

    @Test
    void emptyContextSpecLeavesFindingsUnannotated() {
        TptFile file = new TestFileBuilder()
                .row(values("1", "V4.2", "3", "21380068Q1JSIAN4FO63"))
                .build();
        Finding f = Finding.error("FORMAT/55", null, "55", "55", 1, "L", "msg");
        List<Finding> enriched = FindingEnricher.enrich(file, List.of(f),
                com.findatex.validator.template.api.FindingContextSpec.EMPTY);
        FindingContext ctx = enriched.get(0).context();
        assertThat(ctx).isNotNull();
        assertThat(ctx.portfolioId()).isNull();
        assertThat(ctx.portfolioName()).isNull();
        assertThat(ctx.valuationDate()).isNull();
        assertThat(ctx.instrumentCode()).isNull();
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

        List<Finding> findings = new ValidationEngine(CATALOG, new TptRuleSet())
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
