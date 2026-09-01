package com.thunder.wildernessodysseyapi.environment.glacial.client;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonSnapshot;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialWaterColorModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

/** Client-only adapter applying synchronized season state to every water rendering path. */
public final class GlacialWaterTintManager {

    private GlacialWaterTintManager() {
    }

    /** Returns a seasonal surface tint in glacial biomes and the input elsewhere. */
    public static int surfaceTint(BlockAndTintGetter getter, BlockPos position, int baseTint) {
        if (!(getter instanceof ClientLevel level)) {
            return baseTint;
        }
        return GlacialBiomeManager.environmentalFamily(level.getBiome(position))
                .map(family -> GlacialWaterColorModel.surfaceTint(
                        family,
                        baseTint,
                        ClientGlacialState.snapshot(level).meltFraction()))
                .orElse(baseTint);
    }

    /** Returns a deeper seasonal color for the custom underwater optics model. */
    public static int underwaterTint(ClientLevel level, BlockPos position, int baseTint) {
        GlacialSeasonSnapshot season = ClientGlacialState.snapshot(level);
        return GlacialBiomeManager.environmentalFamily(level.getBiome(position))
                .map(family -> GlacialWaterColorModel.underwaterTint(
                        family, baseTint, season.meltFraction()))
                .orElse(baseTint);
    }

    /** Returns whether glacial clarity and cobalt optics apply at this loaded position. */
    public static boolean isGlacial(ClientLevel level, BlockPos position) {
        return GlacialBiomeManager.isGlacial(level.getBiome(position));
    }
}
