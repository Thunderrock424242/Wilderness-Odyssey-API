package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRendererSectionCoordinates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes legacy Embeddium section coordinates without linking the renderer. */
@Pseudo
@Mixin(
        targets = "org.embeddedt.embeddium.impl.render.chunk.RenderSection",
        remap = false
)
public abstract class EmbeddiumRenderSectionCoordinatesMixin implements WaterRendererSectionCoordinates {

    @Shadow(remap = false)
    public abstract int getChunkX();

    @Shadow(remap = false)
    public abstract int getChunkY();

    @Shadow(remap = false)
    public abstract int getChunkZ();

    @Override
    public int wildernessOdysseyApi$sectionX() {
        return getChunkX();
    }

    @Override
    public int wildernessOdysseyApi$sectionY() {
        return getChunkY();
    }

    @Override
    public int wildernessOdysseyApi$sectionZ() {
        return getChunkZ();
    }
}
