package com.thunder.wildernessodysseyapi.WorldGen.blocks;

import com.thunder.wildernessodysseyapi.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Block representing a cryo tube spawn point.
 */
public class CryoTubeBlock {
    /**
     * Registry for blocks.
     */
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MOD_ID);

    /**
     * The cryo tube block instance.
     */
    public static final DeferredBlock<Block> CRYO_TUBE_BLOCK = registerBlock(
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .sound(SoundType.METAL)));

    private static <T extends Block> DeferredBlock<T> registerBlock(Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register("cryo_tube_block", block);
        registerBlockItem(toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(DeferredBlock<T> block) {
        ModItems.ITEMS.register("cryo_tube_block", () -> new BlockItem(block.get(), new Item.Properties()));
    }

    /**
     * Register the block with the given event bus.
     */
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
