package com.firstapi.backend.service;

import com.firstapi.backend.config.AppSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SensitiveDataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitiveDataService.class);
    private static final String PREFIX = "enc:";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppSecurityProperties properties;
    private SecretKeySpec keySpec;

    public SensitiveDataService(AppSecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        this.keySpec = new SecretKeySpec(sha256(properties.getDataSecret()), "AES");
        if ("local-dev-data-secret-change-me".equals(properties.getDataSecret())) {
            LOGGER.warn("Using the default data encryption secret. Set FIRSTAPI_DATA_SECRET before public deployment.");
        }
    }

    public String protect(String value) {
        if (value == null || value.trim().isEmpty() || value.startsWith(PREFIX)) {
            return value;
        }
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to protect sensitive data", ex);
        }
    }

    public String reveal(String value) {
        if (value == null || value.trim().isEmpty() || !value.startsWith(PREFIX)) {
            return value;
        }
        String[] parts = value.substring(PREFIX.length()).split("\\.");
        if (parts.length != 2) {
            throw new IllegalStateException("Stored secret has an invalid format");
        }
        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[0]);
            byte[] cipherText = Base64.getUrlDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to reveal sensitive data", ex);
        }
    }

    private byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to initialize data encryption", ex);
        }
    }
}
