package com.thunder.wildernessodysseyapi.entity;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, ModConstants.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<PurpleStormMonsterEntity>> PURPLE_STORM_MONSTER =
            ENTITY_TYPES.register("purple_storm_monster",
                    () -> EntityType.Builder.of(PurpleStormMonsterEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("purple_storm_monster"));

    @EventBusSubscriber(modid = ModConstants.MOD_ID)
    public static final class ModEntityEvents {
        private ModEntityEvents() {
        }

        @SubscribeEvent
        public static void onAttributeCreate(EntityAttributeCreationEvent event) {
            event.put(PURPLE_STORM_MONSTER.get(), PurpleStormMonsterEntity.createAttributes().build());
        }

        @SubscribeEvent
        public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
            event.register(
                    PURPLE_STORM_MONSTER.get(),
                    SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    PurpleStormMonsterEntity::checkPurpleStormSpawnRules,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE
            );
        }
    }
}
