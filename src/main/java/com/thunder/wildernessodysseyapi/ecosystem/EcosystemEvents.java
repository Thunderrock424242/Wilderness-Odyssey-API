package com.thunder.wildernessodysseyapi.ecosystem;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.behavior.EcosystemBehaviorGoal;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.service.EcosystemServices;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * NeoForge integration points for installing controllers and recording disturbances.
 *
 * <p>Entity join only attaches the goal; it performs no world query because the
 * event can fire before the underlying chunk reaches FULL status.</p>
 */
public final class EcosystemEvents {

    private static final int ECOSYSTEM_GOAL_PRIORITY = 2;

    private EcosystemEvents() {
    }

    /** Adds one conditional goal to server animals that have a loaded profile. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)
                || !(event.getEntity() instanceof PathfinderMob animal)) {
            return;
        }
        installController(animal);
    }

    /** Installs controllers on already-loaded mobs after a data-pack profile reload. */
    public static void refreshLoadedControllers(MinecraftServer server) {
        if (!EcosystemConfig.ENABLED.get()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof PathfinderMob animal) {
                    installController(animal);
                }
            }
        }
    }

    private static void installController(PathfinderMob animal) {
        if (!EcosystemConfig.ENABLED.get() || SpeciesBehaviorProfileManager.profileFor(animal).isEmpty()) {
            return;
        }
        AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
        if (needs.controllerInstalled()) {
            return;
        }
        animal.goalSelector.addGoal(ECOSYSTEM_GOAL_PRIORITY, new EcosystemBehaviorGoal(animal));
        needs.markControllerInstalled();
        needs.scheduleEvaluation(animal.level().getGameTime() + Math.floorMod(animal.getId(), 40));
    }

    /** Records attacks as high-intensity disturbances and wakes a profiled victim's controller. */
    @SubscribeEvent
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.getNewDamage() <= 0.0F) {
            return;
        }
        Entity source = event.getSource().getEntity();
        EcosystemServices.disturbances().record(
                level,
                event.getEntity().blockPosition(),
                source == null ? null : source.getUUID(),
                Math.min(1.0, 0.5 + event.getNewDamage() / Math.max(1.0F, event.getEntity().getMaxHealth())),
                level.getGameTime()
        );
        if (event.getEntity() instanceof PathfinderMob animal
                && SpeciesBehaviorProfileManager.profileFor(animal).isPresent()) {
            AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
            if (source != null) {
                needs.rememberThreat(source.blockPosition(), source.getUUID(), level.getGameTime() + 240L);
            }
            needs.scheduleEvaluation(level.getGameTime() + 1L);
        }
    }

    /** Records nearby player block breaking as a moderate transient disturbance. */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EcosystemServices.disturbances().record(
                    level,
                    event.getPos(),
                    event.getPlayer().getUUID(),
                    0.45,
                    level.getGameTime()
            );
        }
    }

    /** Releases world-derived caches when a server dimension unloads. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EcosystemServices.clear(level);
        }
    }
}
