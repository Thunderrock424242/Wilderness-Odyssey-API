package com.thunder.wildernessodysseyapi.blocks;

import com.thunder.wildernessodysseyapi.ocean.CustomWaterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    // Create a DeferredRegister for blocks
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "mymod");

    // Register your custom water block
    public static final RegistryObject<Block> CUSTOM_WATER = BLOCKS.register("custom_water",
            () -> new CustomWaterBlock(BlockBehaviour.Properties.of(Material.WATER).noCollission().strength(1.0F)));

    // Initialization method removed as it's unnecessary
}
