package com.findatex.validator.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;

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
        AppSettings loaded = load();
        // If load() detected a legacy unencrypted OpenFIGI key on disk, eagerly
        // rewrite the file so the plaintext stops sitting there. We use a flag
        // on the loaded snapshot rather than checking equality (encrypt is
        // non-deterministic — random IV per call).
        boolean migrated = legacyMigrationDetected;
        legacyMigrationDetected = false;
        this.current = loaded;
        if (migrated && loaded != null) {
            try {
                update(loaded);
                log.info("Migrated legacy plaintext OpenFIGI key to encrypted form in {}", file);
            } catch (RuntimeException e) {
                log.warn("Could not auto-rewrite settings after legacy-key migration: {}", e.toString());
            }
        }
    }

    /** Set transiently by {@link #decryptOpenFigiKey(AppSettings)} when a legacy plaintext key was observed. */
    private volatile boolean legacyMigrationDetected;

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
            // Create parent dir with 0700 on POSIX so the encrypted-credentials file
            // is never readable by other local users via umask-default 0755 dirs.
            PosixPerms.createOwnerOnlyParents(file);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            // Pre-create the .tmp with 0600 so the subsequent FileOutputStream
            // (Jackson's writer) inherits the perms — closes the window where the
            // tmpfile is briefly world-readable before the atomic move.
            createOrTightenTmp(tmp);
            // Encrypt sensitive fields right before writing so the on-disk form
            // never holds plaintext. The in-memory record we cache continues to
            // hold plaintext — encryption is a transparent storage concern.
            MAPPER.writeValue(tmp.toFile(), encryptOpenFigiKey(next));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            PosixPerms.tightenToOwnerOnly(file);
            this.current = next;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save settings to " + file, e);
        }
    }

    private static void createOrTightenTmp(Path tmp) throws IOException {
        if (PosixPerms.posixAvailable()) {
            Files.deleteIfExists(tmp);
            Files.createFile(tmp, PosixFilePermissions.asFileAttribute(
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        }
        // Windows: rely on default ACL (user-private under user profile).
    }

    private AppSettings load() {
        if (!Files.exists(file)) return AppSettings.defaults();
        try {
            AppSettings raw = MAPPER.readValue(file.toFile(), AppSettings.class);
            return raw == null ? AppSettings.defaults() : decryptOpenFigiKey(raw);
        } catch (IOException e) {
            log.warn("Could not read settings from {} ({}); using defaults", file, e.getMessage());
            return AppSettings.defaults();
        }
    }

    /** Returns a copy of {@code s} with the OpenFIGI key replaced by its AES-GCM ciphertext. */
    private static AppSettings encryptOpenFigiKey(AppSettings s) {
        AppSettings.External e = s.external();
        if (e == null || e.isin() == null) return s;
        AppSettings.Isin isin = e.isin();
        String stored = PasswordCipher.encrypt(isin.openFigiApiKey());
        return replaceIsin(s, new AppSettings.Isin(
                isin.enabled(), stored, isin.checkCurrency(), isin.checkCicConsistency()));
    }

    /**
     * Inverse of {@link #encryptOpenFigiKey(AppSettings)}. If decryption fails
     * (returns empty for a non-empty input), assume the file is from a pre-
     * encryption release and treat the value as legacy plaintext — the
     * constructor then triggers an immediate re-save in encrypted form.
     */
    private AppSettings decryptOpenFigiKey(AppSettings s) {
        AppSettings.External e = s.external();
        if (e == null || e.isin() == null) return s;
        AppSettings.Isin isin = e.isin();
        String stored = isin.openFigiApiKey();
        String plaintext;
        if (stored == null || stored.isEmpty()) {
            plaintext = "";
        } else {
            String decrypted = PasswordCipher.decrypt(stored);
            if (!decrypted.isEmpty()) {
                plaintext = decrypted;
            } else {
                plaintext = stored;
                legacyMigrationDetected = true;
            }
        }
        return replaceIsin(s, new AppSettings.Isin(
                isin.enabled(), plaintext, isin.checkCurrency(), isin.checkCicConsistency()));
    }

    private static AppSettings replaceIsin(AppSettings s, AppSettings.Isin newIsin) {
        AppSettings.External e = s.external();
        return new AppSettings(
                new AppSettings.External(e.enabled(), e.lei(), newIsin, e.cache()),
                s.proxy());
    }

    private static Path defaultPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path base = os.contains("win")
                ? Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "findatex-validator")
                : Path.of(System.getProperty("user.home"), ".config", "findatex-validator");
        return base.resolve("settings.json");
    }
}
