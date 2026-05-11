package com.thunder.wildernessodysseyapi.temporalrift.registry;

import com.thunder.wildernessodysseyapi.temporalrift.blockentity.AncientTimeCapsuleBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.TimeCapsuleBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

public final class TemporalRiftBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftCoreBlockEntity>> RIFT_CORE =
            BLOCK_ENTITIES.register(
                    "rift_core",
                    () -> BlockEntityType.Builder.of(RiftCoreBlockEntity::new, TemporalRiftBlocks.RIFT_CORE.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TimeCapsuleBlockEntity>> TIME_CAPSULE =
            BLOCK_ENTITIES.register(
                    "time_capsule",
                    () -> BlockEntityType.Builder.of(TimeCapsuleBlockEntity::new, TemporalRiftBlocks.TIME_CAPSULE.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AncientTimeCapsuleBlockEntity>> ANCIENT_TIME_CAPSULE =
            BLOCK_ENTITIES.register(
                    "ancient_time_capsule",
                    () -> BlockEntityType.Builder.of(AncientTimeCapsuleBlockEntity::new, TemporalRiftBlocks.ANCIENT_TIME_CAPSULE.get()).build(null)
            );

    private TemporalRiftBlockEntities() {
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
