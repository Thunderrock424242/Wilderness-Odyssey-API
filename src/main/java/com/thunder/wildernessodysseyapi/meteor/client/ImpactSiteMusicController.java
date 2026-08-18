package com.thunder.wildernessodysseyapi.meteor.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.client.ClientEnvironmentState;
import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class ImpactSiteMusicController {
    private static final int EFFECT_RADIUS = 100;

    private static ImpactSiteMusicSoundInstance activeMusic;

    private ImpactSiteMusicController() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || minecraft.isPaused()) {
            fadeOutAndCleanup(minecraft);
            return;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        if (!isInAnomalyBiome(level, playerPos)) {
            fadeOutAndCleanup(minecraft);
            return;
        }

        float targetVolume = 0.0F;
        var environment = ClientEnvironmentState.current(level);
        if (environment.isPresent() && environment.get().meteorPresent()) {
            BlockPos impactCenter = new BlockPos(
                    environment.get().meteorX(),
                    environment.get().meteorY(),
                    environment.get().meteorZ()
            );
            double distance = Math.sqrt(impactCenter.distSqr(playerPos));
            if (distance <= EFFECT_RADIUS) {
                targetVolume = (float) (1.0D - (distance / EFFECT_RADIUS));
            }
        }

        if (targetVolume > 0.001F) {
            ensureMusicStarted(minecraft);
        }

        if (activeMusic != null) {
            activeMusic.setTargetVolume(targetVolume);
            if (activeMusic.isStopped()) {
                activeMusic = null;
            }
        }
    }

    private static void ensureMusicStarted(Minecraft minecraft) {
        if (activeMusic != null && minecraft.getSoundManager().isActive(activeMusic)) {
            return;
        }

        activeMusic = new ImpactSiteMusicSoundInstance();
        minecraft.getSoundManager().play(activeMusic);
    }

    private static void fadeOutAndCleanup(Minecraft minecraft) {
        if (activeMusic == null) {
            return;
        }

        activeMusic.setTargetVolume(0.0F);
        if (!minecraft.getSoundManager().isActive(activeMusic) || activeMusic.isStopped()) {
            activeMusic = null;
        }
    }

    private static boolean isInAnomalyBiome(ClientLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(ModBiomes.ANOMALY_FOREST_KEY);
    }
}
