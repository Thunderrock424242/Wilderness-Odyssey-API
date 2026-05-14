package com.thunder.wildernessodysseyapi.core;

import com.thunder.wildernessodysseyapi.entity.RiftbornEntity;
import com.thunder.wildernessodysseyapi.entity.RiftListenerEntity;
import com.thunder.wildernessodysseyapi.entity.RiftMawEntity;
import com.thunder.wildernessodysseyapi.meteor.entity.MeteorEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ModConstants.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR =
            ENTITY_TYPES.register("meteor", () ->
                    EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                            .sized(1.5f, 1.5f)        // hitbox
                            .clientTrackingRange(128)  // visible from far away
                            .updateInterval(1)         // update every tick for smooth movement
                            .build("meteor")
            );

    public static final DeferredHolder<EntityType<?>, EntityType<RiftbornEntity>> RIFTBORN =
            ENTITY_TYPES.register("riftborn",
                    () -> EntityType.Builder.of(RiftbornEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("riftborn"));

    public static final DeferredHolder<EntityType<?>, EntityType<RiftListenerEntity>> RIFT_LISTENER =
            ENTITY_TYPES.register("rift_listener",
                    () -> EntityType.Builder.of(RiftListenerEntity::new, MobCategory.MONSTER)
                            .sized(0.9F, 3.2F)
                            .clientTrackingRange(12)
                            .fireImmune()
                            .build("rift_listener"));

    public static final DeferredHolder<EntityType<?>, EntityType<RiftMawEntity>> RIFT_MAW =
            ENTITY_TYPES.register("rift_maw",
                    () -> EntityType.Builder.of(RiftMawEntity::new, MobCategory.MONSTER)
                            .sized(2.2F, 3.8F)
                            .clientTrackingRange(12)
                            .fireImmune()
                            .build("rift_maw"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

    @EventBusSubscriber(modid = ModConstants.MOD_ID)
    public static final class ModEntityEvents {
        private ModEntityEvents() {
        }

        @SubscribeEvent
        public static void onAttributeCreate(EntityAttributeCreationEvent event) {
            event.put(RIFTBORN.get(), RiftbornEntity.createAttributes().build());
            event.put(RIFT_LISTENER.get(), RiftListenerEntity.createAttributes().build());
            event.put(RIFT_MAW.get(), RiftMawEntity.createAttributes().build());
        }

        @SubscribeEvent
        public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
            event.register(
                    RIFTBORN.get(),
                    SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    RiftbornEntity::checkRiftbornSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE
            );
            event.register(
                    RIFT_LISTENER.get(),
                    SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    RiftListenerEntity::checkRiftListenerSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE
            );
        }
    }
}
