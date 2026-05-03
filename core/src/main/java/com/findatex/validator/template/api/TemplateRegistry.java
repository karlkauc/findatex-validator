package com.findatex.validator.template.api;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.util.IOUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Process-wide directory of installed templates. Phase 0 leaves it empty; concrete templates
 * register themselves in later sub-steps. Thread-safe for the typical write-once-then-read pattern.
 */
public final class TemplateRegistry {

    private static final List<TemplateDefinition> TEMPLATES = new ArrayList<>();
    private static volatile boolean initialized = false;

    private TemplateRegistry() {
    }

    /**
     * Idempotently registers all built-in templates. Safe to call multiple times. Holds a
     * downward dependency on the per-template packages on purpose: this is the single bootstrap
     * point and keeps the rest of the api package free of concrete-template imports.
     *
     * <p>Also raises POI's defensive limits to bounds appropriate for our largest legitimate
     * inputs (spec XLSXs ~5 MB packed / ~30 MB unpacked, fund-instance uploads up to the
     * 25 MB body cap). The defaults are sometimes too tight (legitimate sample files trip
     * the inflate-ratio check) and POI's ZipSecureFile state is global, so it has to be
     * configured exactly once at bootstrap. The values below remain well below "OOM the
     * JVM" while letting all known good inputs through.
     */
    public static synchronized void init() {
        if (initialized) return;
        configurePoiLimits();
        register(new com.findatex.validator.template.tpt.TptTemplate());
        register(new com.findatex.validator.template.eet.EetTemplate());
        register(new com.findatex.validator.template.emt.EmtTemplate());
        register(new com.findatex.validator.template.ept.EptTemplate());
        initialized = true;
    }

    private static void configurePoiLimits() {
        // Reject zip bombs: any single OPC entry whose decompressed size is >200×
        // its compressed size is treated as malicious. Real OOXML rarely exceeds 50–100×.
        ZipSecureFile.setMinInflateRatio(0.005d);
        // Cap any single zip entry at 200 MB decompressed.
        ZipSecureFile.setMaxEntrySize(200L * 1024L * 1024L);
        // Cap entry count per workbook at 10K (largest legitimate spec is <500).
        ZipSecureFile.setMaxFileCount(10_000);
        // Cap any single byte[] POI allocates internally (SST, formula bytes, etc.) at 200 MB.
        IOUtils.setByteArrayMaxOverride(200 * 1024 * 1024);
    }

    public static synchronized void register(TemplateDefinition definition) {
        for (TemplateDefinition existing : TEMPLATES) {
            if (existing.id() == definition.id()) {
                throw new IllegalStateException("Template " + definition.id() + " already registered");
            }
        }
        TEMPLATES.add(definition);
    }

    public static synchronized List<TemplateDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(TEMPLATES));
    }

    public static synchronized TemplateDefinition of(TemplateId id) {
        for (TemplateDefinition def : TEMPLATES) {
            if (def.id() == id) return def;
        }
        throw new NoSuchElementException("No template registered for id " + id);
    }

    /** Test-only hook for resetting the registry between unit tests. */
    static synchronized void clearForTesting() {
        TEMPLATES.clear();
        initialized = false;
    }
}
