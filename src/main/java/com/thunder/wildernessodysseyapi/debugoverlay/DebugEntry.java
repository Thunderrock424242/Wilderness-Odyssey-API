package com.thunder.wildernessodysseyapi.debugoverlay;

import java.util.Objects;

/** A label/value row, or an unlabelled raw line, inside a debug section. */
public record DebugEntry(String label, DebugValue value, boolean raw) {
    public DebugEntry {
        label = Objects.requireNonNullElse(label, "");
        value = Objects.requireNonNull(value, "Debug entries require a value");
    }

    /** Creates an aligned label/value entry. */
    public static DebugEntry of(String label, DebugValue value) {
        return new DebugEntry(label, value, false);
    }

    /** Creates an aligned entry with a normal semantic value. */
    public static DebugEntry of(String label, Object value) {
        return of(label, DebugValue.normal(value));
    }

    /** Creates an unmodified full-width line for the Vanilla Raw page. */
    public static DebugEntry raw(String line) {
        return new DebugEntry("", DebugValue.normal(line), true);
    }
}
