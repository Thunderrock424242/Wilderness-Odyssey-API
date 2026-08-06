package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Defines the depth-tested translucent pass used by density-raymarched clouds. */
public final class RaymarchedCloudRenderTypes extends RenderStateShard {

    private static final ShaderStateShard CLOUD_SHADER =
            new ShaderStateShard(RaymarchedCloudShaders::getShader);
    private static final RenderType RAYMARCHED_CLOUDS = RenderType.create(
            "wildernessodysseyapi_raymarched_clouds",
            DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
            VertexFormat.Mode.QUADS,
            524_288,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(CLOUD_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)
    );

    private RaymarchedCloudRenderTypes(String name, Runnable setupState, Runnable clearState) {
        super(name, setupState, clearState);
    }

    /** Returns the high-quality cloud pass. */
    public static RenderType raymarchedClouds() {
        return RAYMARCHED_CLOUDS;
    }
}
