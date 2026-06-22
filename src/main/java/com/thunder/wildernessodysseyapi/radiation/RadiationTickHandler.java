package com.thunder.wildernessodysseyapi.radiation;

import com.thunder.wildernessodysseyapi.meteor.worldgen.MeteorSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

import static com.thunder.wildernessodysseyapi.core.ModRegistries.RADIATION_EFFECT;

/**
 * Applies server-authoritative radiation effects near recorded meteor craters.
 *
 * <p>The handler samples players once per second instead of every tick, keeping
 * distance checks bounded while refreshing a five-second effect.</p>
 */
public final class RadiationTickHandler {

    /** Radiation zone radius relative to the crater bowl radius. */
    private static final double RADIATION_ZONE_MULTIPLIER = 1.5;
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
            MeteorSavedData data = MeteorSavedData.get(level);
            List<MeteorSavedData.MeteorRecord> meteors = data.getMeteors();
            if (meteors.isEmpty()) {
                continue;
            }

            for (ServerPlayer player : level.players()) {
                Vec3 playerPosition = player.position();
                int strongestAmplifier = -1;

                for (MeteorSavedData.MeteorRecord meteor : meteors) {
                    double radiationRadius = meteor.craterRadius() * RADIATION_ZONE_MULTIPLIER;
                    double radiationRadiusSquared = radiationRadius * radiationRadius;

                    double deltaX = playerPosition.x - meteor.center().getX();
                    double deltaZ = playerPosition.z - meteor.center().getZ();
                    // Horizontal distance models the radiation zone as a cylinder around the crater.
                    double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;

                    if (distanceSquared <= radiationRadiusSquared) {
                        int amplifier = RadiationEffect.getAmplifierForDistance(distanceSquared, radiationRadius);
                        strongestAmplifier = Math.max(strongestAmplifier, amplifier);
                    }
                }

                if (strongestAmplifier >= 0) {
                    player.addEffect(new MobEffectInstance(
                            RADIATION_EFFECT,
                            EFFECT_DURATION,
                            strongestAmplifier,
                            false,
                            true,
                            true
                    ));
                }
            }
        }
    }
}
