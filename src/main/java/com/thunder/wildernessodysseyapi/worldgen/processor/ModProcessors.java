package com.thunder.wildernessodysseyapi.worldgen.processor;

import com.thunder.wildernessodysseyapi.core.ModConstants;
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
     * Processor that prevents bunker placement from clearing non-dirt blocks when air is placed.
     */
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<BunkerPlacementProcessor>> BUNKER_PLACEMENT =
            PROCESSORS.register("bunker_placement", () -> () -> BunkerPlacementProcessor.CODEC);

    /** Final validator that discards NBT incompatible with a processed block state. */
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<BlockEntityNbtSanitizingProcessor>> BLOCK_ENTITY_NBT_SANITIZER =
            PROCESSORS.register("block_entity_nbt_sanitizer", () -> () -> BlockEntityNbtSanitizingProcessor.CODEC);

    private ModProcessors() {
    }
}
