package com.tpt.validator;

/**
 * Tiny launcher to side-step JavaFX classpath restrictions when running the shaded JAR.
 * The shaded jar's main class points here; we then call into {@link App#main(String[])}.
 */
public final class AppLauncher {
    public static void main(String[] args) {
        App.main(args);
    }
}
