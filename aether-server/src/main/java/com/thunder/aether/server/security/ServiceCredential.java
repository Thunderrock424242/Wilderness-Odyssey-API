package com.thunder.aether.server.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/** One operator-provisioned service secret, retained only as a digest and never serialized. */
public final class ServiceCredential {
    private final byte[] digest;
    private final Instant expiresAt;

    public ServiceCredential(String token, Instant expiresAt) {
        if (token != null && !token.isEmpty() && !token.matches("[A-Za-z0-9._~-]{32,256}")) {
            throw new IllegalArgumentException("INVALID_SERVICE_CREDENTIAL");
        }
        digest = token == null || token.isEmpty() ? null : digest(token);
        this.expiresAt = expiresAt;
    }

    /** Missing or expired credentials always reject requests. */
    public boolean matches(String token, Instant now) {
        return digest != null && token != null && token.length() <= 256
                && (expiresAt == null || now.isBefore(expiresAt))
                && MessageDigest.isEqual(digest, digest(token));
    }

    public boolean sameSecret(ServiceCredential other) {
        return digest != null && other.digest != null && MessageDigest.isEqual(digest, other.digest);
    }

    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }

    @Override public String toString() { return "ServiceCredential[redacted]"; }
}
