package com.thunder.wildernessodysseyapi.developmentstudio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Bounded text sanitation shared by Studio UI payloads and saved metadata. */
public final class StudioText {
    public static final int MAX_BOOKMARK_NAME = 64;
    public static final int MAX_BOOKMARK_NOTES = 512;
    public static final int MAX_TAGS = 8;
    public static final int MAX_TAG_LENGTH = 24;

    private StudioText() {
    }

    /** Removes formatting/control characters and bounds a one-line value. */
    public static String singleLine(String value, int maxLength) {
        return sanitize(value, maxLength, false).trim();
    }

    /** Removes formatting/control characters while retaining ordinary newlines. */
    public static String notes(String value) {
        return sanitize(value, MAX_BOOKMARK_NOTES, true).trim();
    }

    /** Returns a stable, deduplicated, bounded list of bookmark tags. */
    public static List<String> tags(List<String> values) {
        Set<String> sanitized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String tag = singleLine(value, MAX_TAG_LENGTH);
                if (!tag.isBlank()) {
                    sanitized.add(tag);
                }
                if (sanitized.size() >= MAX_TAGS) {
                    break;
                }
            }
        }
        return List.copyOf(new ArrayList<>(sanitized));
    }

    private static String sanitize(String value, int maxLength, boolean allowNewlines) {
        if (value == null || value.isEmpty() || maxLength <= 0) {
            return "";
        }

        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(Math.min(normalized.length(), maxLength));
        for (int offset = 0; offset < normalized.length() && result.length() < maxLength; ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n' && allowNewlines) {
                result.append('\n');
                continue;
            }
            if (codePoint == '\n' || codePoint == 0x00A7 || Character.isISOControl(codePoint)) {
                if (codePoint == '\n' && !allowNewlines && result.length() > 0
                        && result.charAt(result.length() - 1) != ' ') {
                    result.append(' ');
                }
                continue;
            }
            if (result.length() + Character.charCount(codePoint) > maxLength) {
                break;
            }
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }
}
