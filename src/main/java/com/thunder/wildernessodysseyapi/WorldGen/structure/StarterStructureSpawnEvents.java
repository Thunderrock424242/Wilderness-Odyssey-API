package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingSpawnEvent;

/**
 * Denies hostile mob spawns inside the starter bunker immediately after placement.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class StarterStructureSpawnEvents {
    private StarterStructureSpawnEvents() {
    }

    @SubscribeEvent
    public static void denyHostiles(LivingSpawnEvent.CheckSpawn event) {
        if (!StructureConfig.PREVENT_STARTER_STRUCTURE_HOSTILES.get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }

        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (StarterStructureSpawnGuard.isDenied(level, pos)) {
            event.setResult(Event.Result.DENY);
        }
    }
}
