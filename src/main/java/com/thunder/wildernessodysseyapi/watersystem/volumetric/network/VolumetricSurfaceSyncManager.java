package com.thunder.wildernessodysseyapi.watersystem.volumetric.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager.SimulatedFluid;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager.SurfaceSample;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Periodically ships compact surface samples to clients for mesh rendering.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class VolumetricSurfaceSyncManager {
    private static final int SYNC_INTERVAL_TICKS = 4;
    private static final int SAMPLE_RADIUS = 40;
    private static final int MAX_SAMPLES_PER_FLUID = 2048;

    private VolumetricSurfaceSyncManager() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!event.hasTime()) {
            return;
        }

        long gameTime = event.getServer().getTickCount();
        if (gameTime % SYNC_INTERVAL_TICKS != 0L) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                BlockPos center = player.blockPosition();
                var water = VolumetricFluidManager.sampleSurface(level, SimulatedFluid.WATER, center, SAMPLE_RADIUS, MAX_SAMPLES_PER_FLUID);
                var lava = VolumetricFluidManager.isLavaSimulationEnabled()
                        ? VolumetricFluidManager.sampleSurface(level, SimulatedFluid.LAVA, center, SAMPLE_RADIUS, MAX_SAMPLES_PER_FLUID)
                        : List.<SurfaceSample>of();
                if (water.isEmpty() && lava.isEmpty()) {
                    continue;
                }
                PacketDistributor.sendToPlayer(player, new VolumetricSurfaceSyncPayload(level.dimension().location(), gameTime, water, lava));
            }
        }
    }
}
