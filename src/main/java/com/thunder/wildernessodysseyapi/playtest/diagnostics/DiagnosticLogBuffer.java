package com.thunder.wildernessodysseyapi.playtest.diagnostics;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Pattern;

/** Bounded session-only log buffer. Redaction happens before memory or disk retention. */
public final class DiagnosticLogBuffer {
    private static final Pattern SECRETS = Pattern.compile("(?i)(?:bearer\\s+[A-Za-z0-9._~+/-]+|(?:password|api[_-]?key|secret|(?:access|auth|session|refresh)?token)\\s*[:=]\\s*[\\\"']?[^\\s\\\"']+|https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/[^\\s]+|[A-Za-z0-9_-]{23,28}\\.[A-Za-z0-9_-]{6,8}\\.[A-Za-z0-9_-]{20,110})");
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final int maximum;
    private int bytes;

    public DiagnosticLogBuffer(int maximum) { this.maximum = Math.clamp(maximum, 256, 256 * 1024); }

    public synchronized void append(String level, String logger, String message) {
        if (message == null || logger == null || logger.toLowerCase(Locale.ROOT).contains("chat")
                || message.contains("[CHAT]") || message.contains("[System]")
                || message.substring(0, Math.min(256, message.length())).matches("(?:\\[[^]\\r\\n]{1,40}] )?<[^>\\r\\n]{1,64}> .*")) return;
        // Bound input before applying regular expressions, including maliciously large log messages.
        String input = "[" + level + "] [" + logger + "] " + message.substring(0, Math.min(8192, message.length()));
        String line = redact(input.substring(0, Math.min(8192, input.length()))) + "\n";
        while (line.getBytes(StandardCharsets.UTF_8).length > maximum) line = line.substring(0, line.length() / 2);
        int size = line.getBytes(StandardCharsets.UTF_8).length;
        while (bytes + size > maximum && !lines.isEmpty()) bytes -= lines.removeFirst().getBytes(StandardCharsets.UTF_8).length;
        lines.addLast(line);
        bytes += size;
    }

    public static String redact(String text) {
        return SECRETS.matcher(text).replaceAll("[redacted]")
                .replaceAll("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}", "[redacted-email]")
                .replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "[redacted-ip]")
                .replaceAll("(?i)[A-Z]:\\\\Users\\\\[^\\\\\\r\\n]+", "[redacted-home]")
                .replaceAll("/(?:home|Users)/[^/\\r\\n]+", "/[redacted-home]");
    }

    public synchronized String drain() { String result = String.join("", lines); clear(); return result; }
    public synchronized void clear() { lines.clear(); bytes = 0; }
}
