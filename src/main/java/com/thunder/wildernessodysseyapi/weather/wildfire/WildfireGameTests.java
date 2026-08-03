package com.thunder.wildernessodysseyapi.weather.wildfire;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.integration.SeasonalWeatherInfluence;
import com.thunder.wildernessodysseyapi.weather.integration.WaterInfluenceSample;
import com.thunder.wildernessodysseyapi.weather.integration.WeatherWaterInfluence;
import com.thunder.wildernessodysseyapi.weather.simulation.AtmosphereInputSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded-world regression coverage for campfire source and vanilla-fire placement rules. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WildfireGameTests {

    private static final WeatherConfig.WildfireSettings TEST_SETTINGS =
            new WeatherConfig.WildfireSettings(true, 600, 48_000, 168_000, 2, 4, 4, 32, 1.0);
    private static final WeatherWaterInfluence NO_WATER = new WeatherWaterInfluence() {
        @Override
        public WaterInfluenceSample sample(
                ServerLevel level,
                com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey cell,
                int cellSize,
                int refreshIntervalTicks
        ) {
            return WaterInfluenceSample.UNKNOWN;
        }

        @Override
        public void clear() {
        }
    };

    private WildfireGameTests() {
    }

    /** Confirms a forced open normal campfire ember places exactly normal vanilla fire on tagged leaves. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void openCampfireIgnitesTaggedLoadedFuel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos campfire = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(campfire.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(campfire, Blocks.CAMPFIRE.defaultBlockState(), 3);
        placeLeafRing(level, campfire);

        helper.runAfterDelay(1, () -> {
            boolean previousFireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
            try {
                WildfireScheduler.IgnitionResult result = scheduler(WeatherSample.CLEAR)
                        .forceIgnition(level, campfire, 256, TEST_SETTINGS);
                helper.assertTrue(result == WildfireScheduler.IgnitionResult.IGNITED,
                        "Forced ember result was " + result + " instead of IGNITED");
                int fire = countFireNear(level, campfire);
                helper.assertTrue(fire == 1,
                        "Forced ember placed " + fire + " fire blocks instead of exactly one");
                helper.succeed();
            } finally {
                level.getGameRules().getRule(GameRules.RULE_DOFIRETICK)
                        .set(previousFireTick, level.getServer());
            }
        });
    }

    /** Confirms covered normal campfires and open soul campfires are not wildfire sources. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coveredAndSoulCampfiresRemainSafe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos covered = helper.absolutePos(new BlockPos(6, 2, 8));
        BlockPos soul = helper.absolutePos(new BlockPos(10, 2, 8));
        level.setBlock(covered.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(covered, Blocks.CAMPFIRE.defaultBlockState(), 3);
        level.setBlock(covered.above(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(soul.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(soul, Blocks.SOUL_CAMPFIRE.defaultBlockState(), 3);
        placeLeafRing(level, covered);

        helper.runAfterDelay(1, () -> {
            boolean previousFireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
            level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
            try {
                WildfireScheduler scheduler = scheduler(WeatherSample.CLEAR);
                WildfireScheduler.IgnitionResult coveredResult = scheduler
                        .forceIgnition(level, covered, 256, TEST_SETTINGS);
                WildfireScheduler.IgnitionResult soulResult = scheduler
                        .forceIgnition(level, soul, 256, TEST_SETTINGS);
                helper.assertTrue(coveredResult == WildfireScheduler.IgnitionResult.NO_CAMPFIRE,
                        "Covered campfire result was " + coveredResult + " instead of NO_CAMPFIRE");
                helper.assertTrue(soulResult == WildfireScheduler.IgnitionResult.NO_CAMPFIRE,
                        "Soul campfire result was " + soulResult + " instead of NO_CAMPFIRE");
                helper.assertTrue(countFireNear(level, covered) == 0,
                        "Rejected campfire sources must not place fire");
                helper.succeed();
            } finally {
                level.getGameRules().getRule(GameRules.RULE_DOFIRETICK)
                        .set(previousFireTick, level.getServer());
            }
        });
    }

    private static WildfireScheduler scheduler(WeatherSample sample) {
        return new WildfireScheduler(
                (level, position) -> sample,
                new AtmosphereInputSampler(NO_WATER, SeasonalWeatherInfluence.NONE)
        );
    }

    private static void placeLeafRing(ServerLevel level, BlockPos center) {
        for (int dz = -5; dz <= 5; dz++) {
            for (int dx = -5; dx <= 5; dx++) {
                double distance = Math.hypot(dx, dz);
                if (distance < 3.4 || distance > 4.8) {
                    continue;
                }
                level.setBlock(
                        center.offset(dx, -1, dz),
                        Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                        3
                );
            }
        }
    }

    private static int countFireNear(ServerLevel level, BlockPos center) {
        int fire = 0;
        for (int dy = -2; dy <= 4; dy++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dx = -5; dx <= 5; dx++) {
                    if (level.getBlockState(center.offset(dx, dy, dz)).is(Blocks.FIRE)) {
                        fire++;
                    }
                }
            }
        }
        return fire;
    }
}
