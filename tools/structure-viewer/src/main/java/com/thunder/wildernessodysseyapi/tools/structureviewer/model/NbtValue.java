package com.thunder.wildernessodysseyapi.tools.structureviewer.model;

import java.util.List;
import java.util.Map;

/** Typed NBT tree for imported metadata; numeric and array types survive inspection. */
public record NbtValue(int type, Object value) {
    public NbtValue {
        if (value instanceof Map<?, ?> map) value = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(map));
        if (value instanceof List<?> list) value = List.copyOf(list);
    }

    /** Compound entries, or an empty map for other types. */
    @SuppressWarnings("unchecked")
    public Map<String, NbtValue> compound() {
        return type == 10 ? (Map<String, NbtValue>) value : Map.of();
    }

    /** List/array entries, or an empty list for other types. */
    @SuppressWarnings("unchecked")
    public List<NbtValue> list() {
        return value instanceof List<?> ? (List<NbtValue>) value : List.of();
    }

    /** Bounded typed tree text, keeping large payloads responsive in the inspector. */
    public String display() {
        StringBuilder result = new StringBuilder();
        append(result, 0);
        if (result.length() > 32768) result.setLength(32768);
        if (result.length() == 32768) result.append("\n… display truncated; original data retained");
        return result.toString();
    }

    private void append(StringBuilder out, int depth) {
        if (out.length() >= 32768) return;
        if (type == 10) {
            out.append("{\n");
            for (var entry : compound().entrySet()) {
                if (out.length() >= 32768) break;
                out.append("  ".repeat(Math.min(depth + 1, 32))).append(entry.getKey()).append(": ");
                entry.getValue().append(out, depth + 1);
                out.append('\n');
            }
            out.append("  ".repeat(Math.min(depth, 32))).append('}');
        } else if (value instanceof List<?>) {
            out.append(type == 7 ? "[B; " : type == 11 ? "[I; " : type == 12 ? "[L; " : "[");
            for (NbtValue item : list()) {
                if (out.length() >= 32768) break;
                item.append(out, depth + 1);
                out.append(", ");
            }
            out.append(']');
        } else {
            if (type == 8) out.append('"');
            out.append(value);
            out.append(switch (type) { case 1 -> "b"; case 2 -> "s"; case 4 -> "L";
                case 5 -> "f"; case 6 -> "d"; case 8 -> "\""; default -> ""; });
        }
    }
}

