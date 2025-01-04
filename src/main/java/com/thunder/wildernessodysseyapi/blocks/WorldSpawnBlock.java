package com.thunder.wildernessodysseyapi.blocks;

import com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass;
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

public class WorldSpawnBlock {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(WildernessOdysseyAPIMainModClass.MOD_ID);

    // Unbreakable Block
    public static final DeferredBlock<Block> WORLD_SPAWN_BLOCK = registerBlock(
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F) // Unbreakable and explosion-resistant
                    .noLootTable()               // Prevents any drops
                    .sound(SoundType.STONE)));

    private static <T extends Block> DeferredBlock<T> registerBlock(Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register("world_spawn_block", block);
        registerBlockItem(toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(DeferredBlock<T> block) {
        ModItems.ITEMS.register("world_spawn_block", () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
