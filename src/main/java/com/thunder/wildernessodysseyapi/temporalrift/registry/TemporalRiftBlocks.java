package com.thunder.wildernessodysseyapi.temporalrift.registry;

import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.temporalrift.block.AncientTimeCapsuleBlock;
import com.thunder.wildernessodysseyapi.temporalrift.block.RiftCoreBlock;
import com.thunder.wildernessodysseyapi.temporalrift.block.TimeCapsuleBlock;
import com.thunder.wildernessodysseyapi.temporalrift.item.TimeCapsuleBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

public final class TemporalRiftBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);

    public static final DeferredBlock<RiftCoreBlock> RIFT_CORE = BLOCKS.register(
            "rift_core",
            () -> new RiftCoreBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()
                    .strength(-1.0F, 3_600_000.0F)
                    .lightLevel(state -> 11)
                    .sound(SoundType.GLASS))
    );

    public static final DeferredBlock<TimeCapsuleBlock> TIME_CAPSULE = BLOCKS.register(
            "time_capsule",
            () -> new TimeCapsuleBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 3))
    );

    public static final DeferredBlock<AncientTimeCapsuleBlock> ANCIENT_TIME_CAPSULE = BLOCKS.register(
            "ancient_time_capsule",
            () -> new AncientTimeCapsuleBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 8.0F)
                    .sound(SoundType.ANCIENT_DEBRIS)
                    .lightLevel(state -> 5))
    );

    static {
        ModItems.ITEMS.register("time_capsule", () -> new TimeCapsuleBlockItem(TIME_CAPSULE.get(), new Item.Properties()));
    }

    private TemporalRiftBlocks() {
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
