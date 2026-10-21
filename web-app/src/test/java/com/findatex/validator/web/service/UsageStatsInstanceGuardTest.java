package com.findatex.validator.web.service;

import com.findatex.validator.web.config.WebConfig;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A local or load-test instance that carries the production DB URL must not
 * write into the production statistics (2026-10: 3,263 bogus rows from one
 * load test). Only {@code FINDATEX_WEB_INSTANCE=prod} enables the inserts.
 * No CDI: config and datasource are stubbed.
 */
class UsageStatsInstanceGuardTest {

    private static WebConfig config(String instance, boolean dbConfigured) {
        return new WebConfig() {
            @Override
            public UsageStats usageStats() {
                return new UsageStats(Optional.empty(), 60, Optional.empty(),
                        dbConfigured, Optional.empty(), instance);
            }
        };
    }

    private static UsageStatsService usage(WebConfig cfg) {
        UsageStatsService s = new UsageStatsService();
        s.config = cfg;
        s.dataSource = new ResolvableDataSource();
        return s;
    }

    private static QuickFeedbackService feedback(WebConfig cfg) {
        QuickFeedbackService s = new QuickFeedbackService();
        s.config = cfg;
        s.dataSource = new ResolvableDataSource();
        return s;
    }

    @Test
    void prodInstanceWithDbWrites() {
        WebConfig cfg = config("prod", true);
        assertThat(usage(cfg).enabled()).isTrue();
        assertThat(feedback(cfg).enabled()).isTrue();
    }

    @Test
    void nonProdInstanceStaysInertEvenWithTheDbUrl() {
        for (String instance : List.of("dev", "staging", "loadtest", "")) {
            WebConfig cfg = config(instance, true);
            assertThat(usage(cfg).enabled()).as(instance).isFalse();
            assertThat(feedback(cfg).enabled()).as(instance).isFalse();
        }
    }

    @Test
    void prodWithoutDbStaysInert() {
        WebConfig cfg = config("prod", false);
        assertThat(usage(cfg).enabled()).isFalse();
        assertThat(feedback(cfg).enabled()).isFalse();
    }

    @Test
    void writesRowsNeedsBothProdAndDb() {
        assertThat(config("prod", true).usageStats().writesRows()).isTrue();
        assertThat(config("dev", true).usageStats().writesRows()).isFalse();
        assertThat(config("prod", false).usageStats().writesRows()).isFalse();
    }

    /** Minimal Instance that reports resolvable; get() is never reached by enabled(). */
    private static final class ResolvableDataSource implements Instance<AgroalDataSource> {
        @Override public Instance<AgroalDataSource> select(Annotation... q) { throw new UnsupportedOperationException(); }
        @Override public <U extends AgroalDataSource> Instance<U> select(Class<U> t, Annotation... q) { throw new UnsupportedOperationException(); }
        @Override public <U extends AgroalDataSource> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> t, Annotation... q) { throw new UnsupportedOperationException(); }
        @Override public boolean isUnsatisfied() { return false; }
        @Override public boolean isAmbiguous() { return false; }
        @Override public void destroy(AgroalDataSource i) { }
        @Override public Handle<AgroalDataSource> getHandle() { throw new UnsupportedOperationException(); }
        @Override public Iterable<? extends Handle<AgroalDataSource>> handles() { throw new UnsupportedOperationException(); }
        @Override public Iterator<AgroalDataSource> iterator() { throw new UnsupportedOperationException(); }
        @Override public AgroalDataSource get() { throw new UnsupportedOperationException(); }
    }
}
