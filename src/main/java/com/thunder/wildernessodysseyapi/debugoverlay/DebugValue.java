package com.thunder.wildernessodysseyapi.debugoverlay;

import java.util.Objects;

/**
 * A formatted value displayed by the Wilderness debug HUD.
 *
 * <p>Pages choose a semantic tone instead of an RGB value so the renderer owns
 * the visual palette and future themes do not leak into data providers.</p>
 */
public record DebugValue(String text, Tone tone) {
    public DebugValue {
        text = Objects.requireNonNullElse(text, "N/A");
        tone = Objects.requireNonNullElse(tone, Tone.NORMAL);
    }

    /** Creates an ordinary informational value. */
    public static DebugValue normal(Object value) {
        return new DebugValue(String.valueOf(value), Tone.NORMAL);
    }

    /** Creates a positive status value. */
    public static DebugValue good(Object value) {
        return new DebugValue(String.valueOf(value), Tone.GOOD);
    }

    /** Creates a value that needs attention but is still usable. */
    public static DebugValue warning(Object value) {
        return new DebugValue(String.valueOf(value), Tone.WARNING);
    }

    /** Creates a failed or critically unhealthy value. */
    public static DebugValue error(Object value) {
        return new DebugValue(String.valueOf(value), Tone.ERROR);
    }

    /** Creates a standard unavailable value. */
    public static DebugValue unavailable() {
        return unavailable("Unavailable");
    }

    /** Creates an unavailable value with a specific explanation. */
    public static DebugValue unavailable(Object value) {
        return new DebugValue(String.valueOf(value), Tone.UNAVAILABLE);
    }

    /** Describes why a value is colored without coupling pages to color constants. */
    public enum Tone {
        NORMAL,
        GOOD,
        WARNING,
        ERROR,
        UNAVAILABLE
    }
}
