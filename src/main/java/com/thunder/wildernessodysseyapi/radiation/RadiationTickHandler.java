package com.thunder.wildernessodysseyapi.radiation;

import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteServices;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.thunder.wildernessodysseyapi.core.ModRegistries.RADIATION_EFFECT;

/**
 * Applies server-authoritative radiation effects near recorded meteor craters.
 *
 * <p>The handler samples players once per second instead of every tick, keeping
 * distance checks bounded while refreshing a five-second effect.</p>
 */
public final class RadiationTickHandler {

    /** Duration applied on each refresh, in ticks. */
    private static final int EFFECT_DURATION = 100;
    /** Sampling interval, in server ticks. */
    private static final int CHECK_INTERVAL = 20;

    private RadiationTickHandler() {
    }

    /** Registers the runtime handler on NeoForge's game event bus. */
    public static void register() {
        NeoForge.EVENT_BUS.register(new RadiationTickHandler());
    }

    /**
     * Refreshes player radiation effects after each configured tick interval.
     *
     * @param event the post-server-tick event containing the authoritative server
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                double radiation = MeteorSiteServices.radiationAt(level, player.blockPosition());
                if (radiation > 0.0) {
                    player.addEffect(new MobEffectInstance(
                            RADIATION_EFFECT,
                            EFFECT_DURATION,
                            RadiationEffect.getAmplifierForExposure(radiation),
                            false,
                            true,
                            true
                    ));
                }
            }
        }
    }
}
