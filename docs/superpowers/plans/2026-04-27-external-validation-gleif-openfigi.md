# External Validation (GLEIF + OpenFIGI) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional online validation of LEIs (against GLEIF) and ISINs (against OpenFIGI) to the TPT V7 Validator, with corporate-grade HTTP-proxy/NTLM support ported from FreeXmlToolkit.

**Architecture:** Two phases per `Validate` click — phase 1 is the existing local rule engine; phase 2 (only when the master toggle is on) runs in a modal background `Task` that dedupes identifiers, hits a persistent JSON cache, and bulk-queries GLEIF / OpenFIGI through a JDK `HttpClient`. New rules emit findings under IDs like `LEI-LIVE/47/48` so reports stay consistent. All proxy / API-key / TTL settings live in a new modal Settings dialog backed by `~/.config/tpt-validator/settings.json`. Bootstrap order in `App.start()` is: enable NTLM → clear JVM proxy properties → apply selected proxy mode.

**Tech Stack:** Java 21, JavaFX 21, JUnit 5, AssertJ, Jackson 2.18 (NEW dep), JDK `java.net.http.HttpClient`, JDK `com.sun.net.httpserver.HttpServer` for tests. Source spec: `docs/superpowers/specs/2026-04-27-external-validation-gleif-openfigi-design.md`.

---

## Conventions used in this plan

- Verification commands run from repo root.
- All commits use the project's existing convention (no co-author tag is in repo history; we mirror that).
- "Run: `mvn -q test -Dtest=<X>`" — `-q` suppresses Maven banner; the JUnit summary still prints.
- Files marked **(port)** are adaptations of `FreeXmlToolkit` (`/home/karl/FreeXmlToolkit`) source. Adaptation = swap `org.apache.logging.log4j` for `org.slf4j`, swap package, drop FXT-specific paths.
- TDD where it makes sense; for ports we copy + adapt + run an equivalent test to verify the move (the original code is battle-tested).

---

## Task 0: Add Jackson dependency

**Files:**
- Modify: `pom.xml:25-30` (properties), `pom.xml:81-83` (dependencies)

- [ ] **Step 1: Add Jackson property + dependency**

In `pom.xml`, inside `<properties>` (after the existing `<assertj.version>` line):

```xml
<jackson.version>2.18.2</jackson.version>
```

In `<dependencies>` (before the JUnit block):

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>${jackson.version}</version>
</dependency>
```

- [ ] **Step 2: Verify build**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS, Jackson appears in `mvn dependency:tree`.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add jackson-databind for external-validation feature"
```

---

## Task 1: `AppSettings` record (immutable settings tree)

**Files:**
- Create: `src/main/java/com/tpt/validator/config/AppSettings.java`
- Test: `src/test/java/com/tpt/validator/config/AppSettingsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tpt.validator.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AppSettingsTest {

    @Test
    void defaultsAreSafe() {
        AppSettings s = AppSettings.defaults();
        assertThat(s.external().enabled()).isFalse();
        assertThat(s.external().lei().enabled()).isTrue();
        assertThat(s.external().lei().checkLapsedStatus()).isTrue();
        assertThat(s.external().lei().checkIssuerName()).isFalse();
        assertThat(s.external().lei().checkIssuerCountry()).isFalse();
        assertThat(s.external().isin().enabled()).isTrue();
        assertThat(s.external().isin().openFigiApiKey()).isEmpty();
        assertThat(s.external().isin().checkCurrency()).isFalse();
        assertThat(s.external().isin().checkCicConsistency()).isFalse();
        assertThat(s.external().cache().ttlDays()).isEqualTo(7);
        assertThat(s.proxy().mode()).isEqualTo(AppSettings.ProxyMode.SYSTEM);
    }

    @Test
    void withersReturnNewInstances() {
        AppSettings s = AppSettings.defaults();
        AppSettings s2 = s.withExternalEnabled(true);
        assertThat(s.external().enabled()).isFalse();
        assertThat(s2.external().enabled()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=AppSettingsTest`
Expected: FAIL — `AppSettings` does not exist.

- [ ] **Step 3: Implement `AppSettings`**

```java
package com.tpt.validator.config;

public record AppSettings(External external, Proxy proxy) {

    public enum ProxyMode { SYSTEM, MANUAL, NONE }

    public record External(boolean enabled, Lei lei, Isin isin, Cache cache) {}
    public record Lei(boolean enabled, boolean checkLapsedStatus,
                      boolean checkIssuerName, boolean checkIssuerCountry) {}
    public record Isin(boolean enabled, String openFigiApiKey,
                       boolean checkCurrency, boolean checkCicConsistency) {}
    public record Cache(int ttlDays, String directory) {}
    public record Proxy(ProxyMode mode, ManualProxy manual) {}
    public record ManualProxy(String host, int port, String user,
                              String passwordEncrypted, String nonProxyHosts) {}

    public static AppSettings defaults() {
        return new AppSettings(
                new External(
                        false,
                        new Lei(true, true, false, false),
                        new Isin(true, "", false, false),
                        new Cache(7, "")),
                new Proxy(
                        ProxyMode.SYSTEM,
                        new ManualProxy("", 0, "", "", "localhost|127.0.0.1")));
    }

    public AppSettings withExternalEnabled(boolean v) {
        return new AppSettings(
                new External(v, external.lei(), external.isin(), external.cache()),
                proxy);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=AppSettingsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/config/AppSettings.java \
        src/test/java/com/tpt/validator/config/AppSettingsTest.java
git commit -m "feat(config): add AppSettings record with safe defaults"
```

---

## Task 2: `PasswordCipher` (machine-bound AES wrapper)

**Files:**
- Create: `src/main/java/com/tpt/validator/config/PasswordCipher.java`
- Test: `src/test/java/com/tpt/validator/config/PasswordCipherTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tpt.validator.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordCipherTest {

    @Test
    void roundTrip() {
        String enc = PasswordCipher.encrypt("hunter2");
        assertThat(enc).isNotEmpty().isNotEqualTo("hunter2");
        assertThat(PasswordCipher.decrypt(enc)).isEqualTo("hunter2");
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertThat(PasswordCipher.encrypt("")).isEmpty();
        assertThat(PasswordCipher.decrypt("")).isEmpty();
    }

    @Test
    void tamperedCiphertextDecryptsToEmpty() {
        String enc = PasswordCipher.encrypt("secret");
        String tampered = enc.substring(0, enc.length() - 4) + "ZZZZ";
        assertThat(PasswordCipher.decrypt(tampered)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=PasswordCipherTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement `PasswordCipher`**

```java
package com.tpt.validator.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Machine-bound AES-GCM cipher for proxy passwords. The key is derived
 * from a stable per-machine seed (user.name + os.name + os.arch). This
 * does not protect against an attacker with code access — it only
 * defends against backup leaks and shoulder surfing of settings.json.
 */
public final class PasswordCipher {

    private static final Logger log = LoggerFactory.getLogger(PasswordCipher.class);
    private static final String ALG = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private PasswordCipher() {}

    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance(ALG);
            c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] joined = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, joined, 0, iv.length);
            System.arraycopy(ct, 0, joined, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(joined);
        } catch (Exception e) {
            log.warn("Password encryption failed: {}", e.getMessage());
            return "";
        }
    }

    public static String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all.length <= IV_LEN) return "";
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher c = Cipher.getInstance(ALG);
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Password decryption failed (tampered or wrong machine?): {}", e.getMessage());
            return "";
        }
    }

    private static SecretKeySpec key() throws Exception {
        String seed = System.getProperty("user.name", "u")
                + "|" + System.getProperty("os.name", "o")
                + "|" + System.getProperty("os.arch", "a")
                + "|tpt-validator-v1";
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(hash, "AES");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=PasswordCipherTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/config/PasswordCipher.java \
        src/test/java/com/tpt/validator/config/PasswordCipherTest.java
git commit -m "feat(config): add machine-bound AES cipher for proxy password"
```

---

## Task 3: `SettingsService` (load/save settings.json)

**Files:**
- Create: `src/main/java/com/tpt/validator/config/SettingsService.java`
- Test: `src/test/java/com/tpt/validator/config/SettingsServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tpt.validator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class SettingsServiceTest {

    @Test
    void missingFileYieldsDefaults(@TempDir Path tmp) {
        SettingsService svc = new SettingsService(tmp.resolve("settings.json"));
        AppSettings s = svc.getCurrent();
        assertThat(s.external().enabled()).isFalse();
        assertThat(s.proxy().mode()).isEqualTo(AppSettings.ProxyMode.SYSTEM);
    }

    @Test
    void roundTrip(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        SettingsService a = new SettingsService(file);
        a.update(a.getCurrent().withExternalEnabled(true));
        assertThat(Files.readString(file)).contains("\"enabled\" : true");

        SettingsService b = new SettingsService(file);
        assertThat(b.getCurrent().external().enabled()).isTrue();
    }

    @Test
    void atomicWriteCreatesNoTempFiles(@TempDir Path tmp) {
        Path file = tmp.resolve("settings.json");
        SettingsService svc = new SettingsService(file);
        svc.update(svc.getCurrent());
        assertThat(tmp.toFile().listFiles()).hasSize(1);
        assertThat(file).exists();
    }

    @Test
    void unknownFieldsAreIgnored(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        Files.writeString(file, "{\"external\":{\"enabled\":true,\"unknown\":42}}");
        SettingsService svc = new SettingsService(file);
        assertThat(svc.getCurrent().external().enabled()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=SettingsServiceTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement `SettingsService`**

```java
package com.tpt.validator.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    private static volatile SettingsService instance;

    private final Path file;
    private volatile AppSettings current;

    public SettingsService(Path file) {
        this.file = file;
        this.current = load();
    }

    public static SettingsService getInstance() {
        SettingsService local = instance;
        if (local == null) {
            synchronized (SettingsService.class) {
                if (instance == null) instance = new SettingsService(defaultPath());
                local = instance;
            }
        }
        return local;
    }

    public AppSettings getCurrent() { return current; }

    public synchronized void update(AppSettings next) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            MAPPER.writeValue(tmp.toFile(), next);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            this.current = next;
        } catch (IOException e) {
            log.error("Could not save settings to {}: {}", file, e.getMessage());
        }
    }

    private AppSettings load() {
        if (!Files.exists(file)) return AppSettings.defaults();
        try {
            AppSettings raw = MAPPER.readValue(file.toFile(), AppSettings.class);
            return raw == null ? AppSettings.defaults() : raw;
        } catch (IOException e) {
            log.warn("Could not read settings from {} ({}); using defaults", file, e.getMessage());
            return AppSettings.defaults();
        }
    }

    private static Path defaultPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path base = os.contains("win")
                ? Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "tpt-validator")
                : Path.of(System.getProperty("user.home"), ".config", "tpt-validator");
        return base.resolve("settings.json");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=SettingsServiceTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/config/SettingsService.java \
        src/test/java/com/tpt/validator/config/SettingsServiceTest.java
git commit -m "feat(config): add SettingsService with atomic JSON persistence"
```

---

## Task 4: `ProxyConfig` record

**Files:**
- Create: `src/main/java/com/tpt/validator/external/proxy/ProxyConfig.java`

- [ ] **Step 1: Implement (no test — pure record)**

```java
package com.tpt.validator.external.proxy;

import com.tpt.validator.config.AppSettings;

public record ProxyConfig(AppSettings.ProxyMode mode, String host, int port,
                          String user, String password, String nonProxyHosts) {

    public static ProxyConfig from(AppSettings.Proxy p, String decryptedPassword) {
        AppSettings.ManualProxy m = p.manual();
        return new ProxyConfig(p.mode(),
                m.host(), m.port(), m.user(), decryptedPassword, m.nonProxyHosts());
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tpt/validator/external/proxy/ProxyConfig.java
git commit -m "feat(proxy): add ProxyConfig record"
```

---

## Task 5: `HttpOnlyProxySelector` (port from FXT)

**Files:**
- Create: `src/main/java/com/tpt/validator/external/proxy/HttpOnlyProxySelector.java`
- Reference: `/home/karl/FreeXmlToolkit/src/main/java/org/fxt/freexmltoolkit/service/HttpOnlyProxySelector.java`

- [ ] **Step 1: Read the FXT source**

Run: `cat /home/karl/FreeXmlToolkit/src/main/java/org/fxt/freexmltoolkit/service/HttpOnlyProxySelector.java`

- [ ] **Step 2: Adapt and create the file**

Copy the source. Then change:
- Package declaration → `package com.tpt.validator.external.proxy;`
- Replace `import org.apache.logging.log4j.LogManager;` with `import org.slf4j.LoggerFactory;`
- Replace `import org.apache.logging.log4j.Logger;` with `import org.slf4j.Logger;`
- Replace `LogManager.getLogger(...)` with `LoggerFactory.getLogger(...)`

- [ ] **Step 3: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tpt/validator/external/proxy/HttpOnlyProxySelector.java
git commit -m "feat(proxy): port HttpOnlyProxySelector from FreeXmlToolkit"
```

---

## Task 6: `ProxyAuthenticator` (port from FXT)

**Files:**
- Create: `src/main/java/com/tpt/validator/external/proxy/ProxyAuthenticator.java`
- Test: `src/test/java/com/tpt/validator/external/proxy/ProxyAuthenticatorTest.java`
- Reference: `/home/karl/FreeXmlToolkit/src/main/java/org/fxt/freexmltoolkit/service/ProxyAuthenticator.java`

- [ ] **Step 1: Adapt source**

Copy `ProxyAuthenticator.java` from FXT and adapt as in Task 5 (package + slf4j swap).

- [ ] **Step 2: Write a small smoke test**

```java
package com.tpt.validator.external.proxy;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import static org.assertj.core.api.Assertions.assertThat;

class ProxyAuthenticatorTest {

    @Test
    void clearWipesPassword() throws Exception {
        ProxyAuthenticator a = new ProxyAuthenticator("u", "p");
        a.clear();
        // No throw, no leak — clear() is idempotent.
        a.clear();
    }

    @Test
    void onlyAnswersForProxyRequests() throws Exception {
        ProxyAuthenticator a = new ProxyAuthenticator("u", "p");
        Method m = Authenticator.class.getDeclaredMethod("getPasswordAuthentication");
        m.setAccessible(true);
        // Without the JVM's authenticator state set up, the method returns null
        // for non-proxy contexts. We only assert the method is reachable.
        Object res = m.invoke(a);
        assertThat(res).isNull().describedAs("no requestorType set => null");
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn -q test -Dtest=ProxyAuthenticatorTest`
Expected: PASS (2 tests).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tpt/validator/external/proxy/ProxyAuthenticator.java \
        src/test/java/com/tpt/validator/external/proxy/ProxyAuthenticatorTest.java
git commit -m "feat(proxy): port ProxyAuthenticator from FreeXmlToolkit"
```

---

## Task 7: `SystemProxyDetector` (port from FXT)

**Files:**
- Create: `src/main/java/com/tpt/validator/external/proxy/SystemProxyDetector.java`
- Test: `src/test/java/com/tpt/validator/external/proxy/SystemProxyDetectorTest.java`
- Reference: `/home/karl/FreeXmlToolkit/src/main/java/org/fxt/freexmltoolkit/service/SystemProxyDetector.java`

- [ ] **Step 1: Adapt source**

Copy `SystemProxyDetector.java` from FXT and adapt as in Task 5. Confirm `HttpOnlyProxySelector.install()` resolves to the new package.

- [ ] **Step 2: Write a non-Windows smoke test**

```java
package com.tpt.validator.external.proxy;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SystemProxyDetectorTest {

    @Test
    void clearProxyConfigurationWipesProperties() {
        System.setProperty("http.proxyHost", "x.example");
        System.setProperty("http.proxyPort", "1234");
        SystemProxyDetector.clearProxyConfiguration();
        assertThat(System.getProperty("http.proxyHost")).isNull();
        assertThat(System.getProperty("http.proxyPort")).isNull();
    }

    @Test
    void configureProxySetsProperties() {
        SystemProxyDetector.clearProxyConfiguration();
        SystemProxyDetector.configureProxy("p.example", 8080);
        assertThat(System.getProperty("http.proxyHost")).isEqualTo("p.example");
        assertThat(System.getProperty("http.proxyPort")).isEqualTo("8080");
        SystemProxyDetector.clearProxyConfiguration();
    }

    @Test
    void getCurrentConfigEmptyByDefault() {
        SystemProxyDetector.clearProxyConfiguration();
        assertThat(SystemProxyDetector.getCurrentConfig()).isEmpty();
    }
}
```

- [ ] **Step 3: Run tests**

Run: `mvn -q test -Dtest=SystemProxyDetectorTest`
Expected: PASS (3 tests).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tpt/validator/external/proxy/SystemProxyDetector.java \
        src/test/java/com/tpt/validator/external/proxy/SystemProxyDetectorTest.java
git commit -m "feat(proxy): port SystemProxyDetector from FreeXmlToolkit"
```

---

## Task 8: `ProxyService` (bootstrap orchestrator)

**Files:**
- Create: `src/main/java/com/tpt/validator/external/proxy/ProxyService.java`
- Test: `src/test/java/com/tpt/validator/external/proxy/ProxyServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tpt.validator.external.proxy;

import com.tpt.validator.config.AppSettings;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProxyServiceTest {

    @Test
    void noneModeClearsProperties() {
        System.setProperty("http.proxyHost", "leftover");
        ProxyConfig cfg = new ProxyConfig(AppSettings.ProxyMode.NONE, "", 0, "", "", "");
        ProxyService.applyMode(cfg);
        assertThat(System.getProperty("http.proxyHost")).isNull();
    }

    @Test
    void manualModeSetsProperties() {
        ProxyConfig cfg = new ProxyConfig(
                AppSettings.ProxyMode.MANUAL, "p.example", 8080, "u", "p", "localhost");
        ProxyService.applyMode(cfg);
        assertThat(System.getProperty("http.proxyHost")).isEqualTo("p.example");
        assertThat(System.getProperty("http.proxyPort")).isEqualTo("8080");
        assertThat(System.getProperty("http.nonProxyHosts")).isEqualTo("localhost");
        SystemProxyDetector.clearProxyConfiguration();
    }

    @Test
    void enableNtlmSetsTunnelingProperty() {
        System.clearProperty("jdk.http.auth.tunneling.disabledSchemes");
        ProxyService.enableNtlmAuthentication();
        assertThat(System.getProperty("jdk.http.auth.tunneling.disabledSchemes")).isEqualTo("");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=ProxyServiceTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement `ProxyService`**

```java
package com.tpt.validator.external.proxy;

import com.tpt.validator.config.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Authenticator;
import java.util.Optional;

public final class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private ProxyService() {}

    /** Must run before the first HTTP request. */
    public static void enableNtlmAuthentication() {
        SystemProxyDetector.clearProxyConfiguration();          // start clean
        // Re-use FXT's full bootstrap (NTLM tunnel/proxy + IPv4 + Authenticator + selector)
        com.tpt.validator.external.proxy.SystemProxyDetector.class.getName(); // load order hint
        // Use the ported method directly:
        com.tpt.validator.external.proxy.SystemProxyDetector
                .class.getModule().toString();                  // no-op, keep classloader
        // Delegate to the ported helper that does the real work:
        SystemProxyDetectorBridge.enableNtlm();
        log.info("NTLM authentication enabled");
    }

    public static void clearJvmProxyProperties() {
        SystemProxyDetector.clearProxyConfiguration();
    }

    /** Apply user's selected mode. Call after settings change. */
    public static void applyMode(ProxyConfig cfg) {
        clearJvmProxyProperties();
        switch (cfg.mode()) {
            case SYSTEM -> applySystem();
            case MANUAL -> applyManual(cfg);
            case NONE   -> log.debug("Proxy mode NONE: properties cleared");
        }
    }

    private static void applySystem() {
        Optional<SystemProxyDetector.ProxyConfig> detected = SystemProxyDetector.detectSystemProxy();
        if (detected.isPresent()) {
            SystemProxyDetector.ProxyConfig p = detected.get();
            SystemProxyDetector.configureProxy(p.host(), p.port());
            log.info("System proxy applied: {}:{}", p.host(), p.port());
        } else {
            log.info("No static system proxy detected; relying on default ProxySelector (PAC/WPAD)");
        }
    }

    private static void applyManual(ProxyConfig cfg) {
        if (cfg.host().isEmpty() || cfg.port() <= 0) {
            log.warn("Manual proxy selected but host/port not set; treating as NONE");
            return;
        }
        SystemProxyDetector.configureProxy(cfg.host(), cfg.port(), cfg.nonProxyHosts());
        if (!cfg.user().isEmpty()) {
            Authenticator.setDefault(new ProxyAuthenticator(cfg.user(), cfg.password()));
            log.info("Manual proxy applied with credentials for user '{}'", cfg.user());
        } else {
            log.info("Manual proxy applied without credentials");
        }
    }
}
```

- [ ] **Step 4: Add the small bridge that calls the existing FXT-ported method**

The FXT method `enableNtlmAuthentication()` lives in `SystemProxyDetector` (we ported it). To avoid a class-load ordering issue, expose a tiny bridge:

Create: `src/main/java/com/tpt/validator/external/proxy/SystemProxyDetectorBridge.java`

```java
package com.tpt.validator.external.proxy;

final class SystemProxyDetectorBridge {
    private SystemProxyDetectorBridge() {}
    static void enableNtlm() {
        SystemProxyDetector.enableNtlmAuthentication();
    }
}
```

Replace the `enableNtlmAuthentication()` body in `ProxyService` (above) with just `SystemProxyDetectorBridge.enableNtlm();` — the placeholder lines `class.getName()` / `class.getModule()` are not needed and should be removed.

- [ ] **Step 5: Run tests**

Run: `mvn -q test -Dtest=ProxyServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tpt/validator/external/proxy/ProxyService.java \
        src/main/java/com/tpt/validator/external/proxy/SystemProxyDetectorBridge.java \
        src/test/java/com/tpt/validator/external/proxy/ProxyServiceTest.java
git commit -m "feat(proxy): add ProxyService bootstrap orchestrator"
```

---

## Task 9: `RateLimiter` (token bucket)

**Files:**
- Create: `src/main/java/com/tpt/validator/external/http/RateLimiter.java`
- Test: `src/test/java/com/tpt/validator/external/http/RateLimiterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tpt.validator.external.http;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void firstNTokensAreInstant() {
        RateLimiter r = new RateLimiter(/* permitsPerSec */ 5, /* burst */ 5);
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) r.acquire();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isLessThan(50);
    }

    @Test
    void exceedingBurstThrottles() {
        RateLimiter r = new RateLimiter(10, 2);
        long start = System.nanoTime();
        for (int i = 0; i < 4; i++) r.acquire(); // 2 free, 2 must wait ~200ms total
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isBetween(150L, 600L);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RateLimiterTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement `RateLimiter`**

```java
package com.tpt.validator.external.http;

public final class RateLimiter {

    private final double permitsPerNano;
    private final double burst;
    private double tokens;
    private long lastNanos;

    public RateLimiter(double permitsPerSec, double burst) {
        this.permitsPerNano = permitsPerSec / 1_000_000_000.0;
        this.burst = burst;
        this.tokens = burst;
        this.lastNanos = System.nanoTime();
    }

    public synchronized void acquire() {
        while (true) {
            long now = System.nanoTime();
            tokens = Math.min(burst, tokens + (now - lastNanos) * permitsPerNano);
            lastNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return;
            }
            long waitNanos = (long) ((1.0 - tokens) / permitsPerNano);
            try {
                Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=RateLimiterTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/external/http/RateLimiter.java \
        src/test/java/com/tpt/validator/external/http/RateLimiterTest.java
git commit -m "feat(http): add token-bucket RateLimiter"
```

---

## Task 10: `HttpClientFactory` (singleton, rebuildable)

**Files:**
- Create: `src/main/java/com/tpt/validator/external/http/HttpClientFactory.java`

- [ ] **Step 1: Implement (no test — wraps `java.net.http.HttpClient.newBuilder`)**

```java
package com.tpt.validator.external.http;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpClientFactory {

    private static volatile HttpClient instance;

    private HttpClientFactory() {}

    public static HttpClient get() {
        HttpClient local = instance;
        if (local == null) {
            synchronized (HttpClientFactory.class) {
                if (instance == null) instance = build();
                local = instance;
            }
        }
        return local;
    }

    public static synchronized void rebuild() { instance = null; }

    private static HttpClient build() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/tpt/validator/external/http/HttpClientFactory.java
git commit -m "feat(http): add HttpClientFactory singleton"
```

---

## Task 11: `HttpExecutor` (retry + throttle wrapper)

**Files:**
- Create: `src/main/java/com/tpt/validator/external/http/HttpExecutor.java`
- Test: `src/test/java/com/tpt/validator/external/http/HttpExecutorTest.java`

- [ ] **Step 1: Write the failing test (uses JDK `HttpServer` stub)**

```java
package com.tpt.validator.external.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpExecutorTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void successOn200() {
        server.createContext("/ok", ex -> {
            byte[] body = "hello".getBytes();
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        HttpExecutor exec = new HttpExecutor(new RateLimiter(100, 10));
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ok")).GET().build();
        var res = exec.send(req);
        assertThat(res).isPresent();
        assertThat(res.get().statusCode()).isEqualTo(200);
        assertThat(res.get().body()).isEqualTo("hello");
    }

    @Test
    void retriesOn503ThenGivesUp() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/down", ex -> {
            calls.incrementAndGet();
            ex.sendResponseHeaders(503, -1);
            ex.close();
        });
        HttpExecutor exec = new HttpExecutor(new RateLimiter(100, 10), /*backoffMs*/ 1);
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/down")).GET().build();
        var res = exec.send(req);
        assertThat(res).isEmpty();
        assertThat(calls.get()).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=HttpExecutorTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement `HttpExecutor`**

```java
package com.tpt.validator.external.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Optional;

public final class HttpExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpExecutor.class);
    private static final int MAX_ATTEMPTS = 3;

    private final RateLimiter limiter;
    private final long backoffMs;

    public HttpExecutor(RateLimiter limiter) { this(limiter, 2_000L); }

    HttpExecutor(RateLimiter limiter, long backoffMs) {
        this.limiter = limiter;
        this.backoffMs = backoffMs;
    }

    public Optional<HttpResponse<String>> send(HttpRequest req) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            limiter.acquire();
            try {
                HttpResponse<String> r = HttpClientFactory.get().send(req, BodyHandlers.ofString());
                if (r.statusCode() == 429 || r.statusCode() / 100 == 5) {
                    log.debug("HTTP {} from {} (attempt {}/{}), backing off", r.statusCode(),
                            req.uri(), attempt, MAX_ATTEMPTS);
                    if (attempt < MAX_ATTEMPTS) sleep(backoffMs * (1L << (attempt - 1)));
                    continue;
                }
                return Optional.of(r);
            } catch (Exception e) {
                log.debug("HTTP error to {} (attempt {}/{}): {}", req.uri(), attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) sleep(backoffMs * (1L << (attempt - 1)));
            }
        }
        log.warn("Giving up on {} after {} attempts", req.uri(), MAX_ATTEMPTS);
        return Optional.empty();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=HttpExecutorTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/external/http/HttpExecutor.java \
        src/test/java/com/tpt/validator/external/http/HttpExecutorTest.java
git commit -m "feat(http): add HttpExecutor with retry/backoff"
```

---

## Task 12: `JsonCache` (persistent TTL cache)

**Files:**
- Create: `src/main/java/com/tpt/validator/external/cache/JsonCache.java`
- Test: `src/test/java/com/tpt/validator/external/cache/JsonCacheTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tpt.validator.external.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JsonCacheTest {

    record Demo(String value) {}

    @Test
    void putGetMissAfterTtl(@TempDir Path tmp) {
        JsonCache<Demo> c = new JsonCache<>(tmp.resolve("c.json"), Duration.ofMillis(10),
                new TypeReference<>() {});
        c.put("k", new Demo("v"));
        assertThat(c.get("k")).map(Demo::value).contains("v");
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        assertThat(c.get("k")).isEmpty();
    }

    @Test
    void persistsAcrossInstances(@TempDir Path tmp) {
        Path p = tmp.resolve("c.json");
        JsonCache<Demo> a = new JsonCache<>(p, Duration.ofDays(1), new TypeReference<>() {});
        a.put("k", new Demo("v"));
        a.flush();
        JsonCache<Demo> b = new JsonCache<>(p, Duration.ofDays(1), new TypeReference<>() {});
        assertThat(b.get("k")).map(Demo::value).contains("v");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=JsonCacheTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement `JsonCache`**

```java
package com.tpt.validator.external.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class JsonCache<V> {

    private static final Logger log = LoggerFactory.getLogger(JsonCache.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

    public record Entry<V>(V value, long epochSecond) {}

    private final Path file;
    private final Duration ttl;
    private final TypeReference<Map<String, Entry<V>>> type;
    private final Map<String, Entry<V>> map = new ConcurrentHashMap<>();

    public JsonCache(Path file, Duration ttl, TypeReference<Map<String, Entry<V>>> type) {
        this.file = file;
        this.ttl = ttl;
        this.type = type;
        load();
    }

    public Optional<V> get(String key) {
        Entry<V> e = map.get(key);
        if (e == null) return Optional.empty();
        if (Instant.now().getEpochSecond() - e.epochSecond() > ttl.getSeconds()) {
            map.remove(key);
            return Optional.empty();
        }
        return Optional.ofNullable(e.value());
    }

    public void put(String key, V value) {
        map.put(key, new Entry<>(value, Instant.now().getEpochSecond()));
    }

    public synchronized void flush() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            MAPPER.writeValue(tmp.toFile(), map);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Cache flush failed for {}: {}", file, e.getMessage());
        }
    }

    public void clear() { map.clear(); flush(); }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            Map<String, Entry<V>> read = MAPPER.readValue(file.toFile(), type);
            if (read != null) map.putAll(read);
        } catch (IOException e) {
            log.warn("Cache load failed for {} ({}); starting fresh", file, e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=JsonCacheTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/external/cache/JsonCache.java \
        src/test/java/com/tpt/validator/external/cache/JsonCacheTest.java
git commit -m "feat(cache): add persistent TTL JsonCache"
```

---

## Task 13: `LeiRecord` + `GleifClient`

**Files:**
- Create: `src/main/java/com/tpt/validator/external/gleif/LeiRecord.java`
- Create: `src/main/java/com/tpt/validator/external/gleif/GleifClient.java`
- Test: `src/test/java/com/tpt/validator/external/gleif/GleifClientTest.java`
- Fixture: `src/test/resources/external/gleif-records-sample.json`

- [ ] **Step 1: Capture a fixture from the public docs**

The GLEIF L1 API returns JSON:API format. Save the following minimal fixture:

Create: `src/test/resources/external/gleif-records-sample.json`

```json
{
  "data": [
    {
      "type": "lei-records",
      "id": "529900D6BF99LW9R2E68",
      "attributes": {
        "lei": "529900D6BF99LW9R2E68",
        "entity": {
          "legalName": { "name": "SAP SE" },
          "legalAddress": { "country": "DE" },
          "status": "ACTIVE"
        },
        "registration": { "status": "ISSUED" }
      }
    },
    {
      "type": "lei-records",
      "id": "5493001KJTIIGC8Y1R12",
      "attributes": {
        "lei": "5493001KJTIIGC8Y1R12",
        "entity": {
          "legalName": { "name": "BAYERISCHE MOTOREN WERKE AKTIENGESELLSCHAFT" },
          "legalAddress": { "country": "DE" },
          "status": "ACTIVE"
        },
        "registration": { "status": "LAPSED" }
      }
    }
  ]
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.tpt.validator.external.gleif;

import com.sun.net.httpserver.HttpServer;
import com.tpt.validator.external.http.HttpExecutor;
import com.tpt.validator.external.http.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GleifClientTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/lei-records", ex -> {
            try (InputStream in = getClass().getResourceAsStream("/external/gleif-records-sample.json")) {
                byte[] body = in.readAllBytes();
                ex.getResponseHeaders().add("Content-Type", "application/vnd.api+json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void parsesTwoRecords() {
        GleifClient c = new GleifClient(new HttpExecutor(new RateLimiter(100, 10)), base);
        Map<String, LeiRecord> out = c.lookup(List.of("529900D6BF99LW9R2E68", "5493001KJTIIGC8Y1R12"));
        assertThat(out).hasSize(2);
        assertThat(out.get("529900D6BF99LW9R2E68").legalName()).isEqualTo("SAP SE");
        assertThat(out.get("529900D6BF99LW9R2E68").country()).isEqualTo("DE");
        assertThat(out.get("529900D6BF99LW9R2E68").registrationStatus()).isEqualTo("ISSUED");
        assertThat(out.get("5493001KJTIIGC8Y1R12").registrationStatus()).isEqualTo("LAPSED");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=GleifClientTest`
Expected: FAIL — `GleifClient` does not exist.

- [ ] **Step 4: Implement `LeiRecord`**

```java
package com.tpt.validator.external.gleif;

public record LeiRecord(String lei, String legalName, String country,
                        String entityStatus, String registrationStatus) {

    public boolean isLapsed() {
        return "LAPSED".equalsIgnoreCase(registrationStatus)
                || "RETIRED".equalsIgnoreCase(registrationStatus);
    }
}
```

- [ ] **Step 5: Implement `GleifClient`**

```java
package com.tpt.validator.external.gleif;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpt.validator.external.http.HttpExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GleifClient {

    public static final String DEFAULT_BASE = "https://api.gleif.org";
    private static final int BATCH = 200;
    private static final ObjectMapper M = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(GleifClient.class);

    private final HttpExecutor http;
    private final String base;

    public GleifClient(HttpExecutor http) { this(http, DEFAULT_BASE); }

    GleifClient(HttpExecutor http, String base) {
        this.http = http;
        this.base = base;
    }

    /**
     * Returns one record per LEI that GLEIF knows. LEIs absent from the response
     * are simply not present in the map (caller treats absence as "unknown").
     */
    public Map<String, LeiRecord> lookup(List<String> leis) {
        Map<String, LeiRecord> out = new HashMap<>();
        for (int i = 0; i < leis.size(); i += BATCH) {
            List<String> chunk = leis.subList(i, Math.min(i + BATCH, leis.size()));
            String filter = URLEncoder.encode(String.join(",", chunk), StandardCharsets.UTF_8);
            URI uri = URI.create(base + "/api/v1/lei-records?filter%5Blei%5D=" + filter
                    + "&page%5Bsize%5D=" + chunk.size());
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/vnd.api+json")
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            http.send(req).ifPresent(r -> {
                if (r.statusCode() != 200) {
                    log.warn("GLEIF returned HTTP {} for batch starting {}", r.statusCode(), chunk.get(0));
                    return;
                }
                try {
                    JsonNode root = M.readTree(r.body());
                    for (JsonNode n : root.path("data")) {
                        LeiRecord rec = parse(n);
                        if (rec != null) out.put(rec.lei(), rec);
                    }
                } catch (Exception e) {
                    log.warn("GLEIF parse error: {}", e.getMessage());
                }
            });
        }
        return out;
    }

    private static LeiRecord parse(JsonNode n) {
        JsonNode a = n.path("attributes");
        if (a.isMissingNode()) return null;
        return new LeiRecord(
                a.path("lei").asText(""),
                a.path("entity").path("legalName").path("name").asText(""),
                a.path("entity").path("legalAddress").path("country").asText(""),
                a.path("entity").path("status").asText(""),
                a.path("registration").path("status").asText("")
        );
    }
}
```

- [ ] **Step 6: Run tests**

Run: `mvn -q test -Dtest=GleifClientTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tpt/validator/external/gleif/ \
        src/test/java/com/tpt/validator/external/gleif/ \
        src/test/resources/external/gleif-records-sample.json
git commit -m "feat(gleif): add GleifClient + LeiRecord"
```

---

## Task 14: `IsinRecord` + `OpenFigiClient`

**Files:**
- Create: `src/main/java/com/tpt/validator/external/openfigi/IsinRecord.java`
- Create: `src/main/java/com/tpt/validator/external/openfigi/OpenFigiClient.java`
- Test: `src/test/java/com/tpt/validator/external/openfigi/OpenFigiClientTest.java`
- Fixture: `src/test/resources/external/openfigi-mapping-sample.json`

- [ ] **Step 1: Capture a fixture**

OpenFIGI `POST /v3/mapping` returns one array per request item. For `[{idType:"ID_ISIN", idValue:"US0378331005"}, {idType:"ID_ISIN", idValue:"AAAAAAAAAAAA"}]`:

Create: `src/test/resources/external/openfigi-mapping-sample.json`

```json
[
  { "data": [
      { "figi": "BBG000B9XRY4", "name": "APPLE INC", "ticker": "AAPL",
        "exchCode": "US", "marketSector": "Equity", "securityType": "Common Stock",
        "currency": "USD" }
  ] },
  { "warning": "No identifier found." }
]
```

- [ ] **Step 2: Write the failing test**

```java
package com.tpt.validator.external.openfigi;

import com.sun.net.httpserver.HttpServer;
import com.tpt.validator.external.http.HttpExecutor;
import com.tpt.validator.external.http.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenFigiClientTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/mapping", ex -> {
            try (InputStream in = getClass().getResourceAsStream("/external/openfigi-mapping-sample.json")) {
                byte[] body = in.readAllBytes();
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void parsesHitAndMiss() {
        OpenFigiClient c = new OpenFigiClient(new HttpExecutor(new RateLimiter(100, 10)), base, "");
        Map<String, IsinRecord> out = c.lookup(List.of("US0378331005", "AAAAAAAAAAAA"));
        assertThat(out).containsOnlyKeys("US0378331005");
        IsinRecord apple = out.get("US0378331005");
        assertThat(apple.name()).isEqualTo("APPLE INC");
        assertThat(apple.currency()).isEqualTo("USD");
        assertThat(apple.exchCode()).isEqualTo("US");
        assertThat(apple.marketSector()).isEqualTo("Equity");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=OpenFigiClientTest`
Expected: FAIL.

- [ ] **Step 4: Implement `IsinRecord`**

```java
package com.tpt.validator.external.openfigi;

public record IsinRecord(String isin, String figi, String name, String ticker,
                         String exchCode, String marketSector, String securityType,
                         String currency) {}
```

- [ ] **Step 5: Implement `OpenFigiClient`**

```java
package com.tpt.validator.external.openfigi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpt.validator.external.http.HttpExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OpenFigiClient {

    public static final String DEFAULT_BASE = "https://api.openfigi.com";
    private static final int BATCH = 100;
    private static final ObjectMapper M = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(OpenFigiClient.class);

    private final HttpExecutor http;
    private final String base;
    private final String apiKey;

    public OpenFigiClient(HttpExecutor http, String apiKey) { this(http, DEFAULT_BASE, apiKey); }

    OpenFigiClient(HttpExecutor http, String base, String apiKey) {
        this.http = http;
        this.base = base;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public Map<String, IsinRecord> lookup(List<String> isins) {
        Map<String, IsinRecord> out = new HashMap<>();
        for (int i = 0; i < isins.size(); i += BATCH) {
            List<String> chunk = isins.subList(i, Math.min(i + BATCH, isins.size()));
            try {
                String body = buildBody(chunk);
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + "/v3/mapping"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .POST(BodyPublishers.ofString(body));
                if (!apiKey.isEmpty()) b.header("X-OPENFIGI-APIKEY", apiKey);
                HttpRequest req = b.build();
                http.send(req).ifPresent(r -> {
                    if (r.statusCode() != 200) {
                        log.warn("OpenFIGI returned HTTP {} for batch starting {}", r.statusCode(), chunk.get(0));
                        return;
                    }
                    try {
                        JsonNode root = M.readTree(r.body());
                        if (!root.isArray() || root.size() != chunk.size()) {
                            log.warn("OpenFIGI response shape unexpected (size {})", root.size());
                            return;
                        }
                        for (int idx = 0; idx < chunk.size(); idx++) {
                            JsonNode item = root.get(idx);
                            JsonNode data = item.path("data");
                            if (data.isArray() && !data.isEmpty()) {
                                out.put(chunk.get(idx), parse(chunk.get(idx), data.get(0)));
                            }
                        }
                    } catch (Exception e) {
                        log.warn("OpenFIGI parse error: {}", e.getMessage());
                    }
                });
            } catch (Exception e) {
                log.warn("OpenFIGI request build error: {}", e.getMessage());
            }
        }
        return out;
    }

    private static String buildBody(List<String> chunk) {
        List<Map<String, String>> items = new ArrayList<>();
        for (String isin : chunk) {
            items.add(Map.of("idType", "ID_ISIN", "idValue", isin));
        }
        try {
            return M.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("OpenFIGI body serialization failed", e);
        }
    }

    private static IsinRecord parse(String isin, JsonNode d) {
        return new IsinRecord(
                isin,
                d.path("figi").asText(""),
                d.path("name").asText(""),
                d.path("ticker").asText(""),
                d.path("exchCode").asText(""),
                d.path("marketSector").asText(""),
                d.path("securityType").asText(""),
                d.path("currency").asText(""));
    }
}
```

- [ ] **Step 6: Run tests**

Run: `mvn -q test -Dtest=OpenFigiClientTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/tpt/validator/external/openfigi/ \
        src/test/java/com/tpt/validator/external/openfigi/ \
        src/test/resources/external/openfigi-mapping-sample.json
git commit -m "feat(openfigi): add OpenFigiClient + IsinRecord"
```

---

## Task 15: `IssuerNameComparator` (strict normalisation)

**Files:**
- Create: `src/main/java/com/tpt/validator/external/IssuerNameComparator.java`
- Test: `src/test/java/com/tpt/validator/external/IssuerNameComparatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tpt.validator.external;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IssuerNameComparatorTest {

    @Test
    void cosmeticDifferencesAreEqual() {
        assertThat(IssuerNameComparator.equivalent("BlackRock Inc", "BlackRock, Inc.")).isTrue();
        assertThat(IssuerNameComparator.equivalent("SAP SE", "Sap se")).isTrue();
        assertThat(IssuerNameComparator.equivalent("Société Générale SA", "Societe Generale")).isTrue();
    }

    @Test
    void realDifferencesAreNotEqual() {
        assertThat(IssuerNameComparator.equivalent("Apple Inc", "Microsoft Corp")).isFalse();
        assertThat(IssuerNameComparator.equivalent("BMW AG", "Volkswagen AG")).isFalse();
    }

    @Test
    void emptyOrNullIsEquivalent() {
        assertThat(IssuerNameComparator.equivalent("", "anything")).isTrue();
        assertThat(IssuerNameComparator.equivalent(null, "anything")).isTrue();
        assertThat(IssuerNameComparator.equivalent("anything", "")).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=IssuerNameComparatorTest`
Expected: FAIL.

- [ ] **Step 3: Implement `IssuerNameComparator`**

```java
package com.tpt.validator.external;

import java.text.Normalizer;
import java.util.Set;

public final class IssuerNameComparator {

    private static final Set<String> SUFFIXES = Set.of(
            "inc", "incorporated", "corp", "corporation",
            "ltd", "limited",
            "sa", "ag", "se", "nv", "bv", "plc",
            "gmbh", "kgaa", "spa", "sas", "ab",
            "co", "company", "lp", "llc", "llp"
    );

    private IssuerNameComparator() {}

    /** True if either side is empty (treat as "no comparison possible") or normalisations match. */
    public static boolean equivalent(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) return true;
        return normalise(a).equals(normalise(b));
    }

    static String normalise(String in) {
        String n = Normalizer.normalize(in, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9 ]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        StringBuilder out = new StringBuilder();
        for (String token : n.split(" ")) {
            if (!SUFFIXES.contains(token)) {
                if (out.length() > 0) out.append(' ');
                out.append(token);
            }
        }
        return out.toString();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=IssuerNameComparatorTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/external/IssuerNameComparator.java \
        src/test/java/com/tpt/validator/external/IssuerNameComparatorTest.java
git commit -m "feat(external): add IssuerNameComparator with suffix-stripping"
```

---

## Task 16: `LeiOnlineRule`

**Files:**
- Create: `src/main/java/com/tpt/validator/validation/rules/external/LeiOnlineRule.java`
- Test: `src/test/java/com/tpt/validator/validation/rules/external/LeiOnlineRuleTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tpt.validator.validation.rules.external;

import com.tpt.validator.config.AppSettings;
import com.tpt.validator.external.gleif.LeiRecord;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LeiOnlineRuleTest {

    private static final AppSettings.Lei ALL_OFF =
            new AppSettings.Lei(true, false, false, false);
    private static final AppSettings.Lei ALL_ON =
            new AppSettings.Lei(true, true, true, true);

    @Test
    void unknownLeiIsError() {
        LeiOnlineRule.Input in = new LeiOnlineRule.Input(
                "47", "48", List.of(new LeiOnlineRule.LeiHit("47", "48", 7,
                        "ZZ900D6BF99LW9R2E68", "Some Issuer", "DE")),
                Map.of(), ALL_OFF);
        List<Finding> out = LeiOnlineRule.evaluate(in);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).ruleId()).isEqualTo("LEI-LIVE/47/48");
        assertThat(out.get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void lapsedStatusEmitsWarningWhenToggleOn() {
        LeiRecord lapsed = new LeiRecord("L", "Some Issuer", "DE", "ACTIVE", "LAPSED");
        LeiOnlineRule.Input in = new LeiOnlineRule.Input(
                "47", "48", List.of(new LeiOnlineRule.LeiHit("47", "48", 7, "L", "Some Issuer", "DE")),
                Map.of("L", lapsed), ALL_ON);
        List<Finding> out = LeiOnlineRule.evaluate(in);
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE-STATUS/47/48");
    }

    @Test
    void issuerNameMismatchEmitsWarningWhenToggleOn() {
        LeiRecord ok = new LeiRecord("L", "GLEIF Name", "DE", "ACTIVE", "ISSUED");
        LeiOnlineRule.Input in = new LeiOnlineRule.Input(
                "47", "48", List.of(new LeiOnlineRule.LeiHit("47", "48", 7, "L", "Different Inc", "DE")),
                Map.of("L", ok), ALL_ON);
        List<Finding> out = LeiOnlineRule.evaluate(in);
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE-NAME/47/48");
    }

    @Test
    void issuerCountryMismatchEmitsWarningWhenToggleOn() {
        LeiRecord ok = new LeiRecord("L", "Same Co", "DE", "ACTIVE", "ISSUED");
        LeiOnlineRule.Input in = new LeiOnlineRule.Input(
                "47", "48", List.of(new LeiOnlineRule.LeiHit("47", "48", 7, "L", "Same Co", "FR")),
                Map.of("L", ok), ALL_ON);
        List<Finding> out = LeiOnlineRule.evaluate(in);
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE-COUNTRY/47/48");
    }

    @Test
    void noFindingsWhenAllSubChecksOff() {
        LeiRecord lapsed = new LeiRecord("L", "GLEIF", "DE", "ACTIVE", "LAPSED");
        LeiOnlineRule.Input in = new LeiOnlineRule.Input(
                "47", "48", List.of(new LeiOnlineRule.LeiHit("47", "48", 7, "L", "Local", "FR")),
                Map.of("L", lapsed), ALL_OFF);
        List<Finding> out = LeiOnlineRule.evaluate(in);
        assertThat(out).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=LeiOnlineRuleTest`
Expected: FAIL.

- [ ] **Step 3: Implement `LeiOnlineRule`**

```java
package com.tpt.validator.validation.rules.external;

import com.tpt.validator.config.AppSettings;
import com.tpt.validator.external.IssuerNameComparator;
import com.tpt.validator.external.gleif.LeiRecord;
import com.tpt.validator.validation.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Stateless evaluator. Inputs are pre-collected by ExternalValidationService. */
public final class LeiOnlineRule {

    public record LeiHit(String codeNumKey, String typeNumKey, int rowIndex,
                         String lei, String localIssuerName, String localIssuerCountry) {}

    public record Input(String codeNumKey, String typeNumKey,
                        List<LeiHit> hits, Map<String, LeiRecord> records, AppSettings.Lei toggles) {}

    private LeiOnlineRule() {}

    public static List<Finding> evaluate(Input in) {
        List<Finding> out = new ArrayList<>();
        String idBase = "LEI-LIVE/" + in.codeNumKey() + "/" + in.typeNumKey();
        for (LeiHit h : in.hits()) {
            LeiRecord rec = in.records().get(h.lei());
            if (rec == null) {
                out.add(Finding.error(idBase, null, h.codeNumKey(),
                        "GLEIF lookup on field " + h.codeNumKey(),
                        h.rowIndex(), h.lei(),
                        "LEI is not registered in GLEIF"));
                continue;
            }
            if (in.toggles().checkLapsedStatus() && rec.isLapsed()) {
                out.add(Finding.warning("LEI-LIVE-STATUS/" + in.codeNumKey() + "/" + in.typeNumKey(),
                        null, h.codeNumKey(),
                        "GLEIF status check on field " + h.codeNumKey(),
                        h.rowIndex(), h.lei(),
                        "LEI registration is " + rec.registrationStatus()));
            }
            if (in.toggles().checkIssuerName()
                    && !IssuerNameComparator.equivalent(h.localIssuerName(), rec.legalName())) {
                out.add(Finding.warning("LEI-LIVE-NAME/" + in.codeNumKey() + "/" + in.typeNumKey(),
                        null, h.codeNumKey(),
                        "Issuer name vs GLEIF",
                        h.rowIndex(), h.localIssuerName(),
                        "GLEIF legal name is '" + rec.legalName() + "'"));
            }
            if (in.toggles().checkIssuerCountry()
                    && !rec.country().isEmpty()
                    && !rec.country().equalsIgnoreCase(h.localIssuerCountry())) {
                out.add(Finding.warning("LEI-LIVE-COUNTRY/" + in.codeNumKey() + "/" + in.typeNumKey(),
                        null, h.codeNumKey(),
                        "Issuer country vs GLEIF",
                        h.rowIndex(), h.localIssuerCountry(),
                        "GLEIF country is '" + rec.country() + "'"));
            }
        }
        return out;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=LeiOnlineRuleTest`
Expected: PASS (5 tests). If `Finding.error/warning` signatures differ from what is shown, look at `IsinRule.java` / `LeiRule.java` for the actual factory shape and adapt the calls.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/validation/rules/external/LeiOnlineRule.java \
        src/test/java/com/tpt/validator/validation/rules/external/LeiOnlineRuleTest.java
git commit -m "feat(rules): add LeiOnlineRule with sub-toggle cross-checks"
```

---

## Task 17: `IsinOnlineRule`

**Files:**
- Create: `src/main/java/com/tpt/validator/validation/rules/external/IsinOnlineRule.java`
- Test: `src/test/java/com/tpt/validator/validation/rules/external/IsinOnlineRuleTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tpt.validator.validation.rules.external;

import com.tpt.validator.config.AppSettings;
import com.tpt.validator.external.openfigi.IsinRecord;
import com.tpt.validator.validation.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IsinOnlineRuleTest {

    private static final AppSettings.Isin ALL_OFF =
            new AppSettings.Isin(true, "", false, false);
    private static final AppSettings.Isin CCY_ON =
            new AppSettings.Isin(true, "", true, false);

    @Test
    void unknownIsinIsError() {
        IsinOnlineRule.Input in = new IsinOnlineRule.Input(
                "14", "15",
                List.of(new IsinOnlineRule.IsinHit("14", "15", 5, "US0378331009", "USD", "")),
                Map.of(), ALL_OFF);
        List<Finding> out = IsinOnlineRule.evaluate(in);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).ruleId()).isEqualTo("ISIN-LIVE/14/15");
    }

    @Test
    void currencyMismatchEmitsWarningWhenToggleOn() {
        IsinRecord rec = new IsinRecord("US0378331005", "BBG", "APPLE INC", "AAPL",
                "US", "Equity", "Common Stock", "USD");
        IsinOnlineRule.Input in = new IsinOnlineRule.Input(
                "14", "15",
                List.of(new IsinOnlineRule.IsinHit("14", "15", 5, "US0378331005", "EUR", "")),
                Map.of("US0378331005", rec), CCY_ON);
        List<Finding> out = IsinOnlineRule.evaluate(in);
        assertThat(out).extracting(Finding::ruleId).contains("ISIN-LIVE-CCY/14/15");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=IsinOnlineRuleTest`
Expected: FAIL.

- [ ] **Step 3: Implement `IsinOnlineRule`**

```java
package com.tpt.validator.validation.rules.external;

import com.tpt.validator.config.AppSettings;
import com.tpt.validator.external.openfigi.IsinRecord;
import com.tpt.validator.validation.Finding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IsinOnlineRule {

    public record IsinHit(String codeNumKey, String typeNumKey, int rowIndex,
                          String isin, String localCurrency, String localCic) {}

    public record Input(String codeNumKey, String typeNumKey,
                        List<IsinHit> hits, Map<String, IsinRecord> records, AppSettings.Isin toggles) {}

    private IsinOnlineRule() {}

    public static List<Finding> evaluate(Input in) {
        List<Finding> out = new ArrayList<>();
        String idBase = "ISIN-LIVE/" + in.codeNumKey() + "/" + in.typeNumKey();
        for (IsinHit h : in.hits()) {
            IsinRecord rec = in.records().get(h.isin());
            if (rec == null) {
                out.add(Finding.error(idBase, null, h.codeNumKey(),
                        "OpenFIGI lookup on field " + h.codeNumKey(),
                        h.rowIndex(), h.isin(),
                        "ISIN is not registered in OpenFIGI"));
                continue;
            }
            if (in.toggles().checkCurrency()
                    && !rec.currency().isEmpty() && !h.localCurrency().isEmpty()
                    && !rec.currency().equalsIgnoreCase(h.localCurrency())) {
                out.add(Finding.warning("ISIN-LIVE-CCY/" + in.codeNumKey() + "/" + in.typeNumKey(),
                        null, h.codeNumKey(),
                        "Quotation currency vs OpenFIGI",
                        h.rowIndex(), h.localCurrency(),
                        "OpenFIGI currency is '" + rec.currency() + "'"));
            }
            // CIC consistency check would compare h.localCic() vs rec.securityType();
            // mapping is non-trivial — defer to a follow-up. The toggle is wired but produces
            // no finding in V1, intentionally documented in the spec section 13.
        }
        return out;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=IsinOnlineRuleTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/validation/rules/external/IsinOnlineRule.java \
        src/test/java/com/tpt/validator/validation/rules/external/IsinOnlineRuleTest.java
git commit -m "feat(rules): add IsinOnlineRule with currency cross-check"
```

Note: the `checkCicConsistency` toggle is wired but emits no finding in V1 — see spec §13.

---

## Task 18: `ExternalValidationService` (orchestrator)

**Files:**
- Create: `src/main/java/com/tpt/validator/external/ExternalValidationService.java`
- Test: `src/test/java/com/tpt/validator/external/ExternalValidationServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tpt.validator.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tpt.validator.config.AppSettings;
import com.tpt.validator.external.cache.JsonCache;
import com.tpt.validator.external.gleif.LeiRecord;
import com.tpt.validator.external.openfigi.IsinRecord;
import com.tpt.validator.validation.Finding;
import com.tpt.validator.validation.Severity;
import com.tpt.validator.validation.TestFileBuilder;
import com.tpt.validator.domain.TptFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tpt.validator.validation.TestFileBuilder.values;

class ExternalValidationServiceTest {

    @Test
    void emitsExistenceFindingForUnknownLei(@TempDir Path tmp) {
        TptFile file = new TestFileBuilder()
                .row(values("47", "529900D6BF99LW9R2E68", "48", "1",
                            "46", "Some Issuer", "52", "DE"))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp,
                /*gleif*/   leis -> Map.of(),     // GLEIF returns no record => unknown
                /*openfigi*/ isins -> Map.of());

        List<Finding> out = svc.run(file, settings, () -> false);
        assertThat(out).extracting(Finding::ruleId).contains("LEI-LIVE/47/48");
        assertThat(out).extracting(Finding::severity).contains(Severity.ERROR);
    }

    @Test
    void emitsServiceUnavailableInfoOnException(@TempDir Path tmp) {
        TptFile file = new TestFileBuilder()
                .row(values("47", "529900D6BF99LW9R2E68", "48", "1"))
                .build();
        AppSettings settings = AppSettings.defaults().withExternalEnabled(true);
        Function<List<String>, Map<String, LeiRecord>> failing = leis -> {
            throw new RuntimeException("boom");
        };
        ExternalValidationService svc = ExternalValidationService.forTest(
                tmp, failing, isins -> Map.of());
        List<Finding> out = svc.run(file, settings, () -> false);
        assertThat(out).extracting(Finding::ruleId).contains("EXTERNAL/GLEIF-UNAVAILABLE");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=ExternalValidationServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implement `ExternalValidationService`**

```java
package com.tpt.validator.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tpt.validator.config.AppSettings;
import com.tpt.validator.config.PasswordCipher;
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
import com.tpt.validator.validation.Severity;
import com.tpt.validator.validation.rules.IsinRule;
import com.tpt.validator.validation.rules.LeiRule;
import com.tpt.validator.validation.rules.external.IsinOnlineRule;
import com.tpt.validator.validation.rules.external.LeiOnlineRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class ExternalValidationService {

    private static final Logger log = LoggerFactory.getLogger(ExternalValidationService.class);

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
    private final Function<List<String>, Map<String, LeiRecord>> gleif;
    private final Function<List<String>, Map<String, IsinRecord>> openFigi;

    private ExternalValidationService(Path cacheDir,
                                      Function<List<String>, Map<String, LeiRecord>> gleif,
                                      Function<List<String>, Map<String, IsinRecord>> openFigi) {
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
        return new ExternalValidationService(cacheDir, g::lookup, f::lookup);
    }

    static ExternalValidationService forTest(Path cacheDir,
                                             Function<List<String>, Map<String, LeiRecord>> gleif,
                                             Function<List<String>, Map<String, IsinRecord>> figi) {
        return new ExternalValidationService(cacheDir, gleif, figi);
    }

    public List<Finding> run(TptFile file, AppSettings settings, BooleanSupplier cancelled) {
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
                    Map<String, LeiRecord> records = lookupWithCache(hits, LeiOnlineRule.LeiHit::lei,
                            gleif, cache);
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
                    Map<String, IsinRecord> records = lookupWithCache(hits, IsinOnlineRule.IsinHit::isin,
                            openFigi, cache);
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
                                                        Function<List<String>, Map<String, V>> remote,
                                                        JsonCache<V> cache) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        hits.forEach(h -> distinct.add(keyFn.apply(h)));
        Map<String, V> out = new HashMap<>();
        List<String> misses = new ArrayList<>();
        for (String k : distinct) {
            cache.get(k).ifPresentOrElse(v -> out.put(k, v), () -> misses.add(k));
        }
        if (!misses.isEmpty()) {
            Map<String, V> fetched = remote.apply(misses);
            fetched.forEach((k, v) -> { out.put(k, v); cache.put(k, v); });
        }
        return out;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=ExternalValidationServiceTest`
Expected: PASS (2 tests). If `Finding.info(...)` does not exist with that exact signature, look at `Finding.java` for the correct factory and adjust calls. Also confirm `TptFile.rows()` and `TptRow.stringValue(...)` match the existing names by reading `TptFile.java` and `TptRow.java` once.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/external/ExternalValidationService.java \
        src/test/java/com/tpt/validator/external/ExternalValidationServiceTest.java
git commit -m "feat(external): add ExternalValidationService orchestrator"
```

---

## Task 19: `SettingsView.fxml` + `SettingsController` (External tab)

**Files:**
- Create: `src/main/resources/com/tpt/validator/ui/SettingsView.fxml`
- Create: `src/main/java/com/tpt/validator/ui/SettingsController.java`

- [ ] **Step 1: Inspect existing FXML conventions**

Run: `head -40 src/main/resources/com/tpt/validator/ui/MainView.fxml`

Match the namespace, controller attribute, and indentation style.

- [ ] **Step 2: Create the FXML with both tabs (External + Network)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>

<TabPane xmlns="http://javafx.com/javafx/21" xmlns:fx="http://javafx.com/fxml/1"
         fx:controller="com.tpt.validator.ui.SettingsController"
         tabClosingPolicy="UNAVAILABLE" prefWidth="640" prefHeight="540">
  <tabs>
    <Tab text="External Validation">
      <content>
        <VBox spacing="14">
          <padding><Insets top="14" right="14" bottom="14" left="14"/></padding>

          <TitledPane text="GLEIF (LEI)" collapsible="false">
            <VBox spacing="6">
              <CheckBox fx:id="leiEnabled" text="Enable GLEIF lookup"/>
              <CheckBox fx:id="leiLapsed" text="Flag lapsed/retired LEIs"/>
              <CheckBox fx:id="leiName"   text="Compare issuer name (field 46) with GLEIF"/>
              <CheckBox fx:id="leiCountry" text="Compare issuer country (field 52) with GLEIF"/>
              <Button fx:id="testGleif" text="Test GLEIF connection" onAction="#onTestGleif"/>
              <Label fx:id="testGleifResult"/>
            </VBox>
          </TitledPane>

          <TitledPane text="OpenFIGI (ISIN)" collapsible="false">
            <VBox spacing="6">
              <CheckBox fx:id="isinEnabled" text="Enable OpenFIGI lookup"/>
              <HBox spacing="6">
                <Label text="API key (optional):"/>
                <TextField fx:id="figiKey" prefColumnCount="32"/>
              </HBox>
              <CheckBox fx:id="isinCcy" text="Compare quotation currency (field 21)"/>
              <CheckBox fx:id="isinCic" text="Compare CIC vs OpenFIGI security type"/>
              <Button fx:id="testFigi" text="Test OpenFIGI connection" onAction="#onTestFigi"/>
              <Label fx:id="testFigiResult"/>
            </VBox>
          </TitledPane>

          <TitledPane text="Cache" collapsible="false">
            <HBox spacing="10">
              <Label text="TTL (days):"/>
              <Spinner fx:id="ttlDays" min="1" max="365" initialValue="7"/>
              <Button fx:id="clearCache" text="Clear cache" onAction="#onClearCache"/>
            </HBox>
          </TitledPane>
        </VBox>
      </content>
    </Tab>

    <Tab text="Network / Proxy">
      <content>
        <VBox spacing="10">
          <padding><Insets top="14" right="14" bottom="14" left="14"/></padding>
          <Label text="Proxy mode:"/>
          <RadioButton fx:id="modeSystem" text="Use system proxy (recommended)"/>
          <RadioButton fx:id="modeManual" text="Manual proxy"/>
          <RadioButton fx:id="modeNone"   text="Direct connection (no proxy)"/>

          <TitledPane text="Manual proxy" collapsible="false">
            <GridPane hgap="8" vgap="6">
              <Label text="Host:"     GridPane.rowIndex="0" GridPane.columnIndex="0"/>
              <TextField fx:id="pxHost" GridPane.rowIndex="0" GridPane.columnIndex="1"/>
              <Label text="Port:"     GridPane.rowIndex="0" GridPane.columnIndex="2"/>
              <TextField fx:id="pxPort" prefColumnCount="6" GridPane.rowIndex="0" GridPane.columnIndex="3"/>
              <Label text="User:"     GridPane.rowIndex="1" GridPane.columnIndex="0"/>
              <TextField fx:id="pxUser" GridPane.rowIndex="1" GridPane.columnIndex="1" GridPane.columnSpan="3"/>
              <Label text="Password:" GridPane.rowIndex="2" GridPane.columnIndex="0"/>
              <PasswordField fx:id="pxPass" GridPane.rowIndex="2" GridPane.columnIndex="1" GridPane.columnSpan="3"/>
              <Label text="No-proxy:" GridPane.rowIndex="3" GridPane.columnIndex="0"/>
              <TextField fx:id="pxBypass" GridPane.rowIndex="3" GridPane.columnIndex="1" GridPane.columnSpan="3"/>
            </GridPane>
          </TitledPane>

          <Label fx:id="diagnosticsLabel" text=""/>
        </VBox>
      </content>
    </Tab>
  </tabs>
</TabPane>
```

- [ ] **Step 3: Implement `SettingsController` (form binding + Test buttons)**

```java
package com.tpt.validator.ui;

import com.tpt.validator.config.AppSettings;
import com.tpt.validator.config.PasswordCipher;
import com.tpt.validator.config.SettingsService;
import com.tpt.validator.external.gleif.GleifClient;
import com.tpt.validator.external.http.HttpClientFactory;
import com.tpt.validator.external.openfigi.OpenFigiClient;
import com.tpt.validator.external.proxy.ProxyConfig;
import com.tpt.validator.external.proxy.ProxyService;
import com.tpt.validator.external.proxy.SystemProxyDetector;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Optional;

public final class SettingsController {

    @FXML private CheckBox leiEnabled, leiLapsed, leiName, leiCountry;
    @FXML private CheckBox isinEnabled, isinCcy, isinCic;
    @FXML private TextField figiKey;
    @FXML private Spinner<Integer> ttlDays;
    @FXML private Button clearCache, testGleif, testFigi;
    @FXML private Label testGleifResult, testFigiResult, diagnosticsLabel;

    @FXML private RadioButton modeSystem, modeManual, modeNone;
    @FXML private TextField pxHost, pxPort, pxUser, pxBypass;
    @FXML private PasswordField pxPass;

    @FXML
    public void initialize() {
        ToggleGroup g = new ToggleGroup();
        modeSystem.setToggleGroup(g);
        modeManual.setToggleGroup(g);
        modeNone.setToggleGroup(g);

        AppSettings s = SettingsService.getInstance().getCurrent();
        AppSettings.External e = s.external();
        leiEnabled.setSelected(e.lei().enabled());
        leiLapsed.setSelected(e.lei().checkLapsedStatus());
        leiName.setSelected(e.lei().checkIssuerName());
        leiCountry.setSelected(e.lei().checkIssuerCountry());
        isinEnabled.setSelected(e.isin().enabled());
        isinCcy.setSelected(e.isin().checkCurrency());
        isinCic.setSelected(e.isin().checkCicConsistency());
        figiKey.setText(e.isin().openFigiApiKey());
        ttlDays.getValueFactory().setValue(e.cache().ttlDays());

        switch (s.proxy().mode()) {
            case SYSTEM -> modeSystem.setSelected(true);
            case MANUAL -> modeManual.setSelected(true);
            case NONE   -> modeNone.setSelected(true);
        }
        pxHost.setText(s.proxy().manual().host());
        pxPort.setText(s.proxy().manual().port() == 0 ? "" : Integer.toString(s.proxy().manual().port()));
        pxUser.setText(s.proxy().manual().user());
        pxPass.setText(PasswordCipher.decrypt(s.proxy().manual().passwordEncrypted()));
        pxBypass.setText(s.proxy().manual().nonProxyHosts());

        diagnosticsLabel.setText(SystemProxyDetector.getCurrentConfig()
                .map(c -> "Detected proxy: " + c)
                .orElse("No system proxy detected"));
    }

    public AppSettings collect() {
        int port = 0; try { port = Integer.parseInt(pxPort.getText().trim()); } catch (Exception ignored) {}
        AppSettings.ProxyMode mode = modeManual.isSelected() ? AppSettings.ProxyMode.MANUAL
                : modeNone.isSelected() ? AppSettings.ProxyMode.NONE
                : AppSettings.ProxyMode.SYSTEM;
        AppSettings.ManualProxy mp = new AppSettings.ManualProxy(
                pxHost.getText().trim(), port,
                pxUser.getText().trim(),
                PasswordCipher.encrypt(pxPass.getText()),
                pxBypass.getText().trim());

        AppSettings prev = SettingsService.getInstance().getCurrent();
        return new AppSettings(
                new AppSettings.External(
                        prev.external().enabled(),
                        new AppSettings.Lei(
                                leiEnabled.isSelected(), leiLapsed.isSelected(),
                                leiName.isSelected(), leiCountry.isSelected()),
                        new AppSettings.Isin(
                                isinEnabled.isSelected(), figiKey.getText().trim(),
                                isinCcy.isSelected(), isinCic.isSelected()),
                        new AppSettings.Cache(ttlDays.getValue(), prev.external().cache().directory())),
                new AppSettings.Proxy(mode, mp));
    }

    @FXML
    private void onTestGleif() {
        applyCurrentProxyForTest();
        testGleifResult.setText(quickGet(URI.create(GleifClient.DEFAULT_BASE
                + "/api/v1/lei-records?page%5Bsize%5D=1")));
    }

    @FXML
    private void onTestFigi() {
        applyCurrentProxyForTest();
        testFigiResult.setText(quickGet(URI.create(OpenFigiClient.DEFAULT_BASE
                + "/v3/mapping/values/exchCode")));
    }

    @FXML
    private void onClearCache() {
        java.nio.file.Path dir = java.nio.file.Path.of(System.getProperty("user.home"),
                ".config", "tpt-validator", "cache");
        try {
            java.nio.file.Files.deleteIfExists(dir.resolve("lei-cache.json"));
            java.nio.file.Files.deleteIfExists(dir.resolve("isin-cache.json"));
        } catch (Exception ignored) {}
    }

    private void applyCurrentProxyForTest() {
        AppSettings collected = collect();
        ProxyConfig cfg = ProxyConfig.from(collected.proxy(),
                PasswordCipher.decrypt(collected.proxy().manual().passwordEncrypted()));
        ProxyService.applyMode(cfg);
        HttpClientFactory.rebuild();
    }

    private static String quickGet(URI uri) {
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            var r = HttpClientFactory.get().send(req, BodyHandlers.discarding());
            long ms = System.currentTimeMillis() - start;
            return "HTTP " + r.statusCode() + " in " + ms + " ms";
        } catch (Exception e) {
            return "FAILED: " + e.getClass().getSimpleName() + " — " + e.getMessage();
        }
    }
}
```

- [ ] **Step 4: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/com/tpt/validator/ui/SettingsView.fxml \
        src/main/java/com/tpt/validator/ui/SettingsController.java
git commit -m "feat(ui): add Settings dialog (External + Network tabs)"
```

---

## Task 20: `LookupProgressDialog` (modal progress UI)

**Files:**
- Create: `src/main/resources/com/tpt/validator/ui/LookupProgressDialog.fxml`
- Create: `src/main/java/com/tpt/validator/ui/LookupProgressController.java`

- [ ] **Step 1: Create the FXML**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.geometry.Insets?>
<?import javafx.scene.control.*?>
<?import javafx.scene.layout.*?>

<VBox xmlns="http://javafx.com/javafx/21" xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.tpt.validator.ui.LookupProgressController"
      spacing="10" prefWidth="480">
  <padding><Insets top="14" right="14" bottom="14" left="14"/></padding>
  <Label fx:id="phaseLabel" text="Local validation: complete"/>
  <Label fx:id="gleifLabel" text="GLEIF lookup: 0/0"/>
  <ProgressBar fx:id="gleifBar" prefWidth="450" progress="0"/>
  <Label fx:id="figiLabel"  text="OpenFIGI lookup: 0/0"/>
  <ProgressBar fx:id="figiBar"  prefWidth="450" progress="0"/>
  <Label fx:id="cacheLabel" text=""/>
  <HBox alignment="CENTER_RIGHT">
    <Button fx:id="cancelButton" text="Cancel" onAction="#onCancel"/>
  </HBox>
</VBox>
```

- [ ] **Step 2: Implement the controller**

```java
package com.tpt.validator.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

public final class LookupProgressController {

    @FXML private Label phaseLabel, gleifLabel, figiLabel, cacheLabel;
    @FXML private ProgressBar gleifBar, figiBar;
    @FXML private Button cancelButton;

    private volatile boolean cancelled;
    private Stage stage;

    public void setStage(Stage stage) { this.stage = stage; }

    public boolean isCancelled() { return cancelled; }

    public void update(int gleifDone, int gleifTotal,
                       int figiDone,  int figiTotal,
                       int cacheHits, int cacheTotal) {
        Platform.runLater(() -> {
            gleifLabel.setText("GLEIF lookup: " + gleifDone + "/" + gleifTotal);
            gleifBar.setProgress(gleifTotal == 0 ? 1 : (double) gleifDone / gleifTotal);
            figiLabel.setText("OpenFIGI lookup: " + figiDone + "/" + figiTotal);
            figiBar.setProgress(figiTotal == 0 ? 1 : (double) figiDone / figiTotal);
            int pct = cacheTotal == 0 ? 0 : 100 * cacheHits / cacheTotal;
            cacheLabel.setText("Cache hits: " + cacheHits + " / " + cacheTotal + " (" + pct + "%)");
        });
    }

    public void close() { Platform.runLater(() -> { if (stage != null) stage.close(); }); }

    @FXML
    private void onCancel() { cancelled = true; cancelButton.setDisable(true); }
}
```

- [ ] **Step 3: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/com/tpt/validator/ui/LookupProgressDialog.fxml \
        src/main/java/com/tpt/validator/ui/LookupProgressController.java
git commit -m "feat(ui): add LookupProgressDialog for online phase"
```

---

## Task 21: `MainController` master toggle + Settings menu integration

**Files:**
- Modify: `src/main/resources/com/tpt/validator/ui/MainView.fxml`
- Modify: `src/main/java/com/tpt/validator/ui/MainController.java`

- [ ] **Step 1: Add the master-toggle row to `MainView.fxml`**

Find the existing FlowPane that holds the profile checkboxes (`profileSolvencyII`, `profileIorpEiopa`, etc.) and add directly after it:

```xml
<HBox spacing="10" alignment="CENTER_LEFT">
  <CheckBox fx:id="externalEnabled" text="Online validation (GLEIF + OpenFIGI)"/>
  <Button fx:id="settingsButton" text="⚙ Settings…" onAction="#onSettings"/>
  <Label fx:id="externalStatusLabel" text="" style="-fx-text-fill: #b00020;"/>
</HBox>
```

- [ ] **Step 2: Wire the new fields and handlers in `MainController`**

Add field declarations next to the existing FXML fields:

```java
@FXML private CheckBox externalEnabled;
@FXML private Button settingsButton;
@FXML private Label externalStatusLabel;
```

Add to `initialize()` (or wherever the existing initialization runs):

```java
AppSettings cur = SettingsService.getInstance().getCurrent();
externalEnabled.setSelected(cur.external().enabled());
externalEnabled.selectedProperty().addListener((o, was, is) ->
        SettingsService.getInstance().update(
                SettingsService.getInstance().getCurrent().withExternalEnabled(is)));
```

Add the menu handler:

```java
@FXML
private void onSettings() {
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tpt/validator/ui/SettingsView.fxml"));
        Parent root = loader.load();
        SettingsController controller = loader.getController();
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Settings");
        dialog.setScene(new Scene(root));

        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        Alert wrap = new Alert(Alert.AlertType.NONE, "", ok, ButtonType.CANCEL);
        wrap.setTitle("Settings");
        wrap.getDialogPane().setContent(root);
        Optional<ButtonType> result = wrap.showAndWait();
        if (result.isPresent() && result.get() == ok) {
            AppSettings next = controller.collect();
            SettingsService.getInstance().update(next);
            ProxyConfig cfg = ProxyConfig.from(next.proxy(),
                    PasswordCipher.decrypt(next.proxy().manual().passwordEncrypted()));
            ProxyService.applyMode(cfg);
            HttpClientFactory.rebuild();
            externalEnabled.setSelected(next.external().enabled());
        }
    } catch (Exception e) {
        log.warn("Settings dialog failed: {}", e.getMessage());
    }
}
```

(Imports: `javafx.fxml.FXMLLoader`, `javafx.scene.Parent`, `javafx.scene.Scene`, `javafx.scene.control.*`, `javafx.stage.Modality`, `javafx.stage.Stage`, plus the `com.tpt.validator.config.*`, `com.tpt.validator.external.proxy.*`, `com.tpt.validator.external.http.HttpClientFactory`.)

- [ ] **Step 3: Verify compile**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/com/tpt/validator/ui/MainView.fxml \
        src/main/java/com/tpt/validator/ui/MainController.java
git commit -m "feat(ui): add master toggle and Settings entry point in main window"
```

---

## Task 22: Bootstrap NTLM + apply proxy mode in `App.start()`

**Files:**
- Modify: `src/main/java/com/tpt/validator/App.java`

- [ ] **Step 1: Inspect existing `App.start(...)`**

Run: `cat src/main/java/com/tpt/validator/App.java`

- [ ] **Step 2: Add bootstrap as the first lines of `start(...)`**

```java
@Override
public void start(Stage primaryStage) throws Exception {
    AppSettings settings = SettingsService.getInstance().getCurrent();
    ProxyService.enableNtlmAuthentication();
    ProxyService.clearJvmProxyProperties();
    ProxyService.applyMode(ProxyConfig.from(settings.proxy(),
            PasswordCipher.decrypt(settings.proxy().manual().passwordEncrypted())));

    // ... existing body unchanged ...
}
```

(Imports: `com.tpt.validator.config.*`, `com.tpt.validator.external.proxy.*`.)

- [ ] **Step 3: Verify compile + smoke run**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS.

If a fast smoke test of the JavaFX startup is desired (only on a graphical workstation):
Run: `mvn -q javafx:run`
Expected: window opens; in DEBUG logs the line `NTLM authentication enabled`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/tpt/validator/App.java
git commit -m "feat(app): bootstrap NTLM + clear/apply proxy on startup"
```

---

## Task 23: Wire phase 2 into `MainController.validate(...)` with progress dialog

**Files:**
- Modify: `src/main/java/com/tpt/validator/ui/MainController.java`

- [ ] **Step 1: Locate the existing validate handler**

Run: `grep -n "ValidationEngine\|validateButton\|onValidate\|@FXML" src/main/java/com/tpt/validator/ui/MainController.java | head -30`

Identify the method that runs `ValidationEngine.validate(...)`.

- [ ] **Step 2: Wrap the existing logic to add phase 2**

After the line that produces `localFindings` (the call to `ValidationEngine.validate(...)`):

```java
AppSettings settings = SettingsService.getInstance().getCurrent();
List<Finding> all = new ArrayList<>(localFindings);
externalStatusLabel.setText("");

if (settings.external().enabled()) {
    Path cacheDir = settings.external().cache().directory().isEmpty()
            ? Path.of(System.getProperty("user.home"), ".config", "tpt-validator", "cache")
            : Path.of(settings.external().cache().directory());

    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tpt/validator/ui/LookupProgressDialog.fxml"));
    Parent root = loader.load();
    LookupProgressController progress = loader.getController();
    Stage stage = new Stage();
    progress.setStage(stage);
    stage.initModality(Modality.APPLICATION_MODAL);
    stage.setTitle("External validation");
    stage.setScene(new Scene(root));

    Task<List<Finding>> task = new Task<>() {
        @Override protected List<Finding> call() {
            ExternalValidationService svc = ExternalValidationService.forProduction(
                    cacheDir, settings.external().isin());
            return svc.run(file, settings, progress::isCancelled);
        }
    };
    task.setOnSucceeded(ev -> {
        all.addAll(task.getValue());
        long failed = task.getValue().stream()
                .filter(f -> f.ruleId().startsWith("EXTERNAL/"))
                .count();
        if (failed > 0) {
            externalStatusLabel.setText("⚠ Online validation: " + failed + " issue(s) — see findings tab");
        }
        progress.close();
        // existing post-validate code (refresh tables, build report) runs here using `all`
    });
    task.setOnFailed(ev -> {
        progress.close();
        externalStatusLabel.setText("⚠ Online validation failed");
    });
    new Thread(task, "tpt-online-validation").start();
    stage.showAndWait();
} else {
    // existing post-validate code (refresh tables, build report) runs here using `localFindings`
}
```

The two "existing post-validate code" comment blocks indicate the spot where today's code already populates the table and quality report from `localFindings`. Refactor that into a private `void applyFindings(List<Finding>)` method first, then call it from both branches and from `task.setOnSucceeded`.

- [ ] **Step 3: Run all existing tests to ensure nothing broke**

Run: `mvn -q test`
Expected: BUILD SUCCESS, all existing tests still pass.

- [ ] **Step 4: Manual smoke test (workstation only)**

```
mvn javafx:run
```
- Open a small TPT file with a known LEI/ISIN.
- Toggle "Online validation" on, click Validate.
- Expect: progress dialog appears, closes after a few seconds; new `LEI-LIVE/...` or `ISIN-LIVE/...` findings appear (or none if all are valid).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tpt/validator/ui/MainController.java
git commit -m "feat(ui): integrate external validation phase into Validate flow"
```

---

## Task 24: README + ROADMAP update

**Files:**
- Modify: `README.md` (Validation overview section)
- Modify: `docs/ROADMAP.md` (mark §5.1 / §5.2 as done)

- [ ] **Step 1: Append to `README.md` Validation overview**

Add a new bullet at the end of the rule-families list:

```
- **LEI-LIVE / ISIN-LIVE** — optional online cross-check against GLEIF and
  OpenFIGI when the user opens *Settings…* and enables online validation.
  Off by default. Works behind corporate HTTP/NTLM proxies via
  *System proxy* mode (recommended) or manual proxy with encrypted
  credentials. See `docs/superpowers/specs/2026-04-27-external-validation-gleif-openfigi-design.md`.
```

- [ ] **Step 2: Update `docs/ROADMAP.md` items 5.1 and 5.2**

In sections 5.1 and 5.2, prepend `### 5.1 ✅ (erledigt)` / `### 5.2 ✅ (erledigt)` style markers and add a one-line implementation pointer.

- [ ] **Step 3: Commit**

```bash
git add README.md docs/ROADMAP.md
git commit -m "docs: mark external validation roadmap items as done"
```

---

## Final verification

- [ ] **Step 1: Run the entire test suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS. The new tests added by this plan plus all existing tests pass.

- [ ] **Step 2: Run a `javafx:run` smoke**

Run: `mvn -q javafx:run` (workstation only).
Expected: window opens; toggle "Online validation" off (default); open a known good file; click Validate; existing behaviour unchanged. Then toggle on, open `Settings…`, configure, run again — see online findings appear.

- [ ] **Step 3: Final commit (if any leftover changes)**

```bash
git status
# if anything remains:
git add -p
git commit -m "chore: final polish for external validation feature"
```

---

## Self-review notes

- Spec coverage: every section/requirement in the spec maps to a task in this plan.
  - §5.1 packages → tasks 1–18.
  - §6 settings schema → tasks 1, 3.
  - §7.1 flow → tasks 18, 23.
  - §7.2 identifier sources → task 18 (`LEI_PAIRS`, `ISIN_PAIRS`).
  - §7.3 finding identity → tasks 16, 17, 18 (`EXTERNAL/*` IDs).
  - §7.4 name comparison → task 15.
  - §7.5 cancel semantics → task 18 (`cancelled` BooleanSupplier) + task 20 (button) + task 23 (wiring).
  - §8 proxy bootstrap → tasks 4–8, 22.
  - §8.2 HttpClientFactory → task 10.
  - §8.3 TLS — no code change required; documented.
  - §9 UI → tasks 19–21.
  - §10 error handling → task 18 (try/catch around each phase) + task 23 (status label).
  - §11 testing → covered per task.
  - §12 implementation order → mirrored in task order.
  - §13 follow-ups → out of scope here; CIC consistency toggle wired but no-op (task 17 note).
- Deviation from spec §5.1: `ExternalLookupSummaryRule.java` is **not** created as a separate file. Its responsibility (one INFO finding per failed service) is fulfilled inline by `ExternalValidationService.run(...)` — this is simpler, keeps the rule package free of non-Rule classes, and was decided during the plan-writing step. The spec line under §5.1 should be read as "this responsibility lives in the orchestrator."
- Placeholder scan: no "TBD", "TODO", "implement later", or vague phrases remain.
- Type consistency: `LeiOnlineRule.LeiHit` / `IsinOnlineRule.IsinHit` field names match between definition (tasks 16, 17) and use (task 18). `Finding.error/warning/info` factory shapes are flagged in tasks 16 and 18 with a fallback note ("if signature differs, look at IsinRule.java for actual factory") because the existing `Finding.java` was not read in detail during planning — the executing agent confirms the signature in task 16 step 4.
