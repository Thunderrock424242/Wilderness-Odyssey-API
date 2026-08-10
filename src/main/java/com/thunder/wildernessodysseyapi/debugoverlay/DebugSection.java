package com.thunder.wildernessodysseyapi.debugoverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An ordered group of related rows rendered under one section heading. */
public record DebugSection(String title, List<DebugEntry> entries) {
    public DebugSection {
        title = Objects.requireNonNullElse(title, "");
        entries = List.copyOf(entries);
    }

    /** Starts an ordered section builder. */
    public static Builder builder(String title) {
        return new Builder(title);
    }

    /** Builds sections without exposing mutable entry lists to page consumers. */
    public static final class Builder {
        private final String title;
        private final List<DebugEntry> entries = new ArrayList<>();

        private Builder(String title) {
            this.title = title;
        }

        /** Adds an ordinary aligned value. */
        public Builder add(String label, Object value) {
            return add(label, DebugValue.normal(value));
        }

        /** Adds an aligned semantic value. */
        public Builder add(String label, DebugValue value) {
            entries.add(DebugEntry.of(label, value));
            return this;
        }

        /** Adds an unlabelled full-width line. */
        public Builder addRaw(String line) {
            entries.add(DebugEntry.raw(line));
            return this;
        }

        /** Returns the immutable section. */
        public DebugSection build() {
            return new DebugSection(title, entries);
        }
    }
}
