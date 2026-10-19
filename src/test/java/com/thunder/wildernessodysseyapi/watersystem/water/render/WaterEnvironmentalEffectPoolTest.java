package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import net.minecraft.util.RandomSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WaterEnvironmentalEffectPoolTest {
    @AfterEach
    void clearPool() {
        WaterEnvironmentalEffectPool.clear();
    }

    @Test
    void saturatedPoolCannotExceedSharedTickBudgetAndDisabledSettingsClearIt() {
        for (int index = 0; index < 48; index++) {
            WaterEnvironmentalEffectPool.offer(WaterEnvironmentalEffectPool.Kind.MIST,
                    0, 64, 0, 0, 0, 1, 0);
        }
        AtomicInteger emitted = new AtomicInteger();
        int count = WaterEnvironmentalEffectPool.advance(3, 0, 64, 0,
                new WaterEnvironmentalEffectPool.Settings(2, 16, true, true, true),
                (x, z) -> null,
                (kind, x, y, z, vx, vy, vz) -> emitted.incrementAndGet(), RandomSource.create(7));
        assertEquals(2, count);
        assertEquals(2, emitted.get());
        WaterEnvironmentalEffectPool.advance(4, 0, 64, 0,
                new WaterEnvironmentalEffectPool.Settings(0, 16, true, true, true),
                (x, z) -> null,
                (kind, x, y, z, vx, vy, vz) -> fail("Disabled effects emitted a particle"), RandomSource.create(7));
        assertEquals(0, WaterEnvironmentalEffectPool.activeCount());
    }
    @Test
    void generatedOnlyWaterAdvectsAlongRegionalCurrentAndUsesVisibleSurface() {
        var column = column(0, 0);
        var moved = WaterEnvironmentalEffectPool.advect(
                4, 64, 4, 0, 0, column, 0.06f, 0,
                (x, z) -> 64.35f);
        // 0.06 blocks/tick regional flow = 1.2 m/s. Emitter relaxation is 8%.
        assertEquals(4.0048, moved.x(), 1.0e-7);
        assertEquals(4, moved.z(), 1.0e-7);
        assertEquals(64.35, moved.y(), 1.0e-5);
        assertTrue(moved.present());
    }

    @Test
    void canonicalMotionReplacesRegionalMotionBeforeEmitterTickConversion() {
        var moved = WaterEnvironmentalEffectPool.advect(
                4, 64, 4, 0, 0, column(0, -0.4f), 0.06f, 0,
                (x, z) -> 64);
        assertEquals(4, moved.x(), 1.0e-7);
        assertEquals(3.9984, moved.z(), 1.0e-7);
    }

    @Test
    void missingReceivingSurfaceExpiresEmitterWithoutCrossingFrontier() {
        var moved = WaterEnvironmentalEffectPool.advect(
                4, 64, 4, 0.1, 0, column(0, 0), 0.06f, 0,
                (x, z) -> Float.NaN);
        assertFalse(moved.present());
        assertEquals(4, moved.x());
        assertEquals(4, moved.z());
    }

    @Test
    void floatingEffectsDoNotJumpOntoHigherWaterOrAcrossLargeFalls() {
        assertFalse(WaterEnvironmentalEffectPool.advect(
                4, 64, 4, 0, 0, column(0, 0), 0.06f, 0,
                (x, z) -> 70).present());
        assertFalse(WaterEnvironmentalEffectPool.advect(
                4, 64, 4, 0, 0, column(0, 0), 0.06f, 0,
                (x, z) -> 48).present());
    }

    @Test
    void disabledDistantAndExpiredEffectsNeverSpendTheEmissionBudget() {
        assertFalse(WaterEnvironmentalEffectPool.admitted(100, 99, 4, 16, true));
        assertFalse(WaterEnvironmentalEffectPool.admitted(100, 120, 257, 16, true));
        assertFalse(WaterEnvironmentalEffectPool.admitted(100, 120, 4, 16, false));
        assertTrue(WaterEnvironmentalEffectPool.admitted(100, 120, 256, 16, true));
    }

    private static ClientWaterChunkSnapshot.Column column(float x, float z) {
        return new ClientWaterChunkSnapshot.Column(true, 63, 60, 8, 0, 255, 0,
                x, z, GeneratedWaterChunk.BodyType.RIVER,
                GeneratedWaterChunk.Cell.DEFAULT_WATER_TINT, false);
    }
}
