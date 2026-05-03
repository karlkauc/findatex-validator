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
            // Create parent dir with 0700 on POSIX so the encrypted-credentials file
            // is never readable by other local users via umask-default 0755 dirs.
            PosixPerms.createOwnerOnlyParents(file);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            // Pre-create the .tmp with 0600 so the subsequent FileOutputStream
            // (Jackson's writer) inherits the perms — closes the window where the
            // tmpfile is briefly world-readable before the atomic move.
            createOrTightenTmp(tmp);
            MAPPER.writeValue(tmp.toFile(), next);
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
            return raw == null ? AppSettings.defaults() : raw;
        } catch (IOException e) {
            log.warn("Could not read settings from {} ({}); using defaults", file, e.getMessage());
            return AppSettings.defaults();
        }
    }

    private static Path defaultPath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path base = os.contains("win")
                ? Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "findatex-validator")
                : Path.of(System.getProperty("user.home"), ".config", "findatex-validator");
        return base.resolve("settings.json");
    }
}
