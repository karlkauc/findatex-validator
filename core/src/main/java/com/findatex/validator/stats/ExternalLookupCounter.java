package com.findatex.validator.stats;

import com.findatex.validator.external.ExternalValidationService;
import com.findatex.validator.validation.Finding;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ExternalValidationService.ProgressSink} decorator that counts the
 * GLEIF / OpenFIGI phase for usage statistics while forwarding every callback
 * to a UI sink. Thread-safe; a batch run feeds it from several files, the
 * totals simply accumulate.
 *
 * <p>Only counts are kept — never a key, a record or a message.
 */
public final class ExternalLookupCounter implements ExternalValidationService.ProgressSink {

    private static final String UNAVAILABLE_SUFFIX = "-UNAVAILABLE";
    private static final String CANCELLED = "EXTERNAL/CANCELLED";

    private final ExternalValidationService.ProgressSink delegate;
    private final AtomicInteger lookups = new AtomicInteger();
    private final AtomicInteger cacheHits = new AtomicInteger();
    private volatile boolean touched;

    public ExternalLookupCounter(ExternalValidationService.ProgressSink delegate) {
        this.delegate = delegate == null ? ExternalValidationService.ProgressSink.NOOP : delegate;
    }

    @Override
    public void leiTotal(int total) {
        touched = true;
        lookups.addAndGet(Math.max(total, 0));
        delegate.leiTotal(total);
    }

    @Override
    public void leiDone(int done) {
        delegate.leiDone(done);
    }

    @Override
    public void isinTotal(int total) {
        touched = true;
        lookups.addAndGet(Math.max(total, 0));
        delegate.isinTotal(total);
    }

    @Override
    public void isinDone(int done) {
        delegate.isinDone(done);
    }

    @Override
    public void cacheStats(int hits, int total) {
        touched = true;
        cacheHits.addAndGet(Math.max(hits, 0));
        delegate.cacheStats(hits, total);
    }

    /** True once the online phase reported anything (i.e. it actually ran). */
    public boolean ran() {
        return touched;
    }

    /**
     * Aggregates into {@link UsageEvent.External}. {@code elapsedMs < 0} means
     * "not measured" (null). {@code findings} may be the full finding list —
     * only the {@code EXTERNAL/…-UNAVAILABLE} / {@code EXTERNAL/CANCELLED}
     * ids are counted as errors.
     */
    public UsageEvent.External finish(long elapsedMs, List<Finding> findings) {
        int errors = 0;
        if (findings != null) {
            for (Finding f : findings) {
                String id = f.ruleId();
                if (id == null) continue;
                if (id.startsWith("EXTERNAL/") && (id.endsWith(UNAVAILABLE_SUFFIX) || id.equals(CANCELLED))) {
                    errors++;
                }
            }
        }
        if (!touched && errors == 0) return null;
        Integer duration = elapsedMs < 0 ? null
                : (int) Math.min(elapsedMs, Integer.MAX_VALUE);
        return new UsageEvent.External(lookups.get(), cacheHits.get(), duration, errors);
    }
}
