package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Defines the soft translucent pass used by layered volumetric clouds. */
public final class VolumetricCloudRenderTypes extends RenderStateShard {

    private static final ShaderStateShard CLOUD_SHADER =
            new ShaderStateShard(VolumetricCloudShaders::getShader);

    private static final RenderType VOLUMETRIC_CLOUDS = RenderType.create(
            "wildernessodysseyapi_volumetric_clouds",
            DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL,
            VertexFormat.Mode.QUADS,
            1_048_576,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(CLOUD_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)
    );

    private VolumetricCloudRenderTypes(String name, Runnable setupState, Runnable clearState) {
        super(name, setupState, clearState);
    }

    /** Returns the procedural layered-cloud render pass. */
    public static RenderType volumetricClouds() {
        return VOLUMETRIC_CLOUDS;
    }
}
