package com.thunder.wildernessodysseyapi.cryo.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Depth-tested translucent pass for temporary cryo-cinematic fluid and diagnostics. */
final class CryoTubeRenderTypes extends RenderStateShard {
    private static final ShaderStateShard POSITION_COLOR = new ShaderStateShard(GameRenderer::getPositionColorShader);
    private static final RenderType CINEMATIC = RenderType.create(
            "wildernessodysseyapi_cryo_cinematic",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2_048,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)
    );

    private CryoTubeRenderTypes(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }

    static RenderType cinematic() {
        return CINEMATIC;
    }
}
