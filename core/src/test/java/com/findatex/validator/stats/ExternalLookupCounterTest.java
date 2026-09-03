package com.findatex.validator.stats;

import com.findatex.validator.external.ExternalValidationService;
import com.findatex.validator.validation.Finding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalLookupCounterTest {

    @Test
    void sumsLookupsAndCacheHitsAcrossPhasesAndForwardsToDelegate() {
        List<String> seen = new ArrayList<>();
        ExternalValidationService.ProgressSink ui = new ExternalValidationService.ProgressSink() {
            @Override public void leiTotal(int total) { seen.add("lei" + total); }
            @Override public void isinDone(int done) { seen.add("done" + done); }
        };
        ExternalLookupCounter c = new ExternalLookupCounter(ui);
        assertThat(c.ran()).isFalse();
        c.cacheStats(2, 5);
        c.leiTotal(3);
        c.cacheStats(4, 4);
        c.isinTotal(0);
        c.isinDone(1);

        UsageEvent.External ext = c.finish(250, List.of());
        assertThat(ext.lookups()).isEqualTo(3);
        assertThat(ext.cacheHits()).isEqualTo(6);
        assertThat(ext.durationMs()).isEqualTo(250);
        assertThat(ext.errors()).isZero();
        assertThat(seen).containsExactly("lei3", "done1");
        assertThat(c.ran()).isTrue();
    }

    @Test
    void countsUnavailableAndCancelledFindingsAsErrors() {
        ExternalLookupCounter c = new ExternalLookupCounter(null);
        List<Finding> findings = List.of(
                Finding.info("EXTERNAL/GLEIF-UNAVAILABLE", null, null, "x", 0, null, "m"),
                Finding.info("EXTERNAL/CANCELLED", null, null, "x", 0, null, "m"),
                Finding.info("EXTERNAL/LEI-NOT-FOUND", null, null, "x", 0, null, "m"),
                Finding.error("PRESENCE/5/X", null, "5", "x", 1, null, "m"));
        UsageEvent.External ext = c.finish(-1, findings);
        assertThat(ext.errors()).isEqualTo(2);
        assertThat(ext.lookups()).isZero();
        assertThat(ext.durationMs()).isNull();
    }

    @Test
    void nothingRanMeansNoExternalBlock() {
        assertThat(new ExternalLookupCounter(null).finish(10, List.of())).isNull();
    }
}
