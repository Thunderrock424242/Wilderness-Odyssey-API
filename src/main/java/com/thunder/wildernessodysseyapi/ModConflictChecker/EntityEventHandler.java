package com.thunder.wildernessodysseyapi.ModConflictChecker;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber
public class EntityEventHandler {
    public static void register() {
        ModConflictChecker.LOGGER.info("EntityEventHandler registered.");
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() == null) return;

        EntityType<?> entityType = event.getEntity().getType();
        ResourceLocation entityKey = entityType.getRegistryName();
        if (entityKey != null) {
            String modSource = entityKey.getNamespace();
            ModConflictChecker.LOGGER.info("Entity '{}' spawned in the world by '{}'", entityKey, modSource);
        }
    }
}