package com.thunder.wildernessodysseyapi.ecosystem.service;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.api.EcosystemBehaviorState;
import com.thunder.wildernessodysseyapi.ecosystem.api.EnvironmentalContext;
import com.thunder.wildernessodysseyapi.ecosystem.api.SpeciesBehaviorProfile;
import com.thunder.wildernessodysseyapi.ecosystem.behavior.DefaultEcosystemBehaviorController;
import com.thunder.wildernessodysseyapi.ecosystem.behavior.EcosystemBehaviorGoal;
import com.thunder.wildernessodysseyapi.ecosystem.behavior.GroupFollowerGoal;
import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroup;
import com.thunder.wildernessodysseyapi.ecosystem.group.AnimalGroupManager;
import com.thunder.wildernessodysseyapi.ecosystem.group.GroupBehavior;
import com.thunder.wildernessodysseyapi.ecosystem.state.AnimalNeedsState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Loaded-world regressions for wildlife decisions and their cheap local execution. */
@GameTestHolder("wildernessodysseyapi_environment_tests")
@PrefixGameTestTemplate(false)
public final class EcosystemRemediationGameTests {
    private EcosystemRemediationGameTests() {
    }

    @GameTest(template = "water_flow", batch = "ecosystem_remediation")
    public static void groupDiscoveryDoesNotCountCallerTwiceTowardsCapacity(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        Cow first = fixture.cow(3, 2, 3);
        Cow second = fixture.cow(5, 2, 3);
        fixture.whenRegistered(() -> {
            try (fixture) {
                AnimalGroupManager manager = new AnimalGroupManager((observer, radius) -> List.of(first, second));
                AnimalGroup group = manager.discoverOrCreate(first,
                        SpeciesBehaviorProfileManager.profileFor(first).orElseThrow(), 8, 2, 40L);
                helper.assertTrue(group.memberCount() == 2 && group.members().contains(second.getUUID()),
                        "Candidate discovery counted the caller twice and excluded the second animal at capacity two");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "water_flow", batch = "ecosystem_remediation")
    public static void groupedFollowerAccumulatesNeedsWithoutAnotherExpensiveDecision(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        Cow first = fixture.cow(3, 2, 3);
        Cow second = fixture.cow(5, 2, 3);
        fixture.whenRegistered(() -> {
            try (fixture) {
                AnimalGroup group = fixture.group(first);
                Cow follower = group.getLeader().equals(first.getUUID()) ? second : first;
                AnimalNeedsState needs = follower.getData(ModAttachments.ANIMAL_NEEDS);
                needs.recordEvaluation(fixture.level.getGameTime() - 1_200L, 0L, 0L);
                needs.scheduleEvaluation(0L);
                int usedBefore = EcosystemServices.budget().snapshot(fixture.level).used();
                new EcosystemBehaviorGoal(follower).canUse();
                helper.assertTrue(needs.thirst() > 0.0F && needs.hunger() > 0.0F && needs.rest() > 0.0F,
                        "Grouped followers must accumulate their own physiological needs");
                helper.assertTrue(EcosystemServices.budget().snapshot(fixture.level).used() == usedBefore,
                        "A cached follower must not acquire another expensive environmental decision");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "water_flow", batch = "ecosystem_remediation")
    public static void followerRecoversFromSimulationSuspendedLeader(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        Cow first = fixture.cow(3, 2, 3);
        Cow second = fixture.cow(5, 2, 3);
        fixture.whenRegistered(() -> {
            try (fixture) {
                AnimalGroup group = fixture.group(first);
                Cow leader = group.getLeader().equals(first.getUUID()) ? first : second;
                Cow follower = leader == first ? second : first;
                leader.getData(ModAttachments.ANIMAL_NEEDS).suspendAiForSimulation(false);
                leader.setNoAi(true);
                follower.getData(ModAttachments.ANIMAL_NEEDS).scheduleEvaluation(0L);
                new EcosystemBehaviorGoal(follower).canUse();
                helper.assertTrue(!group.getLeader().equals(leader.getUUID()),
                        "A locally active follower remained owned by a simulation-suspended leader");
                helper.assertTrue(leader.isNoAi(), "Group recovery must preserve the suspension owner's NoAI");
                helper.succeed();
            }
        });
    }

    @GameTest(template = "water_flow", batch = "ecosystem_remediation")
    public static void cachedSwimmingTargetIsRejectedForNonSwimmingAnimal(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Cow animal = fixture.cow(5, 3, 5);
            BlockPos center = animal.blockPosition();
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    fixture.level.setBlock(center.offset(dx, -2, dz), Blocks.STONE.defaultBlockState(), 2);
                    fixture.level.setBlock(center.offset(dx, -1, dz), Blocks.WATER.defaultBlockState(), 2);
                    fixture.level.setBlock(center.offset(dx, 0, dz), Blocks.WATER.defaultBlockState(), 2);
                }
            }
            CachedWaterSourceLocator locator = new CachedWaterSourceLocator();
            SpeciesBehaviorProfile base = SpeciesBehaviorProfileManager.profileFor(animal).orElseThrow();
            SpeciesBehaviorProfile swimmer = drinkingProfile(base, true);
            SpeciesBehaviorProfile nonSwimmer = drinkingProfile(base, false);
            helper.assertTrue(locator.find(animal, swimmer, 2).isPresent(),
                    "Fixture must prime an in-water swimming target");
            helper.assertTrue(locator.find(animal, nonSwimmer, 2).isEmpty(),
                    "A cached swimming approach was reused for a non-swimming profile");
            helper.succeed();
        }
    }

    @GameTest(template = "water_flow", batch = "ecosystem_remediation", skyAccess = true)
    public static void canopyDoesNotHideGroundForage(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Cow animal = fixture.cow(5, 2, 5);
            BlockPos center = animal.blockPosition();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    fixture.level.setBlock(center.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    fixture.level.setBlock(center.offset(dx, 0, dz), Blocks.SHORT_GRASS.defaultBlockState(), 2);
                    fixture.level.setBlock(center.offset(dx, 4, dz), Blocks.OAK_LEAVES.defaultBlockState(), 2);
                    fixture.level.setBlock(center.offset(dx, 5, dz), Blocks.OAK_LEAVES.defaultBlockState(), 2);
                }
            }
            double available = new CachedFoodAvailabilityService(new NearbyLivingEntityCache()).availability(animal, 4);
            helper.assertTrue(available >= 0.95,
                    "A continuous grass floor under a leaf canopy was classified as depleted forage");
            helper.succeed();
        }
    }

    @GameTest(template = "water_flow", batch = "ecosystem_remediation", skyAccess = true)
    public static void clearColdConditionsDiscoverAndExecuteShelter(GameTestHelper helper) throws Exception {
        Fixture fixture = new Fixture(helper, false);
        try {
            Cow animal = fixture.cow(3, 2, 3);
            // A small nearby roof is enough cover without changing the exposed origin.
            fixture.level.setBlock(animal.blockPosition().offset(2, 2, 0), Blocks.STONE.defaultBlockState(), 2);
            helper.runAfterDelay(2L, () -> {
                try {
                    BlockPos covered = animal.blockPosition().east(2);
                    helper.assertTrue(CachedWaterSourceLocator.isStandable(fixture.level, covered)
                                    && !fixture.level.canSeeSky(covered.above()),
                            "Fixture must contain a standable covered position after light propagation");
                    SpeciesBehaviorProfile base = SpeciesBehaviorProfileManager.profileFor(animal).orElseThrow();
                    SpeciesBehaviorProfile.Environment old = base.environment();
                    SpeciesBehaviorProfile coldProfile = new SpeciesBehaviorProfile(base.id(), base.entities(), base.entityTags(),
                            base.needs(), base.drinking(), base.shelter(), base.herd(), base.prey(), base.predator(),
                            new SpeciesBehaviorProfile.Environment(old.activeTime(), 60.0, 60.0,
                                    old.hotDryDrinkThresholdReduction(), old.forageHungerThreshold(), old.restThreshold(),
                                    old.minimumFoodForForage(), old.localTravelRadius(), old.migrationRadius(),
                                    old.scheduleJitterTicks(), old.restDurationTicks(), old.sleepDurationTicks(), old.supportedStates()));
                    EcosystemBehaviorGoal goal = new EcosystemBehaviorGoal(animal);
                    Method build = EcosystemBehaviorGoal.class.getDeclaredMethod("buildContext", ServerLevel.class,
                            SpeciesBehaviorProfile.class, double.class, long.class, AnimalGroup.class);
                    build.setAccessible(true);
                    EnvironmentalContext context = (EnvironmentalContext) build.invoke(goal, fixture.level, coldProfile,
                            1.0, fixture.level.getGameTime(), null);
                    helper.assertTrue(context.weather().temperature() < 60.0 && context.exposedToSky(),
                            "Fixture must be exposed and below its configured preferred minimum");
                    helper.assertTrue(context.shelter().isPresent(),
                            "Clear cold weather did not request a bounded shelter search");
                    AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
                    helper.assertTrue(new DefaultEcosystemBehaviorController().evaluate(context, needs)
                                    && needs.behavior() == EcosystemBehaviorState.SEEK_SHELTER,
                            "The runtime controller discarded a cold SEEK_SHELTER decision");
                    helper.succeed();
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                } finally {
                    fixture.close();
                }
            });
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    @GameTest(template = "water_flow", batch = "ecosystem_follower_lifecycle", timeoutTicks = 300)
    public static void delayedFollowerFinishesRestAfterLeaderGoalStops(GameTestHelper helper) throws Exception {
        assertDelayedFollowerRest(helper, false);
    }

    @GameTest(template = "water_flow", batch = "ecosystem_follower_lifecycle", timeoutTicks = 300)
    public static void clearingCompletedGroupDecisionCancelsFinishingFollowerRest(GameTestHelper helper) throws Exception {
        assertDelayedFollowerRest(helper, true);
    }

    private static void assertDelayedFollowerRest(GameTestHelper helper, boolean interruptAfterLeader) throws Exception {
        Fixture fixture = new Fixture(helper, false);
        try {
            Cow first = fixture.cow(4, 2, 4);
            Cow second = fixture.cow(6, 2, 4);
            fixture.whenRegistered(() -> {
                AnimalGroup group = fixture.group(first);
                Cow leader = group.getLeader().equals(first.getUUID()) ? first : second;
                Cow follower = leader == first ? second : first;
                SpeciesBehaviorProfile profile = SpeciesBehaviorProfileManager.profileFor(leader).orElseThrow();
                AnimalNeedsState leaderNeeds = leader.getData(ModAttachments.ANIMAL_NEEDS);
                AnimalNeedsState followerNeeds = follower.getData(ModAttachments.ANIMAL_NEEDS);
                leaderNeeds.setNeeds(0.0, 0.0, 0.8, 0.0, 0.0);
                followerNeeds.setNeeds(0.0, 0.0, 0.8, 0.0, 0.0);
                long revision = group.publishLeaderDecision(GroupBehavior.REST, null,
                        fixture.level.getGameTime(), EcosystemConfig.LEADER_DECISION_INTERVAL.get());
                leaderNeeds.begin(EcosystemBehaviorState.REST, null, fixture.level.getGameTime());
                EcosystemBehaviorGoal leaderGoal = new EcosystemBehaviorGoal(leader);
                setField(leaderGoal, "activeProfile", profile);
                setField(leaderGoal, "activeGroup", group);
                setField(leaderGoal, "activeGroupRevision", revision);
                leaderGoal.start();
                boolean[] leaderStopped = {false};
                helper.onEachTick(() -> {
                    if (!leaderStopped[0]) {
                        leaderGoal.tick();
                        if (!leaderGoal.canContinueToUse()) {
                            leaderGoal.stop();
                            leaderStopped[0] = true;
                        }
                    }
                });
                // Exercise the real goal lifecycle, including the leader's normal
                // completion/stop, with a member that begins its own rest later.
                helper.runAfterDelay(25L, () ->
                        follower.goalSelector.addGoal(3, new GroupFollowerGoal(follower)));
                if (interruptAfterLeader) {
                    helper.runAfterDelay(profile.environment().restDurationTicks() + 10L, () -> {
                        helper.assertTrue(leaderStopped[0] && followerNeeds.interactionStartedAt() != 0L,
                                "Fixture must interrupt a member already finishing after normal leader completion");
                        group.clearDecisionIfRevision(group.revision());
                    });
                }
                helper.runAfterDelay(profile.environment().restDurationTicks() + 90L, () -> {
                    try {
                        helper.assertTrue(leaderStopped[0] && leaderNeeds.rest() < 0.75F,
                                "Fixture leader did not finish through its normal goal lifecycle");
                        helper.assertTrue(interruptAfterLeader ? followerNeeds.rest() >= 0.79F : followerNeeds.rest() < 0.75F,
                                interruptAfterLeader
                                        ? "A cleared completion decision allowed a finishing follower to restore rest"
                                        : "Delayed follower lost its local rest when the leader goal stopped");
                        helper.succeed();
                    } finally {
                        fixture.close();
                    }
                });
            });
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @GameTest(template = "water_flow", batch = "ecosystem_follower_interrupt", timeoutTicks = 300)
    public static void urgentGroupDecisionInterruptsFollowerRest(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        Cow first = fixture.cow(4, 2, 4);
        Cow second = fixture.cow(6, 2, 4);
        fixture.whenRegistered(() -> {
            AnimalGroup group = fixture.group(first);
            Cow follower = group.getLeader().equals(first.getUUID()) ? second : first;
            AnimalNeedsState needs = follower.getData(ModAttachments.ANIMAL_NEEDS);
            needs.setNeeds(0.0, 0.0, 0.8, 0.0, 0.0);
            int duration = SpeciesBehaviorProfileManager.profileFor(follower).orElseThrow()
                    .environment().restDurationTicks();
            group.publishLeaderDecision(GroupBehavior.REST, null, fixture.level.getGameTime(), 1_000L);
            follower.goalSelector.addGoal(3, new GroupFollowerGoal(follower));
            helper.runAfterDelay(duration / 2L, () -> group.reportThreat(
                    follower.blockPosition(), null, follower.blockPosition().east(4)));
            helper.runAfterDelay(duration + 90L, () -> {
                try {
                    helper.assertTrue(needs.rest() >= 0.79F,
                            "An urgent shared flight decision allowed interrupted rest to complete");
                    helper.succeed();
                } finally {
                    fixture.close();
                }
            });
        });
    }

    @GameTest(template = "water_flow", batch = "ecosystem_follower_revisions", timeoutTicks = 300)
    public static void equivalentGroupRevisionsPreserveFollowerRestProgress(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        try {
            Cow first = fixture.cow(4, 2, 4);
            Cow second = fixture.cow(6, 2, 4);
            fixture.whenRegistered(() -> {
                AnimalGroup group = fixture.group(first);
                Cow follower = group.getLeader().equals(first.getUUID()) ? second : first;
                AnimalNeedsState needs = follower.getData(ModAttachments.ANIMAL_NEEDS);
                needs.setNeeds(0.0, 0.0, 0.8, 0.0, 0.0);
                int duration = SpeciesBehaviorProfileManager.profileFor(follower).orElseThrow()
                        .environment().restDurationTicks();
                group.publishLeaderDecision(GroupBehavior.REST, null, fixture.level.getGameTime(), 40L);
                follower.goalSelector.addGoal(3, new GroupFollowerGoal(follower));
                helper.onEachTick(() -> {
                    if (fixture.level.getGameTime() % 20L == 0L) {
                        group.publishLeaderDecision(GroupBehavior.REST, null, fixture.level.getGameTime(), 40L);
                    }
                });
                helper.runAfterDelay(duration + 90L, () -> {
                    try {
                        helper.assertTrue(needs.rest() < 0.75F,
                                "Equivalent shared decisions repeatedly reset the follower's local rest progress");
                        helper.succeed();
                    } finally {
                        fixture.close();
                    }
                });
            });
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    @GameTest(template = "water_flow", batch = "ecosystem_remediation")
    public static void shelterWaitsForColdToPassBeforeItsReleaseDelay(GameTestHelper helper) throws Exception {
        try (Fixture fixture = new Fixture(helper)) {
            Cow animal = fixture.cow(5, 2, 5);
            fixture.level.setBlock(animal.blockPosition().above(2), Blocks.STONE.defaultBlockState(), 2);
            SpeciesBehaviorProfile base = SpeciesBehaviorProfileManager.profileFor(animal).orElseThrow();
            SpeciesBehaviorProfile.Environment old = base.environment();
            SpeciesBehaviorProfile coldProfile = new SpeciesBehaviorProfile(base.id(), base.entities(), base.entityTags(),
                    base.needs(), base.drinking(), base.shelter(), base.herd(), base.prey(), base.predator(),
                    new SpeciesBehaviorProfile.Environment(old.activeTime(), 60.0, 60.0,
                            old.hotDryDrinkThresholdReduction(), old.forageHungerThreshold(), old.restThreshold(),
                            old.minimumFoodForForage(), old.localTravelRadius(), old.migrationRadius(),
                            old.scheduleJitterTicks(), old.restDurationTicks(), old.sleepDurationTicks(), old.supportedStates()));
            EcosystemBehaviorGoal goal = new EcosystemBehaviorGoal(animal);
            Field activeProfile = EcosystemBehaviorGoal.class.getDeclaredField("activeProfile");
            activeProfile.setAccessible(true);
            activeProfile.set(goal, coldProfile);
            Method shelter = EcosystemBehaviorGoal.class.getDeclaredMethod("tickSheltering", ServerLevel.class, long.class);
            shelter.setAccessible(true);
            AnimalNeedsState needs = animal.getData(ModAttachments.ANIMAL_NEEDS);
            needs.begin(EcosystemBehaviorState.SHELTERING, animal.blockPosition(), fixture.level.getGameTime());
            needs.setShelterReleaseAt(0L);
            long checkedAt = fixture.level.getGameTime() + 40L;
            checkedAt += Math.floorMod(animal.getId() - checkedAt, 40L);
            shelter.invoke(goal, fixture.level, checkedAt);
            helper.assertTrue(needs.behavior() == EcosystemBehaviorState.SHELTERING
                            && needs.shelterReleaseAt() == Long.MAX_VALUE,
                    "Shelter released an animal while below its preferred minimum temperature");
            activeProfile.set(goal, base);
            shelter.invoke(goal, fixture.level, checkedAt + 40L);
            helper.assertTrue(needs.shelterReleaseAt() != Long.MAX_VALUE,
                    "Warm safe conditions did not begin the ordinary shelter release delay");
            long releasedAt = needs.shelterReleaseAt() + 40L;
            releasedAt += Math.floorMod(animal.getId() - releasedAt, 40L);
            shelter.invoke(goal, fixture.level, releasedAt);
            helper.assertTrue(needs.behavior() == EcosystemBehaviorState.IDLE,
                    "Shelter did not release the animal after warm conditions and the configured delay");
            helper.succeed();
        }
    }

    @GameTest(template = "water_flow", batch = "ecosystem_follower_actions", timeoutTicks = 400)
    public static void followersCompleteSharedDrinkingAndRest(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, false);
        try {
            Cow first = fixture.cow(4, 2, 4);
            Cow second = fixture.cow(6, 2, 4);
            fixture.whenRegistered(() -> {
                AnimalGroup group = fixture.group(first);
                Cow leader = group.getLeader().equals(first.getUUID()) ? first : second;
                Cow follower = leader == first ? second : first;
                BlockPos approach = follower.blockPosition();
                BlockPos source = approach.south().below();
                fixture.level.setBlock(source.below(), Blocks.STONE.defaultBlockState(), 2);
                fixture.level.setBlock(source, Blocks.WATER.defaultBlockState(), 2);
                EnvironmentalContext.WaterTarget water = new EnvironmentalContext.WaterTarget(source, approach, 1.0);
                helper.assertTrue(EcosystemServices.isSafeWaterTarget(follower,
                        SpeciesBehaviorProfileManager.profileFor(follower).orElseThrow(), water),
                        "Fixture must supply safe recessed shoreline water");
                group.observeEnvironment(group.environment(), group.foodAvailability(), false, water);
                AnimalNeedsState needs = follower.getData(ModAttachments.ANIMAL_NEEDS);
                needs.setNeeds(0.8, 0.8, 0.8, 0.0, 0.0);
                needs.scheduleEvaluation(0L);
                follower.goalSelector.addGoal(3, new GroupFollowerGoal(follower));
                group.publishLeaderDecision(GroupBehavior.SEEK_WATER, approach, fixture.level.getGameTime(), 1_000L);
                helper.runAfterDelay(150L, () -> {
                    try {
                        helper.assertTrue(needs.thirst() < 0.3F,
                                "A follower at shared safe water did not finish drinking");
                        needs.setNeeds(needs.thirst(), needs.hunger(), 0.8, 0.0, 0.0);
                        group.publishLeaderDecision(GroupBehavior.REST, null, fixture.level.getGameTime(), 1_000L);
                    } catch (Throwable failure) {
                        fixture.close();
                        throw failure;
                    }
                });
                helper.runAfterDelay(330L, () -> {
                    try {
                        helper.assertTrue(needs.rest() < 0.75F,
                                "A follower did not recover rest from the shared resting decision");
                        helper.succeed();
                    } finally {
                        fixture.close();
                    }
                });
            });
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    private static SpeciesBehaviorProfile drinkingProfile(SpeciesBehaviorProfile base, boolean swims) {
        SpeciesBehaviorProfile.Drinking settings = base.drinking();
        return new SpeciesBehaviorProfile(base.id(), base.entities(), base.entityTags(), base.needs(),
                new SpeciesBehaviorProfile.Drinking(true, settings.thirstThreshold(), settings.searchRadius(),
                        settings.durationTicks(), settings.moveSpeed(), settings.thirstRestored(), swims, 4.0),
                base.shelter(), base.herd(), base.prey(), base.predator(), base.environment());
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<Cow> animals = new ArrayList<>();
        private final boolean zones = EcosystemConfig.SIMULATION_ZONES_ENABLED.get();
        private final boolean changeZones;

        private Fixture(GameTestHelper helper) {
            this(helper, true);
        }

        private Fixture(GameTestHelper helper, boolean changeZones) {
            this.helper = helper;
            level = helper.getLevel();
            this.changeZones = changeZones;
            if (changeZones) {
                EcosystemConfig.SIMULATION_ZONES_ENABLED.set(false);
            }
        }

        private Cow cow(int x, int y, int z) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
            for (int floorX = 0; floorX <= 15; floorX++) {
                for (int floorZ = 0; floorZ <= 15; floorZ++) {
                    level.setBlock(helper.absolutePos(new BlockPos(floorX, y - 1, floorZ)),
                            Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
            }
            Cow cow = EntityType.COW.create(level);
            if (cow == null) {
                throw new IllegalStateException("Could not create cow fixture");
            }
            cow.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            cow.addTag("ecosystem_remediation_fixture");
            helper.assertTrue(level.addFreshEntity(cow), "Cow fixture was not inserted");
            cow.goalSelector.removeAllGoals(goal -> true);
            cow.targetSelector.removeAllGoals(goal -> true);
            // Keep actual server ticks from abstracting an observation fixture without a player.
            animals.add(cow);
            return cow;
        }

        private AnimalGroup group(Cow cow) {
            helper.assertTrue(animals.stream().noneMatch(Cow::isNoAi),
                    "Fixture animals unexpectedly entered NoAI before group discovery");
            try {
                // Batches reuse nearby sections before this 20-tick cache expires.
                // Refresh only discovery observations after adding fixture mobs;
                // never clear live group ownership or a shared action mid-test.
                Field nearby = EcosystemServices.class.getDeclaredField("NEARBY_ENTITIES");
                nearby.setAccessible(true);
                ((NearbyLivingEntityCache) nearby.get(null)).clear(level);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
            AnimalGroup group = EcosystemServices.groups().discoverOrCreate(cow,
                    SpeciesBehaviorProfileManager.profileFor(cow).orElseThrow(), 8, 2, 40L);
            helper.assertTrue(group.memberCount() == animals.size()
                            && animals.stream().allMatch(animal -> group.members().contains(animal.getUUID())),
                    "Fixture group does not own both local cows: members=" + group.memberCount());
            return group;
        }

        private void whenRegistered(CheckedAction action) {
            long initialVisible = animals.stream().filter(animal -> level.getEntity(animal.getUUID()) == animal).count();
            // Sequence callbacks run after the scheduled-runnable map has been
            // traversed, so action fixtures may safely register later assertions.
            helper.startSequence().thenExecuteAfter(2, () -> {
                boolean previousZones = EcosystemConfig.SIMULATION_ZONES_ENABLED.get();
                try {
                    helper.assertTrue(animals.stream().allMatch(animal -> level.getEntity(animal.getUUID()) == animal),
                            "Fixture cows must be visible after entity registration; initially visible=" + initialVisible);
                    List<Cow> nearby = level.getEntitiesOfClass(Cow.class,
                            animals.getFirst().getBoundingBox().inflate(8.0), animals::contains);
                    helper.assertTrue(nearby.containsAll(animals),
                            "Fixture cows must be visible to real local entity discovery: visible=" + nearby.size());
                    EcosystemConfig.SIMULATION_ZONES_ENABLED.set(false);
                    action.run();
                } catch (RuntimeException | Error failure) {
                    close();
                    throw failure;
                } catch (Exception failure) {
                    close();
                    throw new IllegalStateException(failure);
                } finally {
                    EcosystemConfig.SIMULATION_ZONES_ENABLED.set(previousZones);
                }
            });
        }

        @Override
        public void close() {
            animals.forEach(Cow::discard);
            if (changeZones) {
                EcosystemConfig.SIMULATION_ZONES_ENABLED.set(zones);
            }
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
