package com.findatex.validator.config;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 * Helpers for creating files and directories with owner-only POSIX permissions
 * ({@code 0600} / {@code 0700}). On Windows the calls fall back to defaults —
 * NTFS ACLs are already user-private for files under a user profile.
 *
 * <p>Used by {@code SettingsService} (the encrypted-proxy-credential file) and
 * by {@code JsonCache} (GLEIF/OpenFIGI lookup data) so that on a shared Linux
 * host another local user cannot read these files via the umask-default
 * 0644/0755. Defence-in-depth — a same-UID attacker still wins.
 */
public final class PosixPerms {

    private static final Set<PosixFilePermission> OWNER_RW =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private static final Set<PosixFilePermission> OWNER_RWX = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    private PosixPerms() {}

    /** True iff the default filesystem reports the {@code posix} attribute view. */
    public static boolean posixAvailable() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    /** Creates parent dirs (0700 when POSIX) up to the given file's parent. No-op if parent is null. */
    public static void createOwnerOnlyParents(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent == null) return;
        if (posixAvailable()) {
            Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(OWNER_RWX));
        } else {
            Files.createDirectories(parent);
        }
    }

    /**
     * Tightens an existing file to {@code 0600} on POSIX systems. Quiet no-op
     * elsewhere. Use after {@code Files.move(...)} from a tempfile, since the
     * temp file's permissions are inherited by the move target on most JDKs.
     */
    public static void tightenToOwnerOnly(Path file) throws IOException {
        if (!posixAvailable()) return;
        if (!Files.exists(file)) return;
        Files.setPosixFilePermissions(file, OWNER_RW);
    }
}
