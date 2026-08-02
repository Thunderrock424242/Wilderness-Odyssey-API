package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterVolumeSnapshots;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;

/**
 * ClientTickHandler
 *
 * Advances lightweight client-only water animation state. Remote authoritative
 * SPH mirrors interpolate server snapshots, while local visual SPH effects
 * tick only on the client from compact event payloads.
 */
@EventBusSubscriber(modid = "wildernessodysseyapi", value = Dist.CLIENT)
public class ClientTickHandler {

    private static final float CLIENT_TICK_DELTA = 0.05f;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        GerstnerWaveAnimator.update();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            if (!WildernessWaterRules.isEnabled(mc.level)) {
                SPHSimulationManager.get().clearLevel(mc.level);
                ClientWaterVolumeSnapshots.clear(mc.level);
                return;
            }
            ClientOceanSeaState.tick(mc.level);
            ClientWaterVolumeSnapshots.tick(mc.level);
            SPHSimulationManager.get().tickLevel(mc.level, CLIENT_TICK_DELTA);
            WaterAmbientEffects.tick(mc);
        }
    }

    /** Clears remote mirrors and classifications when the client leaves a world. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            SPHSimulationManager.get().clearLevel(level);
            ClientOceanSeaState.clear(level);
            ClientWaterVolumeSnapshots.clear(level);
            ClientWaterImmersion.clear(level);
            OceanSurfaceRenderer.clearLevel(level);
            WaterBodyClassifier.clearCache(level);
        }
    }
}
