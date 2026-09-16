package com.thunder.aether.server.security;

import com.sun.net.httpserver.Headers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Fixed credential identities provide a bounded, non-spoofable rate-limit key. */
public final class ApiSecurity {
    private final List<String> keys;
    private final List<byte[]> encoded;
    private final long[] windows;
    private final int[] counts;
    private final int limit;
    public ApiSecurity(List<String> keys, int limit) {
        this.keys = List.copyOf(keys);
        encoded = keys.stream().map(k -> k.getBytes(StandardCharsets.UTF_8)).toList();
        windows = new long[keys.size()]; counts = new int[keys.size()]; this.limit = limit;
    }

    /** Returns a credential slot, never the secret or a player-supplied server identifier. */
    public int authenticate(Headers headers) {
        List<String> values = headers.get("Authorization");
        if (values == null || values.size() != 1) { return -1; }
        String value = values.getFirst();
        if (!value.startsWith("Bearer ") || value.length() > 1031) { return -1; }
        byte[] supplied = value.substring(7).getBytes(StandardCharsets.UTF_8);
        int result = -1;
        for (int i = 0; i < encoded.size(); i++) {
            if (MessageDigest.isEqual(encoded.get(i), supplied)) { result = i; }
        }
        return result;
    }

    /** A fixed one-minute window is shared by all declared server IDs using a credential. */
    public synchronized boolean allow(int identity, long nowNanos) {
        if (windows[identity] == 0 || nowNanos - windows[identity] >= 60_000_000_000L) {
            windows[identity] = nowNanos; counts[identity] = 0;
        }
        if (counts[identity] >= limit) { return false; }
        counts[identity]++; return true;
    }

    /** Opt-in content logging still redacts every configured credential and control character. */
    public String safeLog(String value) {
        String safe = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", " ");
        for (String key : keys) { safe = safe.replace(key, "<redacted>"); }
        return safe.substring(0, Math.min(2000,safe.length()));
    }
}
