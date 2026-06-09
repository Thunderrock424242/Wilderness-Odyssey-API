package com.thunder.wildernessodysseyapi.temporalrift.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class EchoClientEffects {
    private EchoClientEffects() {
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!isEcho()) {
            return;
        }

        event.setRed(0.035F);
        event.setGreen(0.045F);
        event.setBlue(0.07F);
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (!isEcho()) {
            return;
        }

        SoundInstance sound = event.getSound();
        if (sound == null || isAllowedEchoSound(sound.getSource())) {
            return;
        }

        event.setSound(null);
    }

    private static boolean isEcho() {
        Level level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY);
    }

    private static boolean isAllowedEchoSound(SoundSource source) {
        return source == SoundSource.PLAYERS || source == SoundSource.NEUTRAL;
    }
}
