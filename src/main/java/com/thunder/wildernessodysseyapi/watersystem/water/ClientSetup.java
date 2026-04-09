package com.thunder.wildernessodysseyapi.watersystem.water;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.particle.RippleParticleProvider;
import com.thunder.wildernessodysseyapi.watersystem.water.particle.WildernessParticleRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterShaderManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        WaterShaderManager.registerShaders(event);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WildernessParticleRegistry.RIPPLE.get(), RippleParticleProvider::new);
    }
}
