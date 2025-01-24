package com.thunder.wildernessodysseyapi.ModConflictChecker;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

@EventBusSubscriber
public class EntityEventHandler {
    public static void register() {
        LOGGER.info("EntityEventHandler registered.");
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        event.getEntity();

        EntityType<?> entityType = event.getEntity().getType();
        ResourceLocation entityKey = entityType.getRegistryName();
        if (entityKey != null) {
            String modSource = entityKey.getNamespace();
            LOGGER.info("Entity '{}' spawned in the world by '{}'", entityKey, modSource);
        }
    }
}