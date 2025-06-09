package com.thunder.wildernessodysseyapi.ocean.rendering;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;

public class SmoothFluidRenderType {
    public static final RenderType SMOOTH_WATER = RenderType.create(
            "smooth_water",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                   // .setShaderState(new RenderStateShard.ShaderStateShard(() -> SmoothFluidShaders::getShader))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .createCompositeState(true)
    );
}
