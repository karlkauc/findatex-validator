package com.tpt.validator.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tpt.validator.config.AppSettings;
import com.tpt.validator.domain.TptFile;
import com.tpt.validator.domain.TptRow;
import com.tpt.validator.external.cache.JsonCache;
import com.tpt.validator.external.gleif.GleifClient;
import com.tpt.validator.external.gleif.LeiRecord;
import com.tpt.validator.external.http.HttpExecutor;
import com.tpt.validator.external.http.RateLimiter;
import com.tpt.validator.external.openfigi.IsinRecord;
import com.tpt.validator.external.openfigi.OpenFigiClient;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.rules.IsinRule;
import com.tpt.validator.validation.rules.LeiRule;
import com.tpt.validator.validation.rules.external.IsinOnlineRule;
import com.tpt.validator.validation.rules.external.LeiOnlineRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;

public final class ExternalValidationService {

    private static final Logger log = LoggerFactory.getLogger(ExternalValidationService.class);

    /** Sink for live progress events; all methods are no-ops by default. */
    public interface ProgressSink {
        ProgressSink NOOP = new ProgressSink() {};
        default void leiTotal(int total) {}
        default void leiDone(int done) {}
        default void isinTotal(int total) {}
        default void isinDone(int done) {}
        default void cacheStats(int hits, int total) {}
    }

    /** Internal functional type that accepts cancel + progress callbacks. */
    @FunctionalInterface
    private interface RemoteLookup<V> {
        Map<String, V> apply(List<String> keys, BooleanSupplier cancelled, IntConsumer onBatchDone);
    }

    private static final List<String[]> LEI_PAIRS = List.of(
            new String[]{"47", "48"}, new String[]{"50", "51"},
            new String[]{"81", "82"}, new String[]{"84", "85"},
            new String[]{"115", "116"}, new String[]{"119", "120"},
            new String[]{"140", "141"}
    );
    private static final List<String[]> ISIN_PAIRS = List.of(
            new String[]{"14", "15"}, new String[]{"68", "69"}
    );

    private final Path cacheDir;
    private final RemoteLookup<LeiRecord> gleif;
    private final RemoteLookup<IsinRecord> openFigi;

    private ExternalValidationService(Path cacheDir,
                                      RemoteLookup<LeiRecord> gleif,
                                      RemoteLookup<IsinRecord> openFigi) {
        this.cacheDir = cacheDir;
        this.gleif = gleif;
        this.openFigi = openFigi;
    }

    public static ExternalValidationService forProduction(Path cacheDir, AppSettings.Isin isinSettings) {
        HttpExecutor gleifHttp = new HttpExecutor(new RateLimiter(8, 8));
        double figiRate = isinSettings.openFigiApiKey().isEmpty() ? 4 : 100;
        HttpExecutor figiHttp = new HttpExecutor(new RateLimiter(figiRate, figiRate));
        GleifClient g = new GleifClient(gleifHttp);
        OpenFigiClient f = new OpenFigiClient(figiHttp, isinSettings.openFigiApiKey());
        return new ExternalValidationService(cacheDir,
                (leis, c, p) -> g.lookup(leis, c, p),
                (isins, c, p) -> f.lookup(isins, c, p));
    }

    public static ExternalValidationService forTest(Path cacheDir,
                                             Function<List<String>, Map<String, LeiRecord>> gleif,
                                             Function<List<String>, Map<String, IsinRecord>> figi) {
        return new ExternalValidationService(cacheDir,
                (leis, c, p) -> gleif.apply(leis),
                (isins, c, p) -> figi.apply(isins));
    }

    /** Convenience overload — uses NOOP progress sink. */
    public List<Finding> run(TptFile file, AppSettings settings, BooleanSupplier cancelled) {
        return run(file, settings, cancelled, ProgressSink.NOOP);
    }

    public List<Finding> run(TptFile file, AppSettings settings,
                             BooleanSupplier cancelled, ProgressSink sink) {
        if (!settings.external().enabled()) return List.of();

        List<Finding> out = new ArrayList<>();
        Duration ttl = Duration.ofDays(settings.external().cache().ttlDays());

        // ---- LEI phase ----
        if (settings.external().lei().enabled()) {
            try {
                JsonCache<LeiRecord> cache = new JsonCache<>(
                        cacheDir.resolve("lei-cache.json"), ttl, new TypeReference<>() {});
                List<LeiOnlineRule.LeiHit> hits = collectLeiHits(file);
                if (!hits.isEmpty()) {
                    LinkedHashSet<String> distinct = new LinkedHashSet<>();
                    hits.forEach(h -> distinct.add(h.lei()));
                    List<String> misses = new ArrayList<>();
                    for (String k : distinct) {
                        if (cache.get(k).isEmpty()) misses.add(k);
                    }
                    int leiHits = distinct.size() - misses.size();
                    sink.cacheStats(leiHits, distinct.size());
                    sink.leiTotal(misses.size());

                    Map<String, LeiRecord> records = lookupWithCache(hits, LeiOnlineRule.LeiHit::lei,
                            gleif, cache, cancelled, done -> sink.leiDone(done));
                    cache.flush();
                    for (String[] pair : LEI_PAIRS) {
                        List<LeiOnlineRule.LeiHit> sub = hits.stream()
                                .filter(h -> h.codeNumKey().equals(pair[0])).toList();
                        if (sub.isEmpty()) continue;
                        out.addAll(LeiOnlineRule.evaluate(new LeiOnlineRule.Input(
                                pair[0], pair[1], sub, records, settings.external().lei())));
                    }
                }
            } catch (Exception e) {
                log.warn("GLEIF phase failed: {}", e.getMessage());
                out.add(Finding.info("EXTERNAL/GLEIF-UNAVAILABLE", null, null,
                        "GLEIF online validation",
                        0, null, "GLEIF unreachable: " + e.getMessage()));
            }
        }
        if (cancelled.getAsBoolean()) {
            out.add(cancelledFinding());
            return out;
        }

        // ---- ISIN phase ----
        if (settings.external().isin().enabled()) {
            try {
                JsonCache<IsinRecord> cache = new JsonCache<>(
                        cacheDir.resolve("isin-cache.json"), ttl, new TypeReference<>() {});
                List<IsinOnlineRule.IsinHit> hits = collectIsinHits(file);
                if (!hits.isEmpty()) {
                    LinkedHashSet<String> distinct = new LinkedHashSet<>();
                    hits.forEach(h -> distinct.add(h.isin()));
                    List<String> misses = new ArrayList<>();
                    for (String k : distinct) {
                        if (cache.get(k).isEmpty()) misses.add(k);
                    }
                    int isinHits = distinct.size() - misses.size();
                    sink.cacheStats(isinHits, distinct.size());
                    sink.isinTotal(misses.size());

                    Map<String, IsinRecord> records = lookupWithCache(hits, IsinOnlineRule.IsinHit::isin,
                            openFigi, cache, cancelled, done -> sink.isinDone(done));
                    cache.flush();
                    for (String[] pair : ISIN_PAIRS) {
                        List<IsinOnlineRule.IsinHit> sub = hits.stream()
                                .filter(h -> h.codeNumKey().equals(pair[0])).toList();
                        if (sub.isEmpty()) continue;
                        out.addAll(IsinOnlineRule.evaluate(new IsinOnlineRule.Input(
                                pair[0], pair[1], sub, records, settings.external().isin())));
                    }
                }
            } catch (Exception e) {
                log.warn("OpenFIGI phase failed: {}", e.getMessage());
                out.add(Finding.info("EXTERNAL/OPENFIGI-UNAVAILABLE", null, null,
                        "OpenFIGI online validation",
                        0, null, "OpenFIGI unreachable: " + e.getMessage()));
            }
        }
        if (cancelled.getAsBoolean()) out.add(cancelledFinding());
        return out;
    }

    private static Finding cancelledFinding() {
        return Finding.info("EXTERNAL/CANCELLED", null, null,
                "External validation", 0, null, "User cancelled the online phase");
    }

    private static List<LeiOnlineRule.LeiHit> collectLeiHits(TptFile file) {
        List<LeiOnlineRule.LeiHit> out = new ArrayList<>();
        for (TptRow row : file.rows()) {
            String issuerName = row.stringValue("46").orElse("");
            String issuerCountry = row.stringValue("52").orElse("");
            for (String[] pair : LEI_PAIRS) {
                String type = row.stringValue(pair[1]).orElse("");
                if (!"1".equals(type.trim())) continue;
                String code = row.stringValue(pair[0]).orElse("").trim().toUpperCase(Locale.ROOT);
                if (code.isEmpty() || !LeiRule.isValidLei(code)) continue;
                out.add(new LeiOnlineRule.LeiHit(pair[0], pair[1], row.rowIndex(),
                        code, issuerName, issuerCountry));
            }
        }
        return out;
    }

    private static List<IsinOnlineRule.IsinHit> collectIsinHits(TptFile file) {
        List<IsinOnlineRule.IsinHit> out = new ArrayList<>();
        for (TptRow row : file.rows()) {
            String currency = row.stringValue("21").orElse("");
            String cic = row.stringValue("11").orElse("");
            for (String[] pair : ISIN_PAIRS) {
                String type = row.stringValue(pair[1]).orElse("");
                if (!"1".equals(type.trim())) continue;
                String code = row.stringValue(pair[0]).orElse("").trim().toUpperCase(Locale.ROOT);
                if (code.isEmpty() || !IsinRule.isValidIsin(code)) continue;
                out.add(new IsinOnlineRule.IsinHit(pair[0], pair[1], row.rowIndex(),
                        code, currency, cic));
            }
        }
        return out;
    }

    private static <H, V> Map<String, V> lookupWithCache(List<H> hits,
                                                         Function<H, String> keyFn,
                                                         RemoteLookup<V> remote,
                                                         JsonCache<V> cache,
                                                         BooleanSupplier cancelled,
                                                         IntConsumer onBatchDone) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        hits.forEach(h -> distinct.add(keyFn.apply(h)));
        Map<String, V> out = new HashMap<>();
        List<String> misses = new ArrayList<>();
        for (String k : distinct) {
            cache.get(k).ifPresentOrElse(v -> out.put(k, v), () -> misses.add(k));
        }
        if (!misses.isEmpty()) {
            Map<String, V> fetched = remote.apply(misses, cancelled, onBatchDone);
            fetched.forEach((k, v) -> { out.put(k, v); cache.put(k, v); });
        }
        return out;
    }
}
