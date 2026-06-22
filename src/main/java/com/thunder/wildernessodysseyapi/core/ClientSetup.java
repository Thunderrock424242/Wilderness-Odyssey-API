package com.thunder.wildernessodysseyapi.core;

import com.thunder.wildernessodysseyapi.entity.client.RiftListenerRenderer;
import com.thunder.wildernessodysseyapi.entity.client.RiftMawRenderer;
import com.thunder.wildernessodysseyapi.entity.client.RiftboundWraithRenderer;
import com.thunder.wildernessodysseyapi.meteor.renderer.MeteorRenderer;
import com.thunder.wildernessodysseyapi.temporalrift.client.RiftCoreBlockEntityRenderer;
import com.thunder.wildernessodysseyapi.temporalrift.client.TemporalRiftShaders;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlockEntities;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterShaders;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFT_LISTENER.get(), RiftListenerRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFT_MAW.get(), RiftMawRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFTBOUND_WRAITH.get(), RiftboundWraithRenderer::new);
        event.registerBlockEntityRenderer(TemporalRiftBlockEntities.RIFT_CORE.get(), RiftCoreBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            TemporalRiftShaders.register(event);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load temporal rift shader", exception);
        }

        try {
            WaterShaders.register(event);
        } catch (IOException exception) {
            // Enhanced water optics are optional. Keeping registration failure
            // non-fatal preserves the standard translucent fallback path.
            ModConstants.LOGGER.warn("Unable to load built-in water shader; using compatibility rendering", exception);
        }
    }
}
