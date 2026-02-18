package com.thunder.wildernessodysseyapi.worldgen.structure;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Denies hostile mob spawns inside the starter bunker immediately after placement.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class StarterStructureSpawnEvents {
    private StarterStructureSpawnEvents() {
    }

    @SubscribeEvent
    public static void denyHostilesPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!StructureConfig.PREVENT_STARTER_STRUCTURE_HOSTILES.get()) return;
        if (event.getEntityType().getCategory() != MobCategory.MONSTER) return;

        if (StarterStructureSpawnGuard.isDenied(event.getLevel(), event.getPos())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void denyHostilesPosition(MobSpawnEvent.PositionCheck event) {
        if (!StructureConfig.PREVENT_STARTER_STRUCTURE_HOSTILES.get()) return;

        Mob mob = event.getEntity();
        if (mob.getType().getCategory() != MobCategory.MONSTER) return;

        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (StarterStructureSpawnGuard.isDenied(event.getLevel(), pos)) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
