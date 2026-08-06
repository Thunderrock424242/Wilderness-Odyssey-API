package com.thunder.wildernessodysseyapi.weather.client.precipitation;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Render state for untextured, depth-tested precipitation impact rings. */
public final class WeatherImpactRenderTypes extends RenderStateShard {

    private static final ShaderStateShard POSITION_COLOR =
            new ShaderStateShard(GameRenderer::getPositionColorShader);
    private static final RenderType IMPACTS = RenderType.create(
            "wildernessodysseyapi_precipitation_impacts",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            65_536,
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

    private WeatherImpactRenderTypes(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }

    /** Returns the shared pass for all procedural weather impacts. */
    public static RenderType impacts() {
        return IMPACTS;
    }
}
