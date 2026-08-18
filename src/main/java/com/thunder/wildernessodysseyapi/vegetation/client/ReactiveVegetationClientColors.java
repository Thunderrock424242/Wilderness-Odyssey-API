package com.thunder.wildernessodysseyapi.vegetation.client;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Registers client-only drought tinting for the vanilla grass family. */
public final class ReactiveVegetationClientColors {

    private ReactiveVegetationClientColors() {
    }

    /**
     * Layers drought over Minecraft's biome grass color without replacing blocks.
     *
     * <p>Season mods that alter the biome color remain the base color source;
     * this handler contributes only the retained Wilderness drought amount.</p>
     */
    public static void register(RegisterColorHandlersEvent.Block event) {
        event.register((state, getter, position, tintIndex) -> {
            if (getter == null || position == null) {
                return GrassColor.getDefaultColor();
            }
            int biomeColor = BiomeColors.getAverageGrassColor(getter, position);
            VegetationClimateState climate = ClientVegetationClimateStore.stateAtOrNull(position);
            return climate == null
                    ? biomeColor
                    : VegetationColorModel.applyDrought(biomeColor, climate);
        },
                Blocks.GRASS_BLOCK,
                Blocks.SHORT_GRASS,
                Blocks.TALL_GRASS,
                Blocks.FERN,
                Blocks.LARGE_FERN
        );
    }
}
