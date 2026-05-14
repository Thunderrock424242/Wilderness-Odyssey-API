package com.thunder.wildernessodysseyapi.anomaly.registry;

import com.thunder.wildernessodysseyapi.anomaly.block.AnomalyPortalBlock;
import com.thunder.wildernessodysseyapi.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

public final class AnomalyBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);

    public static final DeferredBlock<Block> ANOMALY_ORE = BLOCKS.register(
            "anomaly_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .requiresCorrectToolForDrops()
                    .strength(4.5F, 9.0F)
                    .lightLevel(state -> 3)
                    .sound(SoundType.AMETHYST))
    );

    public static final DeferredBlock<AnomalyPortalBlock> ANOMALY_GATEWAY = BLOCKS.register(
            "anomaly_gateway",
            () -> new AnomalyPortalBlock(BlockBehaviour.Properties.of()
                    .requiresCorrectToolForDrops()
                    .strength(8.0F, 1_200.0F)
                    .lightLevel(state -> 12)
                    .noCollission()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST))
    );

    static {
        ModItems.ITEMS.register("anomaly_ore", () -> new BlockItem(ANOMALY_ORE.get(), new Item.Properties()));
        ModItems.ITEMS.register("anomaly_gateway", () -> new BlockItem(ANOMALY_GATEWAY.get(), new Item.Properties().stacksTo(16)));
    }

    private AnomalyBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
