package com.thunder.wildernessodysseyapi.temporalrift.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class TemporalRiftRenderTypes extends RenderStateShard {
    private static final ShaderStateShard RIFT_SHADER = new ShaderStateShard(TemporalRiftShaders::getRiftShader);
    private static final RenderType RIFT = RenderType.create(
            "wildernessodysseyapi_temporal_rift",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RIFT_SHADER)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)
    );

    private TemporalRiftRenderTypes(String name, Runnable setupState, Runnable clearState) {
        super(name, setupState, clearState);
    }

    public static RenderType rift() {
        return RIFT;
    }
}
