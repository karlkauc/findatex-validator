package com.findatex.validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppInfo {

    private static final String RESOURCE = "META-INF/findatex-validator.properties";
    private static final String APPLICATION_NAME = "FinDatEx Validator";

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
