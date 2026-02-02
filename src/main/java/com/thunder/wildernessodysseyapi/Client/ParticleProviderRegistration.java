package com.thunder.wildernessodysseyapi.Client;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.ModPackPatches.HigherSmoke.Particles.CustomCampfireParticle;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ParticleProviderRegistration {
    private ParticleProviderRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ParticleTypes.CAMPFIRE_COSY_SMOKE, CustomCampfireParticle.Factory::new);
        event.registerSpriteSet(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, CustomCampfireParticle.Factory::new);
    }
}
