package com.thunder.wildernessodysseyapi.WorldGen.processor;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry holder for structure processors used by datapack-driven structures.
 */
public final class ModProcessors {
    /** Structure processor registry. */
    public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, ModConstants.MOD_ID);

    /**
     * Processor that replaces terrain survey markers with sampled surface blocks and strips wool markers.
     */
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<TerrainSurveyProcessor>> TERRAIN_SURVEY =
            PROCESSORS.register("terrain_survey", () -> () -> TerrainSurveyProcessor.CODEC);
    /**
     * Processor that prevents bunker placement from clearing non-dirt blocks when air is placed.
     */
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<BunkerPlacementProcessor>> BUNKER_PLACEMENT =
            PROCESSORS.register("bunker_placement", () -> () -> BunkerPlacementProcessor.CODEC);

    private ModProcessors() {
    }
}
