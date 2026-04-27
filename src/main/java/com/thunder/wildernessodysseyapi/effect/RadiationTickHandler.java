package com.thunder.wildernessodysseyapi.effect;

import com.thunder.wildernessodysseyapi.worldgen.meteor.MeteorSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

import static com.thunder.wildernessodysseyapi.core.WildernessOdysseyAPIMainModClass.RADIATION_EFFECT;

public class RadiationTickHandler {

    /** Radiation zone = 1.5× the crater's bowl radius */
    private static final double RADIATION_ZONE_MULTIPLIER = 1.5;
    /** Duration to apply/refresh the effect each check cycle (ticks) */
    private static final int EFFECT_DURATION = 100; // 5 seconds
    /** How often to check, in ticks */
    private static final int CHECK_INTERVAL = 20;

    public static void register() {
        NeoForge.EVENT_BUS.register(new RadiationTickHandler());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            MeteorSavedData data = MeteorSavedData.get(level);
            List<MeteorSavedData.MeteorRecord> meteors = data.getMeteors();
            if (meteors.isEmpty()) continue;

            for (ServerPlayer player : level.players()) {
                Vec3 playerPos = player.position();

                for (MeteorSavedData.MeteorRecord meteor : meteors) {
                    double radiationRadius   = meteor.craterRadius() * RADIATION_ZONE_MULTIPLIER;
                    double radiationRadiusSq = radiationRadius * radiationRadius;

                    double dx = playerPos.x - meteor.center().getX();
                    double dz = playerPos.z - meteor.center().getZ();
                    // Horizontal distance only — radiation spreads outward like a cylinder
                    double distSq = dx * dx + dz * dz;

                    if (distSq <= radiationRadiusSq) {
                        int amplifier = RadiationEffect.getAmplifierForDistance(distSq, radiationRadius);
                        player.addEffect(new MobEffectInstance(
                            RADIATION_EFFECT,
                            EFFECT_DURATION,
                            amplifier,
                            false, // not ambient
                            true,  // show particles
                            true   // show icon
                        ));
                    }
                }
            }
        }
    }
}
