package com.getjobs.application.security;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
public final class CookieCrypto {
    private static final String ENV_KEY = "GETJOBS_COOKIE_KEY";
    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final Path KEY_PATH = Path.of("./db/.cookie-key");
    private static final SecureRandom RANDOM = new SecureRandom();

    private CookieCrypto() {}

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank() || plainText.startsWith(PREFIX)) {
            return plainText;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, loadKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("加密 Cookie 失败", exception);
        }
    }

    public static String decryptIfNeeded(String value) {
        if (value == null || value.isBlank() || !value.startsWith(PREFIX)) {
            return value;
        }

        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalStateException("无效的密文负载");
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, loadKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            log.warn("解密 Cookie 失败，将返回空值: {}", exception.getMessage());
            return "";
        }
    }

    private static SecretKey loadKey() throws Exception {
        String envValue = System.getenv(ENV_KEY);
        if (envValue != null && !envValue.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(envValue.trim());
            return new SecretKeySpec(decoded, "AES");
        }

        if (Files.exists(KEY_PATH)) {
            byte[] decoded = Base64.getDecoder().decode(Files.readString(KEY_PATH).trim());
            return new SecretKeySpec(decoded, "AES");
        }

        Files.createDirectories(KEY_PATH.getParent());
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey secretKey = keyGenerator.generateKey();
        Files.writeString(KEY_PATH, Base64.getEncoder().encodeToString(secretKey.getEncoded()));
        return secretKey;
    }
}
