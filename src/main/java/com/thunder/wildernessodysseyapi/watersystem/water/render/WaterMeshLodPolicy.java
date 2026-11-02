package com.thunder.wildernessodysseyapi.watersystem.water.render;

/** Bounded detail selection for existing cached surface meshes. */
final class WaterMeshLodPolicy {
    private WaterMeshLodPolicy() { }

    static int select(int ceiling, boolean enabled, boolean sensitive, double distanceSquared,
                      float seaStrength, int previous, int nearDistance) {
        if (ceiling <= 1) return 1;
        if (!enabled || sensitive || Double.isNaN(distanceSquared) || distanceSquared < 0) return 2;
        float sea = Float.isFinite(seaStrength) ? Math.max(0, Math.min(1, seaStrength)) : 0;
        double radius = nearDistance * (1.0 + sea) + (previous == 2 ? 24 : 0);
        return distanceSquared <= radius * radius ? 2 : 1;
    }

    static boolean shouldQueue(int current, int requested, int desired, long elapsedTicks) {
        return desired != current && desired != requested && (desired > current || elapsedTicks >= 40);
    }

    static boolean sensitiveColumn(float depth, float currentX, float currentZ, int riverWeight) {
        return depth < 6 || Math.abs(currentX) > 0.2f || Math.abs(currentZ) > 0.2f || riverWeight >= 96;
    }

    static double distanceSquaredToChunk(double cameraX, double cameraZ, int originX, int originZ) {
        double dx = Math.max(Math.max(originX - cameraX, cameraX - (originX + 16.0)), 0);
        double dz = Math.max(Math.max(originZ - cameraZ, cameraZ - (originZ + 16.0)), 0);
        return dx * dx + dz * dz;
    }
}
