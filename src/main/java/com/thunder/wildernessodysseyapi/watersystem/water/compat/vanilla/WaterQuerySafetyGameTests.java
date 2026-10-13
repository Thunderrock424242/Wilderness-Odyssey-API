package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicReference;

/** Runtime regressions for generation-thread sampling and non-loading water reads. */
@GameTestHolder("wildernessodysseyapi_water_safety_tests")
@PrefixGameTestTemplate(false)
public final class WaterQuerySafetyGameTests {

    private WaterQuerySafetyGameTests() {
    }

    @GameTest(template = "water_flow")
    public static void unaddedStructureMobKeepsVanillaWaterState(GameTestHelper helper) {
        BlockPos water = prepareWater(helper);
        Drowned drowned = EntityType.DROWNED.create(helper.getLevel());
        helper.assertTrue(drowned != null, "Could not create the structure mob");
        drowned.setPos(water.getX() + 0.5, water.getY() + 0.05, water.getZ() + 0.5);
        helper.assertTrue(!drowned.isAddedToLevel(), "Structure fixture was already added to the level");
        drowned.saveWithoutId(new CompoundTag());
        helper.assertTrue(!EntityWaterCompat.stateFor(drowned).authoritativeContactKnown(),
                "Serializing an unadded structure mob performed runtime water sampling");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 100)
    public static void generationWorkerDoesNotConsumeCachedEntityState(GameTestHelper helper) {
        BlockPos water = prepareWater(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 4, 4));
        zombie.setNoGravity(true);
        zombie.setPos(water.getX() + 0.5, water.getY() + 0.05, water.getZ() + 0.5);
        helper.assertTrue(EntityWaterCompat.stateFor(zombie).authoritativeContactKnown(),
                "Added entity did not see the authoritative water fixture");
        AtomicReference<EntityWaterState> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        startQueryWorker(() -> result.set(EntityWaterCompat.stateFor(zombie)), failure);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(failure.get() == null, "Entity worker query failed: " + failure.get());
            helper.assertTrue(result.get() != null, "Entity worker waited for server chunk work");
            helper.assertTrue(!result.get().authoritativeContactKnown(),
                    "Generation worker received mutable runtime entity water state");
            helper.succeed();
        });
    }

    @GameTest(template = "water_flow", timeoutTicks = 100)
    public static void generationWorkerAuthorityQueryReturnsUnavailable(GameTestHelper helper) {
        BlockPos water = prepareWater(helper);
        AtomicReference<WildernessWaterAuthority.CellAuthority> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        startQueryWorker(() -> result.set(WildernessWaterAuthority.sample(helper.getLevel(), water)), failure);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(failure.get() == null, "Authority worker query failed: " + failure.get());
            helper.assertTrue(result.get() != null, "Authority worker waited for server chunk work");
            helper.assertTrue(!result.get().water(), "Generation worker read live runtime water state");
            helper.succeed();
        });
    }

    @GameTest(template = "water_flow")
    public static void leavingEntityReleasesItsCachedState(GameTestHelper helper) {
        prepareWater(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 4, 4));
        EntityWaterState previous = EntityWaterCompat.stateFor(zombie);
        NeoForge.EVENT_BUS.post(new EntityLeaveLevelEvent(zombie, helper.getLevel()));
        helper.assertTrue(EntityWaterCompat.stateFor(zombie) != previous,
                "Entity-leave event retained the old runtime cache entry");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void flowQueryDoesNotLoadTheNeighboringChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos boundary = null;
        for (int distance = 0; distance < 64 && boundary == null; distance++) {
            int x = (origin.getX() >> 4) + distance;
            int z = origin.getZ() >> 4;
            if (level.getChunkSource().getChunkNow(x, z) != null
                    && level.getChunkSource().getChunkNow(x + 1, z) == null) {
                boundary = new BlockPos((x << 4) + 15, level.getMaxBuildHeight() - 8, (z << 4) + 8);
            }
        }
        helper.assertTrue(boundary != null, "No naturally loaded chunk boundary was available");
        var chunk = level.getChunkSource().getChunkNow(boundary.getX() >> 4, boundary.getZ() >> 4);
        BlockState original = chunk.getBlockState(boundary);
        // Bypass runtime reconciliation so this fixture exercises the tagged-water flow branch.
        chunk.setBlockState(boundary, Blocks.WATER.defaultBlockState(), false);
        try {
            helper.assertTrue(WildernessWaterAuthority.sample(level, boundary).water(),
                    "The tagged-water fixture was not sampled");
            helper.assertTrue(level.getChunkSource().getChunkNow((boundary.getX() >> 4) + 1,
                    boundary.getZ() >> 4) == null, "Reading fluid flow generated a neighboring chunk");
        } finally {
            chunk.setBlockState(boundary, original, false);
        }
        helper.succeed();
    }

    private static BlockPos prepareWater(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 4, 4));
        CanonicalWater.set(helper.getLevel(), pos, WaterVolumeChunk.WaterCell.still(4096, 0), true, false);
        helper.assertTrue(WildernessWaterAuthority.sample(helper.getLevel(), pos).authorityOwned(),
                "Authoritative water fixture was unavailable");
        return pos;
    }

    private static void startQueryWorker(Runnable query, AtomicReference<Throwable> failure) {
        Thread worker = new Thread(() -> {
            try {
                query.run();
            } catch (Throwable exception) {
                failure.set(exception);
            }
        }, "WO-Water-Query-Test");
        worker.setDaemon(true);
        worker.start();
    }
}
