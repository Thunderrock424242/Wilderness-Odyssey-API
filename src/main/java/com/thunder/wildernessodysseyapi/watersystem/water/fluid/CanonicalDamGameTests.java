package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Controlled finite inlet/basin/breach fixture; the production server ticker moves all water. */
@GameTestHolder("wildernessodysseyapi_water_safety_tests")
@PrefixGameTestTemplate(false)
public final class CanonicalDamGameTests {

    private CanonicalDamGameTests() { }

    @GameTest(template = "water_flow", batch = "canonical_dam", timeoutTicks = 600)
    public static void finiteBasinFillsBreachesAndRetainsItsSerializedInventory(GameTestHelper helper) {
        new DamFixture(helper, false).start();
    }

    static void runPhysicalFixture(GameTestHelper helper) {
        new DamFixture(helper, true).start();
    }

    private static final class DamFixture {
        private static final int FULL = WaterVolumeChunk.UNITS_PER_BLOCK;
        private static final int FUNDING_LIMIT = 4 * FULL;
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos origin;
        private final boolean requirePhysical;
        private int fundedUnits;
        private int upstreamBeforeBreach;
        private int steps;
        private int stableTicks;
        private boolean breached;
        private boolean physicalBeforeBreach;
        private boolean physicalAfterBreach;
        private int[] previousLevels;

        private DamFixture(GameTestHelper helper, boolean requirePhysical) {
            this.helper = helper;
            this.requirePhysical = requirePhysical;
            level = helper.getLevel();
            origin = helper.absolutePos(new BlockPos(1, 3, 4));
        }

        private void start() {
            for (BlockPos position : BlockPos.betweenClosed(origin.offset(-1, -2, -1), origin.offset(6, 2, 1))) {
                helper.assertTrue(level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null,
                        "Dam fixture must fit inside naturally admitted test chunks");
                level.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
            }
            for (int x = 0; x <= 2; x++) {
                level.setBlock(origin.offset(x, 0, 0), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(origin.offset(x, 1, 0), Blocks.AIR.defaultBlockState(), 2);
            }
            for (int x = 4; x <= 5; x++) {
                level.setBlock(origin.offset(x, -1, 0), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(origin.offset(x, 0, 0), Blocks.AIR.defaultBlockState(), 2);
            }
            assertBalance();
            helper.runAfterDelay(1, this::advance);
        }

        private void advance() {
            helper.assertTrue(++steps < 560, "Dam did not stabilize: funded=" + fundedUnits
                    + ", upstream=" + upstreamUnits() + ", downstream=" + downstreamUnits()
                    + ", breached=" + breached + "; " + WildernessFluidRegistry.diagnostics(level));
            assertBalance();
            if (requirePhysical) {
                boolean active = com.thunder.wildernessodysseyapi.watersystem.water.reactive.ReactiveWaterController
                        .regionAt(level, origin).cells() > 0;
                if (!breached) physicalBeforeBreach |= active && fundedUnits == FUNDING_LIMIT
                        && upperBasinUnits() > WaterVolumeChunk.UNITS_PER_BLOCK / 2
                        && com.thunder.wildernessodysseyapi.watersystem.water.reactive.ReactiveWaterController
                        .regionAt(level, origin.above()).cells() > 0;
                else physicalAfterBreach |= active
                        && com.thunder.wildernessodysseyapi.watersystem.water.reactive.ReactiveWaterController
                        .regionAt(level, origin.east(4)).cells() > 0;
                if (steps == 160 && !breached) helper.assertTrue(physicalBeforeBreach,
                        "Multi-depth filled basin never entered the physical region solver");
            }
            if (!breached) {
                int requested = Math.min(FULL / 4, FUNDING_LIMIT - fundedUnits);
                // This is the fixture's explicit finite inflow boundary. Only
                // the exact accepted amount is credited to its opening ledger.
                fundedUnits += CanonicalWater.addVolume(level, origin.above(), requested, 0, 0, 0);
                assertBalance();
                helper.assertTrue(downstreamUnits() == 0, "The closed dam leaked into disconnected terrain");
                if (fundedUnits == FUNDING_LIMIT && upperBasinUnits() > FULL / 2
                        && (!requirePhysical || physicalBeforeBreach)) {
                    roundTripCanonicalAttachments();
                    upstreamBeforeBreach = upstreamUnits();
                    helper.assertTrue(level.setBlock(origin.east(3), Blocks.AIR.defaultBlockState(), 2),
                            "Accepted automation breach failed");
                    breached = true;
                    previousLevels = levels();
                    assertBalance();
                }
            } else {
                int[] currentLevels = levels();
                boolean receded = downstreamUnits() >= FULL
                        && upstreamUnits() <= upstreamBeforeBreach - FULL;
                stableTicks = receded && Arrays.equals(previousLevels, currentLevels) ? stableTicks + 1 : 0;
                previousLevels = currentLevels;
                if (stableTicks >= 20) {
                    helper.assertTrue(!requirePhysical || physicalAfterBreach,
                            "Stepped breach drained without physical-region participation");
                    roundTripCanonicalAttachments();
                    ModConstants.LOGGER.info("[Canonical dam fixture] fundedUnits={}, upstreamUnits={}, downstreamUnits={}, stableTicks={}, steps={}",
                            fundedUnits, upstreamUnits(), downstreamUnits(), stableTicks, steps);
                    helper.succeed();
                    return;
                }
            }
            helper.runAfterDelay(1, this::advance);
        }

        private int[] levels() {
            int[] result = new int[18];
            int index = 0;
            for (int x = 0; x <= 5; x++) {
                for (int y = -1; y <= 1; y++) {
                    var cell = CanonicalWater.get(level, origin.offset(x, y, 0));
                    helper.assertTrue(!cell.projectionPending(), "Dam fixture has unresolved physical projection");
                    result[index++] = cell.volumeUnits();
                }
            }
            return result;
        }

        private void assertBalance() {
            int stored = Arrays.stream(levels()).sum();
            helper.assertTrue(stored == fundedUnits,
                    "Dam conservation residual=" + (fundedUnits - stored) + ", funded=" + fundedUnits + ", stored=" + stored);
        }

        private int upstreamUnits() {
            int total = 0;
            for (int x = 0; x <= 2; x++) {
                for (int y = 0; y <= 1; y++) total += CanonicalWater.get(level, origin.offset(x, y, 0)).volumeUnits();
            }
            return total;
        }

        private int upperBasinUnits() {
            int total = 0;
            for (int x = 0; x <= 2; x++) total += CanonicalWater.get(level, origin.offset(x, 1, 0)).volumeUnits();
            return total;
        }

        private int downstreamUnits() {
            int total = 0;
            for (int x = 4; x <= 5; x++) {
                for (int y = -1; y <= 0; y++) total += CanonicalWater.get(level, origin.offset(x, y, 0)).volumeUnits();
            }
            return total;
        }

        private void roundTripCanonicalAttachments() {
            // Exercise the production save format and runtime queue rebuild.
            // This is not disk chunk eviction or a server restart.
            Map<LevelChunk, CompoundTag> saved = new LinkedHashMap<>();
            for (int x = 0; x <= 5; x++) {
                BlockPos position = origin.east(x);
                LevelChunk chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
                helper.assertTrue(chunk != null, "Dam chunk unexpectedly unloaded");
                saved.computeIfAbsent(chunk,
                        loaded -> loaded.getData(ModAttachments.WATER_VOLUME).serializeNBT(level.registryAccess()));
            }
            int[] before = levels();
            for (var entry : saved.entrySet()) {
                if (requirePhysical) com.thunder.wildernessodysseyapi.watersystem.water.reactive.ReactiveWaterController
                        .onChunkUnload(level, entry.getKey().getPos());
                CanonicalWater.onChunkUnload(level, entry.getKey().getPos());
                entry.getKey().getData(ModAttachments.WATER_VOLUME).deserializeNBT(level.registryAccess(), entry.getValue());
                CanonicalWater.onChunkLoad(level, entry.getKey());
            }
            helper.assertTrue(Arrays.equals(before, levels()), "Canonical attachment reload changed the dam inventory");
            assertBalance();
        }
    }
}
