package com.findatex.validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppInfo {

    private static final String RESOURCE = "META-INF/findatex-validator.properties";
    private static final String APPLICATION_NAME = "FinDatEx Validator";
    private static final String GITHUB_URL = "https://github.com/karlkauc/findatex-validator";

    private static final Properties PROPS = load();

    private AppInfo() {}

    public static String applicationName() {
        return APPLICATION_NAME;
    }

    public static String version() {
        return resolved("version", "dev");
    }

    public static String buildTimestamp() {
        return resolved("buildTimestamp", "unknown");
    }

    public static String applicationWithVersion() {
        return APPLICATION_NAME + " " + version();
    }

    public static String githubUrl() {
        return GITHUB_URL;
    }

    /**
     * Ingest token for {@code POST /api/usage-stats}, baked in at build time from
     * the Maven property {@code findatex.usage.token} (set from the
     * {@code FINDATEX_USAGE_TOKEN} build env by the {@code usage-token} profile in
     * the root pom). Empty in local/dev builds — the desktop sender then stays
     * silent unless the same variable is present at runtime.
     */
    public static String usageToken() {
        return resolved("usageToken", "");
    }

    private static String resolved(String key, String fallback) {
        String value = PROPS.getProperty(key);
        if (value == null || value.isBlank() || value.startsWith("${")) {
            return fallback;
        }
        return value;
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = AppInfo.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
        }
        return p;
    }
}
