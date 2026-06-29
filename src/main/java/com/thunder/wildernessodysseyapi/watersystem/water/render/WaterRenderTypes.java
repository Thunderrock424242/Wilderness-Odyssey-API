package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Defines the shader-enhanced translucent pass for the dynamic ocean mesh. */
public final class WaterRenderTypes extends RenderStateShard {

    private static final ShaderStateShard OCEAN_SHADER =
            new ShaderStateShard(WaterShaders::getOceanShader);

    private static final RenderType DYNAMIC_OCEAN = RenderType.create(
            "wildernessodysseyapi_dynamic_ocean",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            786_432,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(OCEAN_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    // This pass is transparent and is drawn after vanilla
                    // translucent water. Writing depth lets one ocean quad hide
                    // another, which shows up as dark triangular shoreline
                    // shards when the camera looks through ice or shallow water.
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)
    );

    private WaterRenderTypes(String name, Runnable setupState, Runnable clearState) {
        super(name, setupState, clearState);
    }

    /** Returns the built-in shader ocean pass. */
    public static RenderType dynamicOcean() {
        return DYNAMIC_OCEAN;
    }
}
