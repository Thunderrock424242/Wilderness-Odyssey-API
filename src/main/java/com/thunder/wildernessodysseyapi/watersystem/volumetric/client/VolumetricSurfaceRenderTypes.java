package com.thunder.wildernessodysseyapi.watersystem.volumetric.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom render types for volumetric preview surfaces.
 */
public final class VolumetricSurfaceRenderTypes {
    private static final RenderType WATER_SURFACE = RenderType.create(
            "wildernessodysseyapi_volumetric_water",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.TRIANGLES,
            262_144,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> {
                        if (VolumetricSurfaceShaders.waterShader() != null) {
                            return VolumetricSurfaceShaders.waterShader();
                        }
                        return GameRenderer.getPositionColorTexLightmapShader();
                    }))
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(RenderType.LIGHTMAP)
                    .setCullState(RenderType.NO_CULL)
                    .createCompositeState(true)
    );

    private static final RenderType LAVA_SURFACE = RenderType.create(
            "wildernessodysseyapi_volumetric_lava",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.TRIANGLES,
            262_144,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> {
                        if (VolumetricSurfaceShaders.lavaShader() != null) {
                            return VolumetricSurfaceShaders.lavaShader();
                        }
                        return GameRenderer.getPositionColorTexLightmapShader();
                    }))
                    .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(RenderType.LIGHTMAP)
                    .setCullState(RenderType.NO_CULL)
                    .createCompositeState(true)
    );

    private VolumetricSurfaceRenderTypes() {
    }

    public static RenderType waterSurface() {
        return WATER_SURFACE;
    }

    public static RenderType lavaSurface() {
        return LAVA_SURFACE;
    }
}
