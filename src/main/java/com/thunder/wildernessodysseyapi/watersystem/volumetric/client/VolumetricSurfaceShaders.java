package com.thunder.wildernessodysseyapi.watersystem.volumetric.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * Registers custom volumetric fluid shaders.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VolumetricSurfaceShaders {
    public static final ResourceLocation WATER_SHADER = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "core/volumetric_surface_water");
    public static final ResourceLocation LAVA_SHADER = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "core/volumetric_surface_lava");

    private static ShaderInstance waterShader;
    private static ShaderInstance lavaShader;

    private VolumetricSurfaceShaders() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), WATER_SHADER, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                shader -> waterShader = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), LAVA_SHADER, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                shader -> lavaShader = shader);
    }

    public static ShaderInstance waterShader() {
        return waterShader;
    }

    public static ShaderInstance lavaShader() {
        return lavaShader;
    }
}
