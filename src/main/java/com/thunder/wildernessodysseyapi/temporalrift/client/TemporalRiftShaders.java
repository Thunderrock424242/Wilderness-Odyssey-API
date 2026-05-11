package com.thunder.wildernessodysseyapi.temporalrift.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

public final class TemporalRiftShaders {
    private static ShaderInstance riftShader;

    private TemporalRiftShaders() {
    }

    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "temporal_rift"),
                        DefaultVertexFormat.POSITION_COLOR_NORMAL
                ),
                shader -> riftShader = shader
        );
    }

    public static ShaderInstance getRiftShader() {
        return riftShader != null ? riftShader : GameRenderer.getPositionColorShader();
    }
}
