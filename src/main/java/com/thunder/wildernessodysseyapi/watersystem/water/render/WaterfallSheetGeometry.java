package com.thunder.wildernessodysseyapi.watersystem.water.render;

/** Bounded whitewater material over the existing native curtain, never replacement water volume. */
final class WaterfallSheetGeometry {
    private WaterfallSheetGeometry() { }

    static int emit(int waterY, int topY, float physicalSpeed, int budget,
                    WetCell wet, ExposedFace exposed, QuadSink sink) {
        if (budget <= 0 || topY <= waterY || topY - waterY > 16
                || !Float.isFinite(physicalSpeed) || physicalSpeed < .02f) return 0;
        for (int y = waterY; y <= topY; y++) if (!wet.at(y)) return 0;
        int quads = 0;
        for (int y = waterY + 1; y <= topY && quads < budget; y++) {
            float foam = (float) Math.min(1, Math.sqrt(physicalSpeed * physicalSpeed
                    + 2 * 9.81 * (topY - y + .5)) / 8);
            for (int side = 0; side < 4 && quads < budget; side++) {
                if (!exposed.at(side, y)) continue;
                sink.emit(side, y, foam);
                quads++;
            }
        }
        return quads;
    }

    @FunctionalInterface interface WetCell { boolean at(int y); }
    @FunctionalInterface interface ExposedFace { boolean at(int side, int y); }
    @FunctionalInterface interface QuadSink { void emit(int side, int y, float foam); }

    static void quad(int normalX, int normalZ, float x, float y, float z, float width, VertexSink sink) {
        float tx = -normalZ * width;
        float tz = normalX * width;
        sink.emit(x - tx, y, z - tz, 0, 1);
        sink.emit(x - tx, y + 1, z - tz, 0, 0);
        sink.emit(x + tx, y + 1, z + tz, 1, 0);
        sink.emit(x + tx, y, z + tz, 1, 1);
    }

    @FunctionalInterface interface VertexSink { void emit(float x, float y, float z, float u, float v); }
}
