package com.thunder.wildernessodysseyapi.item.cloak;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.Set;

/**
 * Editable hard-coded list of mobs that are pulled toward cloak overuse.
 */
public final class CloakHunterTargets {
    public static final Set<ResourceLocation> ECHO_HYPOXIA_HUNTER_TYPES = Set.of(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "rift_listener"),
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "rift_maw"),
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "riftbound_wraith"),
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "riftborn"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "warden"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "enderman")
    );

    private CloakHunterTargets() {
    }

    public static void alertNearbyHunters(ServerPlayer player, double range) {
        player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range), mob ->
                mob.isAlive() && ECHO_HYPOXIA_HUNTER_TYPES.contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()))
        ).forEach(mob -> {
            mob.setTarget(player);
            mob.getNavigation().moveTo(player, 1.15D);
        });
    }
}
