package com.thunder.wildernessodysseyapi.weather.client.surface;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Render state for untextured cosmetic wet-ground patches. */
public final class WeatherSurfaceRenderTypes extends RenderStateShard {
    private static final ShaderStateShard POSITION_COLOR = new ShaderStateShard(GameRenderer::getPositionColorShader);
    private static final RenderType WET_SURFACE = RenderType.create(
            "wildernessodysseyapi_wet_surface",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1_024,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(TRANSLUCENT_TARGET)
                    .createCompositeState(false)
    );

    private WeatherSurfaceRenderTypes(String name, Runnable setup, Runnable clear) {
        super(name, setup, clear);
    }

    /** Returns the shared translucent position-color type. */
    public static RenderType wetSurface() {
        return WET_SURFACE;
    }
}
