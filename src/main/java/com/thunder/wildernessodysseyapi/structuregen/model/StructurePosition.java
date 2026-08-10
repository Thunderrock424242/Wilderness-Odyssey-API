package com.thunder.wildernessodysseyapi.structuregen.model;

/** Integer local coordinate inside a structure template. */
public record StructurePosition(int x, int y, int z) implements Comparable<StructurePosition> {

    /**
     * Sorts by layer, then row, then column so generated block order is stable
     * and inspection output remains easy to scan vertically.
     */
    @Override
    public int compareTo(StructurePosition other) {
        int byY = Integer.compare(y, other.y);
        if (byY != 0) {
            return byY;
        }
        int byZ = Integer.compare(z, other.z);
        return byZ != 0 ? byZ : Integer.compare(x, other.x);
    }

    /** Formats this coordinate consistently in diagnostics. */
    public String display() {
        return "[" + x + ", " + y + ", " + z + "]";
    }
}
