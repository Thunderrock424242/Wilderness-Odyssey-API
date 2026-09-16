package com.thunder.wildernessodysseyapi.tools.structureviewer.render;

/** Immutable world/camera vector using Minecraft's Y-up coordinates. */
public record Vec3(double x, double y, double z) {
    /** Adds another vector. */
    public Vec3 add(Vec3 b) { return new Vec3(x + b.x, y + b.y, z + b.z); }
    /** Subtracts another vector. */
    public Vec3 subtract(Vec3 b) { return new Vec3(x - b.x, y - b.y, z - b.z); }
    /** Scales the vector. */
    public Vec3 multiply(double v) { return new Vec3(x * v, y * v, z * v); }
    /** Dot product. */
    public double dot(Vec3 b) { return x * b.x + y * b.y + z * b.z; }
}

