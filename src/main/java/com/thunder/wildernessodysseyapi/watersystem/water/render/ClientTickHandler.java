package com.thunder.wildernessodysseyapi.watersystem.water.render;

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
 * Advances lightweight client-only water animation state. SPH simulation is
 * driven by the logical server tick because it queries world collision data.
 */
@EventBusSubscriber(modid = "wildernessodysseyapi", value = Dist.CLIENT)
public class ClientTickHandler {

    private static final float CLIENT_TICK_DELTA = 0.05f;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        GerstnerWaveAnimator.update();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            SPHSimulationManager.get().tickLevel(mc.level, CLIENT_TICK_DELTA);
        }
    }

    /** Clears remote mirrors and classifications when the client leaves a world. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            SPHSimulationManager.get().clearLevel(level);
            WaterBodyClassifier.clearCache(level);
        }
    }
}
