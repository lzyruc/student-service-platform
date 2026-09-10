package com.college.student_service_platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class PasswordService {
    private final BCryptPasswordEncoder encoder;

    public PasswordService(@Value("${security.password.bcrypt-strength:12}") int strength) {
        if (strength < 10 || strength > 16) {
            throw new IllegalArgumentException("BCrypt strength 必须在 10 到 16 之间");
        }
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    public String encode(String rawPassword) {
        String raw = normalize(rawPassword);
        if (raw.length() < 6 || raw.length() > 72) {
            throw new IllegalArgumentException("密码长度必须在 6 到 72 位之间");
        }
        return encoder.encode(raw);
    }

    public boolean matches(String rawPassword, String storedPassword) {
        String raw = normalize(rawPassword);
        String stored = normalize(storedPassword);
        if (raw.isEmpty() || stored.isEmpty()) return false;
        if (isBcrypt(stored)) return encoder.matches(raw, stored);
        if (isMd5(stored)) {
            return MessageDigest.isEqual(stored.toLowerCase().getBytes(StandardCharsets.UTF_8),
                    md5Hex(raw).getBytes(StandardCharsets.UTF_8));
        }
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8),
                raw.getBytes(StandardCharsets.UTF_8));
    }

    public boolean needsUpgrade(String storedPassword) {
        return !isBcrypt(normalize(storedPassword));
    }

    private boolean isBcrypt(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private boolean isMd5(String value) {
        return value.matches("(?i)^[0-9a-f]{32}$");
    }

    private String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法校验旧密码", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
