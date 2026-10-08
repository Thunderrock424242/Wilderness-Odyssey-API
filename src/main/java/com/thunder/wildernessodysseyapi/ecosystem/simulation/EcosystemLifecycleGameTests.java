package com.thunder.wildernessodysseyapi.ecosystem.simulation;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.EcosystemEvents;
import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.behavior.EcosystemBehaviorGoal;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeManager;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Exercises real loaded entities and owner lifecycle transitions on the server thread. */
@GameTestHolder("wildernessodysseyapi_environment_tests")
@PrefixGameTestTemplate(false)
public final class EcosystemLifecycleGameTests {
    private EcosystemLifecycleGameTests() {
    }

    @GameTest(template = "empty")
    public static void protectedDistantWildlifeKeepsIndividualAi(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = cow(helper);
        try {
            EcosystemSimulationManager manager = EcosystemSimulationManager.get();
            manager.refreshLoadedWildlife(level);
            WildlifeSimulationLod lod = manager.getSimulationLevel(level, cow.blockPosition());
            helper.assertTrue(lod == WildlifeSimulationLod.DISTANT || lod == WildlifeSimulationLod.DORMANT,
                    "Fixture must be outside individual simulation range");
            helper.assertTrue(DistantWildlifeManager.get().isSafeToAbstract(cow),
                    "Fixture must begin as an unprotected adult cow");

            cow.setAge(-24_000);
            assertKeepsAi(helper, cow, "juvenile");
            cow.setAge(0);
            cow.setInLove(null);
            assertKeepsAi(helper, cow, "breeding");
            cow.resetLove();
            cow.setHealth(cow.getMaxHealth() - 1.0F);
            assertKeepsAi(helper, cow, "injured");
            cow.setHealth(cow.getMaxHealth());
            cow.addTag("ecosystem_lifecycle_test");
            assertKeepsAi(helper, cow, "scoreboard tagged");
            helper.succeed();
        } finally {
            cow.discard();
        }
    }

    @GameTest(template = "empty")
    public static void removingSpeciesProfileRestoresOwnedSuspension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = cow(helper);
        List<? extends String> previousAssignments = EcosystemConfig.BEHAVIOR_TAG_ASSIGNMENTS.get();
        try {
            EcosystemSimulationManager manager = EcosystemSimulationManager.get();
            manager.refreshLoadedWildlife(level);
            manager.tick(level.getServer());
            AnimalNeedsState needs = cow.getData(ModAttachments.ANIMAL_NEEDS);
            helper.assertTrue(needs.simulationAiSuspended() && cow.isNoAi(),
                    "Fixture must have actual simulation-owned suspension");
            Goal unrelated = new Goal() {
                @Override
                public boolean canUse() {
                    return true;
                }
            };
            cow.goalSelector.addGoal(0, unrelated);
            WrappedGoal unrelatedWrapper = cow.goalSelector.getAvailableGoals().stream()
                    .filter(wrapped -> wrapped.getGoal() == unrelated).findFirst().orElseThrow();
            WrappedGoal ecosystemWrapper = cow.goalSelector.getAvailableGoals().stream()
                    .filter(wrapped -> wrapped.getGoal() instanceof EcosystemBehaviorGoal)
                    .findFirst().orElseThrow();
            unrelatedWrapper.start();
            ecosystemWrapper.start();
            needs.begin(EcosystemBehaviorState.TRAVEL, cow.blockPosition(), level.getGameTime());

            EcosystemConfig.BEHAVIOR_TAG_ASSIGNMENTS.set(List.of("minecraft:cow=disabled"));
            EcosystemConfig.reload();
            manager.onConfigurationReload(level.getServer());
            EcosystemEvents.refreshLoadedControllers(level.getServer());
            helper.assertTrue(!cow.isNoAi() && !needs.simulationAiSuspended(),
                    "Removing the species profile stranded simulation-owned NoAI");
            helper.assertTrue(!ecosystemWrapper.isRunning() && needs.behavior() == EcosystemBehaviorState.IDLE,
                    "Removing the species profile left its ecosystem goal running");
            helper.assertTrue(unrelatedWrapper.isRunning()
                            && cow.goalSelector.getAvailableGoals().contains(unrelatedWrapper),
                    "Removing the ecosystem profile disturbed an unrelated goal");
            helper.assertTrue(!manager.loadedWildlife(level).contains(cow),
                    "A disabled species remained in loaded wildlife candidates");
            helper.succeed();
        } finally {
            cow.discard();
            EcosystemConfig.BEHAVIOR_TAG_ASSIGNMENTS.set(previousAssignments);
            EcosystemConfig.reload();
            EcosystemEvents.refreshLoadedControllers(level.getServer());
        }
    }

    @GameTest(template = "empty")
    public static void initialDiscoveryAndDisableRestoreOnlyOwnedNoAi(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow owned = cow(helper);
        Cow external = cow(helper);
        boolean previousEnabled = EcosystemConfig.ENABLED.get();
        try {
            EcosystemSimulationManager manager = EcosystemSimulationManager.get();
            manager.unload(level);
            AnimalNeedsState needs = owned.getData(ModAttachments.ANIMAL_NEEDS);
            needs.suspendAiForSimulation(false);
            owned.setNoAi(true);
            external.setNoAi(true);

            helper.assertTrue(manager.loadedWildlife(level).contains(owned),
                    "Initial discovery missed an already-loaded profiled cow");
            helper.assertTrue(!owned.isNoAi() && !needs.simulationAiSuspended(),
                    "Initial discovery did not release a persisted ownership marker");
            helper.assertTrue(external.isNoAi(), "Initial discovery overwrote external NoAI");

            needs.suspendAiForSimulation(false);
            owned.setNoAi(true);
            EcosystemConfig.ENABLED.set(false);
            manager.onConfigurationReload(level.getServer());
            manager.tick(level.getServer());
            helper.assertTrue(!owned.isNoAi() && !needs.simulationAiSuspended(),
                    "Disabling simulation did not restore owned NoAI");
            helper.assertTrue(external.isNoAi(), "Disabling simulation overwrote external NoAI");
            owned.discard();
            helper.assertTrue(!manager.loadedWildlife(level).contains(owned),
                    "Entity leave retained a discarded loaded candidate");
            helper.succeed();
        } finally {
            owned.discard();
            external.discard();
            EcosystemConfig.ENABLED.set(previousEnabled);
            EcosystemEvents.refreshLoadedControllers(level.getServer());
        }
    }

    @GameTest(template = "empty")
    public static void approachingPlayerImmediatelyRestoresTrackedWildlife(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = cow(helper);
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "wildlife-fixture"), ClientInformation.createDefault());
        // Observe the real distance classifier within this synchronous test call.
        // A temporary roster entry avoids third-party hooks requiring a client handshake.
        try {
            EcosystemSimulationManager manager = EcosystemSimulationManager.get();
            EcosystemSimulationSettings settings = EcosystemSimulationSettings.fromConfig();
            player.moveTo(cow.getX() + settings.distantRadius() + settings.cellSize() * 2.0,
                    cow.getY(), cow.getZ(), 0.0F, 0.0F);
            level.players().add(player);
            manager.refreshLoadedWildlife(level);
            manager.tick(level.getServer());
            AnimalNeedsState needs = cow.getData(ModAttachments.ANIMAL_NEEDS);
            helper.assertTrue(needs.simulationAiSuspended(), "Distant fixture did not suspend its owned AI");

            player.moveTo(cow.getX() + 2.0, cow.getY(), cow.getZ(), 0.0F, 0.0F);
            manager.tick(level.getServer());
            helper.assertTrue(!cow.isNoAi() && !needs.simulationAiSuspended(),
                    "An approaching player did not restore registered wildlife in the same manager tick");
            helper.succeed();
        } finally {
            cow.discard();
            level.players().remove(player);
        }
    }

    @GameTest(template = "empty")
    public static void rejectedJoinsNeverBecomeWildlife(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EcosystemSimulationManager manager = EcosystemSimulationManager.get();
        Cow original = cow(helper);
        Cow cancelled = EntityType.COW.create(level);
        Cow duplicate = EntityType.COW.create(level);
        helper.assertTrue(cancelled != null && duplicate != null, "Cow creation failed");
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() == cancelled) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, cancel);
        try {
            manager.refreshLoadedWildlife(level);
            cancelled.moveTo(original.position());
            helper.assertTrue(!level.addFreshEntity(cancelled), "Fixture join was not cancelled");
            helper.assertTrue(cancelled.isAlive() && !cancelled.isRemoved(),
                    "Cancellation must leave a live rejected object to reproduce the cache leak");
            helper.assertTrue(!manager.loadedWildlife(level).contains(cancelled),
                    "A cancelled join entered the transition owner's wildlife snapshot");
            manager.onConfigurationReload(level.getServer());
            helper.assertTrue(!cancelled.isNoAi(), "A rejected entity received simulation-owned suspension");

            duplicate.setUUID(original.getUUID());
            duplicate.moveTo(original.position());
            helper.assertTrue(!level.addFreshEntity(duplicate), "Duplicate UUID insertion was not rejected");
            List<?> wildlife = manager.loadedWildlife(level);
            helper.assertTrue(wildlife.contains(original) && !wildlife.contains(duplicate),
                    "A rejected duplicate replaced the server's accepted wildlife instance");
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
            original.discard();
            cancelled.discard();
            duplicate.discard();
        }
    }

    @GameTest(template = "empty")
    public static void trackingLeaveRetainsLiveWildlifeUntilPhysicalRemoval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = cow(helper);
        Cow newlyEligible = null;
        List<? extends String> previousAssignments = EcosystemConfig.BEHAVIOR_TAG_ASSIGNMENTS.get();
        try {
            EcosystemSimulationManager manager = EcosystemSimulationManager.get();
            manager.refreshLoadedWildlife(level);
            helper.assertTrue(manager.loadedWildlife(level).contains(cow), "Fixture was not registered");
            var original = cow.position();
            BlockPos hiddenPosition = cow.blockPosition().offset(1_000_000, 0, 1_000_000);
            ChunkPos hiddenChunk = new ChunkPos(hiddenPosition);
            helper.assertTrue(level.getChunkSource().getChunkNow(hiddenChunk.x, hiddenChunk.z) == null,
                    "Hidden fixture chunk must remain unloaded");
            cow.moveTo(hiddenPosition.getX(), hiddenPosition.getY(), hiddenPosition.getZ(), 0.0F, 0.0F);
            helper.assertTrue(level.getEntity(cow.getUUID()) == null && !cow.isRemoved(),
                    "Moving into an inaccessible section did not produce temporary tracking loss");
            helper.assertTrue(!manager.loadedWildlife(level).contains(cow),
                    "A hidden entity reached visible wildlife consumers");

            EcosystemConfig.BEHAVIOR_TAG_ASSIGNMENTS.set(List.of("minecraft:cow=disabled"));
            EcosystemConfig.reload();
            EcosystemEvents.refreshLoadedControllers(level.getServer());
            newlyEligible = cow(helper);
            newlyEligible.moveTo(hiddenPosition.getX() + 1, hiddenPosition.getY(), hiddenPosition.getZ(), 0.0F, 0.0F);
            helper.assertTrue(level.getEntity(newlyEligible.getUUID()) == null,
                    "Newly eligible fixture must be hidden during profile refresh");
            EcosystemConfig.BEHAVIOR_TAG_ASSIGNMENTS.set(previousAssignments);
            EcosystemConfig.reload();
            EcosystemEvents.refreshLoadedControllers(level.getServer());

            cow.moveTo(original);
            newlyEligible.moveTo(original);
            helper.assertTrue(manager.loadedWildlife(level).contains(cow),
                    "A profile reload while hidden permanently removed accepted wildlife");
            helper.assertTrue(manager.loadedWildlife(level).contains(newlyEligible),
                    "First-time profile eligibility while hidden missed an already-loaded animal");
            helper.assertTrue(newlyEligible.getData(ModAttachments.ANIMAL_NEEDS).controllerInstalled()
                            && newlyEligible.goalSelector.getAvailableGoals().stream()
                            .anyMatch(goal -> goal.getGoal() instanceof EcosystemBehaviorGoal),
                    "First-time eligibility after tracking resumed failed to install its behavior controller");
            helper.assertTrue(level.getChunkSource().getChunkNow(hiddenChunk.x, hiddenChunk.z) == null,
                    "Wildlife identity validation loaded the hidden fixture chunk");
            cow.discard();
            helper.assertTrue(!manager.loadedWildlife(level).contains(cow),
                    "Physical removal retained the dormant candidate");
            helper.succeed();
        } finally {
            cow.discard();
            if (newlyEligible != null) {
                newlyEligible.discard();
            }
            EcosystemConfig.BEHAVIOR_TAG_ASSIGNMENTS.set(previousAssignments);
            EcosystemConfig.reload();
            EcosystemEvents.refreshLoadedControllers(level.getServer());
        }
    }

    private static Cow cow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = EntityType.COW.create(level);
        helper.assertTrue(cow != null, "Cow creation failed");
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        cow.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0.0F, 0.0F);
        helper.assertTrue(level.addFreshEntity(cow), "Cow could not join the loaded test level");
        return cow;
    }

    @GameTest(template = "empty")
    public static void disabledSimulationPrunesRemovedHiddenCandidates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean previousEnabled = EcosystemConfig.ENABLED.get();
        Cow cow = null;
        try {
            EcosystemConfig.ENABLED.set(false);
            EcosystemSimulationManager manager = EcosystemSimulationManager.get();
            cow = cow(helper);
            manager.tick(level.getServer());
            BlockPos hiddenPosition = cow.blockPosition().offset(1_000_000, 0, 1_000_000);
            cow.moveTo(hiddenPosition.getX(), hiddenPosition.getY(), hiddenPosition.getZ(), 0.0F, 0.0F);
            helper.assertTrue(level.getEntity(cow.getUUID()) == null && !cow.isRemoved(),
                    "Disabled fixture did not enter a hidden live section");
            int retainedBeforeRemoval = manager.retainedCandidateCount(level);
            cow.discard();
            helper.assertTrue(manager.retainedCandidateCount(level) == retainedBeforeRemoval,
                    "Fixture must remove physically without another tracking Leave");
            // Inspect the owner cache directly: loadedWildlife would perform its own pruning.
            for (int pass = 0; pass < (retainedBeforeRemoval + 127) / 128; pass++) {
                manager.tick(level.getServer());
            }
            helper.assertTrue(manager.retainedCandidateCount(level) < retainedBeforeRemoval,
                    "Disabled ticks indefinitely retained physically removed hidden wildlife");
            helper.succeed();
        } finally {
            if (cow != null) {
                cow.discard();
            }
            EcosystemConfig.ENABLED.set(previousEnabled);
            EcosystemEvents.refreshLoadedControllers(level.getServer());
        }
    }

    private static void assertKeepsAi(GameTestHelper helper, Cow cow, String protection) {
        EcosystemSimulationManager.get().onConfigurationReload(helper.getLevel().getServer());
        helper.assertTrue(!DistantWildlifeManager.get().isSafeToAbstract(cow),
                protection + " must veto abstraction");
        helper.assertTrue(!cow.isNoAi() && !cow.getData(ModAttachments.ANIMAL_NEEDS).simulationAiSuspended(),
                protection + " must veto simulation suspension");
    }
}
