package com.thunder.wildernessodysseyapi.temporalrift.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
/** Familiar regional ambience that becomes more distorted with synchronized server instability. */
public final class EchoClientEffects {
    private EchoClientEffects() {
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!isEcho()) {
            return;
        }

        float amount = ClientEchoState.atmosphere() * 0.85F;
        event.setRed(event.getRed() * (1 - amount) + 0.035F * amount);
        event.setGreen(event.getGreen() * (1 - amount) + 0.045F * amount);
        event.setBlue(event.getBlue() * (1 - amount) + 0.07F * amount);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isEcho() || minecraft.player == null || minecraft.isPaused()) return;
        Level level = minecraft.level;
        float amount = ClientEchoState.atmosphere();
        if (amount >= 0.45F && level.getGameTime() % 20 == 0) {
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.ASH,
                        minecraft.player.getX() + level.random.nextDouble() * 12 - 6,
                        minecraft.player.getY() + level.random.nextDouble() * 5,
                        minecraft.player.getZ() + level.random.nextDouble() * 12 - 6,
                        0, 0.015, 0);
            }
        }
        if (amount >= 0.3F && level.getGameTime() % 1200 == 0) {
            level.playLocalSound(minecraft.player.getX() + 12, minecraft.player.getY(), minecraft.player.getZ() - 12,
                    SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.15F, 0.65F, false);
        }
    }

    private static boolean isEcho() {
        Level level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY);
    }

    /** Attenuates existing sound instances without changing identity, looping, streaming or stop behavior. */
    public static float soundMultiplier(SoundSource source) {
        if (!isEcho() || source == SoundSource.PLAYERS || source == SoundSource.MASTER
                || source == SoundSource.VOICE) return 1;
        float suppression = source == SoundSource.NEUTRAL ? 0.7F : 0.92F;
        return 1 - suppression * ClientEchoState.atmosphere();
    }
}
