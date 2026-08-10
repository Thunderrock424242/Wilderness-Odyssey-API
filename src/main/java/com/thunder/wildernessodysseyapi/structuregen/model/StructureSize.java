package com.thunder.wildernessodysseyapi.structuregen.model;

/** Local X/Y/Z dimensions of a structure template. */
public record StructureSize(int x, int y, int z) {

    /** Returns the bounding-box volume using a long to avoid integer overflow. */
    public long volume() {
        return (long) x * y * z;
    }

    /** Returns whether a local block coordinate falls inside these dimensions. */
    public boolean contains(StructurePosition position) {
        return position.x() >= 0 && position.x() < x
                && position.y() >= 0 && position.y() < y
                && position.z() >= 0 && position.z() < z;
    }

    /** Formats dimensions consistently in diagnostics and reports. */
    public String display() {
        return "[" + x + ", " + y + ", " + z + "]";
    }
}
