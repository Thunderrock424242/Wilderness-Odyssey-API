package com.thunder.wildernessodysseyapi.core;

import com.thunder.wildernessodysseyapi.meteor.renderer.MeteorRenderer;
import com.thunder.wildernessodysseyapi.watersystem.water.particle.RippleParticleProvider;
import com.thunder.wildernessodysseyapi.watersystem.water.particle.WildernessParticleRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterShaderManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        WaterShaderManager.registerShaders(event);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WildernessParticleRegistry.RIPPLE.get(), RippleParticleProvider::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.METEOR.get(), MeteorRenderer::new);
    }
}
