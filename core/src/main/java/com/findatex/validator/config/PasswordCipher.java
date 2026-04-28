package com.findatex.validator.config;

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
