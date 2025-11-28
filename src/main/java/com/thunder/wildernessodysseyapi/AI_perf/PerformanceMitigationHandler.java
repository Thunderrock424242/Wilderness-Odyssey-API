package com.thunder.wildernessodysseyapi.AI_perf;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * Performs lightweight, main-thread throttles once an action is approved.
 */
@Mod.EventBusSubscriber(modid = ModConstants.MOD_ID)
public class PerformanceMitigationHandler {

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        if (PerformanceMitigationController.isEntityTickThrottled(serverLevel)
                && mob.tickCount % PerformanceMitigationController.getEntityTickInterval() != 0) {
            PerformanceMitigationController.maybeFreezeEntity(mob);
            mob.getNavigation().stop();
            return;
        } else {
            PerformanceMitigationController.thawEntity(mob);
        }

        if (PerformanceMitigationController.isPathfindingThrottled(serverLevel)
                && mob.tickCount % PerformanceMitigationController.getPathfindingThrottleInterval() != 0) {
            mob.getNavigation().stop();
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.level().isClientSide()) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (PerformanceMitigationController.isEntityTickThrottled(serverLevel)) {
                PerformanceMitigationController.thawNearbyPlayers(serverLevel);
            }
        }
    }
}
