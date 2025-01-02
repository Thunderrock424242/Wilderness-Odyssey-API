package com.thunder.wildernessodysseyapi.block;

import com.thunder.wildernessodysseyapi.ocean.CustomWaterBlock;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "mymod");

    public static final DeferredHolder<Block, CustomWaterBlock> CUSTOM_WATER = BLOCKS.register("custom_water",
            CustomWaterBlock::new);
}