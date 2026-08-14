package com.thunder.wildernessodysseyapi.developmentstudio.entity;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioServerService;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioWorldData;
import com.thunder.wildernessodysseyapi.developmentstudio.config.StudioConfig;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioEntityActionPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioResetPolicy;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegion;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegionRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Server-owned spawning and controls for tagged entities inside the registered Entity Lab. */
public final class StudioEntityService {
    public static final String TEST_ENTITY_TAG = "wildernessodysseyapi.studio_test_entity";

    private StudioEntityService() {
    }

    /** Reauthorizes and applies one operation only to Studio-tagged in-region entities. */
    public static void handle(ServerPlayer player, StudioEntityActionPayload payload) {
        if (!StudioServerService.authorize(player)) {
            return;
        }
        StudioWorldData data = StudioWorldData.getOrCreate(player.getServer());
        StudioTestRegion region = data.testRegion(StudioTestRegionRegistry.ENTITY_LAB).orElse(null);
        if (region == null || region.resetPolicy() != StudioResetPolicy.TAGGED_ENTITIES) {
            player.displayClientMessage(Component.literal("The registered Entity Lab is unavailable."), false);
            StudioServerService.openModule(player, "entities", null);
            return;
        }
        ResourceKey<Level> dimension = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, region.dimension()
        );
        ServerLevel level = player.getServer().getLevel(dimension);
        if (level == null) {
            player.displayClientMessage(Component.literal("The Entity Lab dimension is unavailable."), false);
            StudioServerService.openModule(player, "entities", null);
            return;
        }

        List<Entity> entities = taggedEntities(level, region);
        switch (payload.action()) {
            case SPAWN -> spawn(player, level, region, entities.size(), payload);
            case CLEAR -> entities.forEach(Entity::discard);
            case FREEZE -> entities.stream().filter(Mob.class::isInstance)
                    .map(Mob.class::cast).forEach(mob -> mob.setNoAi(true));
            case UNFREEZE -> entities.stream().filter(Mob.class::isInstance)
                    .map(Mob.class::cast).forEach(mob -> mob.setNoAi(false));
            case MAKE_INVULNERABLE -> entities.forEach(entity -> entity.setInvulnerable(true));
            case MAKE_VULNERABLE -> entities.forEach(entity -> entity.setInvulnerable(false));
        }
        StudioServerService.openModule(player, "entities", null);
    }

    public static int taggedEntityCount(ServerLevel level, StudioTestRegion region) {
        return taggedEntities(level, region).size();
    }

    /** Removes only Studio-owned tagged entities inside an explicitly supplied persisted region. */
    public static int discardTaggedEntities(ServerLevel level, StudioTestRegion region) {
        List<Entity> entities = taggedEntities(level, region);
        entities.forEach(Entity::discard);
        return entities.size();
    }

    private static void spawn(ServerPlayer player,
                              ServerLevel level,
                              StudioTestRegion region,
                              int existingCount,
                              StudioEntityActionPayload payload) {
        Optional<StudioEntityDefinition> definition = StudioEntityRegistry.get(payload.entityTypeId());
        if (definition.isEmpty()) {
            player.displayClientMessage(Component.literal("That entity type is not in the Studio allowlist."), false);
            return;
        }
        int available = Math.max(0, StudioConfig.MAX_ENTITY_LAB_ENTITIES.get() - existingCount);
        int requested = Math.min(payload.count(), available);
        if (requested == 0) {
            player.displayClientMessage(Component.literal("The Entity Lab has reached its configured entity cap."), false);
            return;
        }

        int width = region.max().getX() - region.min().getX() + 1;
        int depth = region.max().getZ() - region.min().getZ() + 1;
        int spawned = 0;
        for (int index = 0; index < requested; index++) {
            Entity entity = definition.get().entityType().create(level);
            if (entity == null) {
                continue;
            }
            int slot = existingCount + index;
            double x = region.min().getX() + (slot % width) + 0.5D;
            double z = region.min().getZ() + ((slot / width) % depth) + 0.5D;
            double y = region.min().getY();
            entity.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
            entity.addTag(TEST_ENTITY_TAG);
            if (level.addFreshEntity(entity)) {
                spawned++;
            }
        }
        player.displayClientMessage(Component.literal("Spawned " + spawned + " tagged Entity Lab test entities."), false);
    }

    private static List<Entity> taggedEntities(ServerLevel level, StudioTestRegion region) {
        return level.getEntities((Entity) null, region.bounds(), entity ->
                entity.getTags().contains(TEST_ENTITY_TAG) && region.contains(entity.blockPosition()));
    }
}
