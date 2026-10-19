package com.thunder.wildernessodysseyapi.anomaly.client;

import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.world.level.material.FogType;

/** Pure visual budgets shared by atmosphere presentation and its offline tests. */
public final class AnomalyEffectPolicy {
    private AnomalyEffectPolicy() {
    }

    public static float clampUnit(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }

    /** Rift influence uses the remaining headroom rather than duplicating biome strength. */
    public static float combinedStrength(float biome, float rift) {
        float base = clampUnit(biome);
        return clampUnit(base + (1.0F - base) * clampUnit(rift) * 0.75F);
    }

    public static float screenStrength(float biome, float rift, float configuredScale, float accessibilityScale) {
        return combinedStrength(biome, rift) * clampUnit(configuredScale) * clampUnit(accessibilityScale);
    }

    /** A slow, narrow pulse is the only motion; disabling either switch removes it completely. */
    public static float motionAmount(float screenStrength, float rift, boolean motionEnabled,
                                     boolean pulsesEnabled, float seconds) {
        if (!motionEnabled || !pulsesEnabled || !Float.isFinite(seconds)) {
            return 0.0F;
        }
        double wave = Math.max(0.0, Math.sin(seconds * Math.PI / 6.0));
        return (float) (Math.pow(wave, 8.0) * 0.0015F
                * clampUnit(screenStrength) * clampUnit(rift));
    }

    /** Air color remains mostly vanilla, including at the strongest local rift. */
    public static float fogBlend(float biome, float rift) {
        return combinedStrength(biome, rift) * 0.26F;
    }

    /** Stronger terrain zones shift gradually from cool cyan toward spatial violet. */
    public static float violetStrength(float biome, float rift) {
        return Math.max(clampUnit(rift), clampUnit((clampUnit(biome) - 0.35F) / 0.65F));
    }

    /** Sky geometry keeps its native horizon fog; fluids retain their own visibility rules. */
    public static boolean canAdjustFog(FogMode mode, FogType medium) {
        return mode == FogMode.FOG_TERRAIN && medium == FogType.NONE;
    }

    /** Limits the extra anomaly terrain haze to twelve percent of the existing far plane. */
    public static float fogFarPlane(float sourceFar, float strength) {
        if (!Float.isFinite(sourceFar) || sourceFar <= 0.0F) {
            return sourceFar;
        }
        return sourceFar * (1.0F - clampUnit(strength) * 0.12F);
    }

    /** At most two motes per twelve client ticks, with none at zero accessibility scale. */
    public static int particleCount(boolean enabled, float strength, long tick) {
        float amount = clampUnit(strength);
        if (!enabled || amount <= 0.01F || Math.floorMod(tick, 12L) != 0L) {
            return 0;
        }
        return amount >= 0.75F ? 2 : 1;
    }
}
