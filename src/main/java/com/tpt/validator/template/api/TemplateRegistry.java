package com.tpt.validator.template.api;

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
     */
    public static synchronized void init() {
        if (initialized) return;
        register(new com.tpt.validator.template.tpt.TptTemplate());
        initialized = true;
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
