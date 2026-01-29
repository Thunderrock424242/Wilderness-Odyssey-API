package com.thunder.wildernessodysseyapi.ModPackPatches.telemetry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class TelemetryHashing {
    private TelemetryHashing() {
    }

    public static String hashIdentifier(String value, String salt) {
        if (value == null) {
            return null;
        }
        String input = value + (salt == null ? "" : salt);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            return value;
        }
    }
}
