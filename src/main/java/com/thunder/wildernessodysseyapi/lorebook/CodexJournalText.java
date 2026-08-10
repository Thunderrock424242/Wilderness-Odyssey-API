package com.thunder.wildernessodysseyapi.lorebook;

/**
 * Defines the server-authoritative text rules for a player's personal Codex journal.
 *
 * <p>The same sanitizer is used before networking and before persistence so a
 * modified client cannot store control characters or an unbounded payload in
 * player data.</p>
 */
public final class CodexJournalText {
    public static final int MAX_LENGTH = 8_192;

    private CodexJournalText() {
    }

    /**
     * Normalizes line endings, removes non-printing control characters, and
     * clamps text without splitting a Unicode surrogate pair.
     *
     * @param rawText untrusted journal text from storage or a network payload
     * @return normalized journal text safe for the Codex editor
     */
    public static String sanitize(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        String normalized = rawText.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sanitized = new StringBuilder(Math.min(normalized.length(), MAX_LENGTH));
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            int characterCount = Character.charCount(codePoint);
            offset += characterCount;

            if (sanitized.length() + characterCount > MAX_LENGTH) {
                break;
            }
            if (codePoint == '\n' || codePoint == '\t'
                    || (!Character.isISOControl(codePoint) && codePoint != 0x00A7)) {
                sanitized.appendCodePoint(codePoint);
            }
        }
        return sanitized.toString();
    }
}
