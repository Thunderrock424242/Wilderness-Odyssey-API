package com.thunder.wildernessodysseyapi.weather.simulation;

import com.mojang.authlib.GameProfile;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.integration.VanillaWeatherCommandAdapter;
import com.thunder.wildernessodysseyapi.weather.integration.LocalizedPrecipitationController;
import com.thunder.wildernessodysseyapi.weather.storage.AtmosphereSavedData;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.AtmosphericWaterExchange;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RegionalHydrologySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/** Real vanilla precipitation and authority commit regressions for weather ownership. */
@GameTestHolder("wildernessodysseyapi_weather_tests")
@PrefixGameTestTemplate(false)
public final class WeatherRemediationGameTests {
    private WeatherRemediationGameTests() {
    }

    @GameTest(template = "water_flow", batch = "weather_remediation", skyAccess = true)
    public static void modeledSnowDoesNotCreateHarvestableVanillaLayers(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos snow = helper.absolutePos(new BlockPos(8, 3, 8));
            int previousSnowHeight = level.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);
            try (Fixture fixture = new Fixture(level)) {
                level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT).set(1, level.getServer());
                level.setBlock(snow.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(snow, Blocks.AIR.defaultBlockState(), 3);
                WeatherAuthority.get().forcePrecipitation(level, snow, PrecipitationType.SNOW);
                helper.assertTrue(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, snow).equals(snow),
                        "Snow fixture's precipitation heightmap did not reach the expected open column");
                helper.assertTrue(LocalizedPrecipitationController.get().isExposedToPrecipitation(level, snow),
                        "Snow fixture did not receive exposed localized precipitation");
                helper.assertTrue(!AtmosphericWaterExchange.query(level, new ChunkPos(snow)).supported(),
                        "Legacy snowfall control unexpectedly had a modeled reservoir");
                level.tickPrecipitation(snow);
                helper.assertTrue(level.getBlockState(snow).is(Blocks.SNOW),
                        "Unmodeled terrain must retain vanilla snow accumulation");
                level.setBlock(snow, Blocks.AIR.defaultBlockState(), 3);

                AtmosphericWaterExchange.publish(level, new ChunkPos(snow), 0, 0,
                        AtmosphericWaterExchange.MILLI_UNITS_PER_CUBIC_METRE, 256, level.getGameTime());
                var before = AtmosphericWaterExchange.query(level, new ChunkPos(snow));
                helper.assertTrue(before.supported(), "Modeled snow fixture did not publish its real reservoir");
                level.tickPrecipitation(snow);
                helper.assertTrue(level.getBlockState(snow).isAir(),
                        "Vanilla precipitation created harvestable snow without debiting modeled SWE");
                helper.assertTrue(AtmosphericWaterExchange.query(level, new ChunkPos(snow)).equals(before),
                        "Suppressing collectible snow must preserve the modeled snow reservoir");
                level.setBlock(snow, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2), 3);
                level.tickPrecipitation(snow);
                helper.assertTrue(level.getBlockState(snow).getValue(SnowLayerBlock.LAYERS) == 2,
                        "Pre-existing vanilla snow must remain unchanged on modeled terrain");
                helper.succeed();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not isolate the weather regression fixture", exception);
            } finally {
                level.getGameRules().getRule(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT)
                        .set(previousSnowHeight, level.getServer());
            }
        });
    }

    @GameTest(template = "water_flow", batch = "weather_remediation")
    public static void activeFootprintAbovePersistenceCapStillCommits(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            ServerLevel level = helper.getLevel();
            int previousCap = WeatherConfig.MAX_PERSISTED_CELLS.get();
            int previousRadius = WeatherConfig.ACTIVE_SIMULATION_RADIUS.get();
            FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "weather-cap-test"));
            player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(8, 3, 8)));
            try (Fixture fixture = new Fixture(level)) {
                WeatherConfig.MAX_PERSISTED_CELLS.set(64);
                WeatherConfig.ACTIVE_SIMULATION_RADIUS.set(3);
                level.players().add(player);
                Constructor<WeatherAuthority> constructor = WeatherAuthority.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                WeatherAuthority authority = constructor.newInstance();
                AtmosphereCellKey center = AtmosphereCellKey.fromBlock(player.getBlockX(), player.getBlockZ(),
                        WeatherConfig.scheduling().cellSize());
                long earlierTick = Math.max(0L, level.getGameTime() - 40L);
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        fixture.grid.restore(new AtmosphereView(new AtmosphereCellKey(center.x() + x, center.z() + z),
                                WeatherSample.CLEAR, 0, earlierTick, earlierTick));
                    }
                }
                var batch = authority.prepareOptionalMaintenance(level, true).simulationBatch();
                helper.assertTrue(batch != null && batch.cellCount() == 81,
                        "Radius-three player footprint must capture its complete 81-cell interpolation region");
                var result = authority.calculateSimulationBatch(batch);
                helper.assertTrue(authority.isSimulationResultCurrent(level, result),
                        "The freshly calculated authority batch was unexpectedly stale");
                helper.assertTrue(authority.applySimulationResult(level, result),
                        "Persistence-cap lookup evicted captured active inputs before their commit");
                helper.assertTrue(fixture.grid.size() == 81,
                        "The active footprint must remain available above the persistence cap");
                AtmosphereView committed = fixture.grid.view(center);
                helper.assertTrue(committed != null && committed.lastSimulatedTick() > earlierTick,
                        "The real commit did not advance physical simulation time");
                helper.assertTrue(fixture.data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess())
                                .getLongArray("cellKeys").length == 64,
                        "Active runtime protection must not broaden the serialized persistence cap");
                AtmosphereSavedData.get(level);
                helper.assertTrue(fixture.grid.size() == 81,
                        "An ordinary SavedData lookup must not evict the protected active footprint");
                for (int index = 0; index < 100; index++) {
                    fixture.grid.restore(new AtmosphereView(new AtmosphereCellKey(center.x() + 50 + index, center.z()),
                            WeatherSample.CLEAR, 0, earlierTick, earlierTick));
                }
                authority.onConfigurationReload();
                var trimmed = authority.prepareOptionalMaintenance(level, true).simulationBatch();
                helper.assertTrue(trimmed != null && trimmed.cellCount() == 81 && fixture.grid.size() == 81,
                        "Admission must trim dormant history while preserving the larger active footprint");
                level.players().remove(player);
                authority.onConfigurationReload();
                var dormant = authority.prepareOptionalMaintenance(level, true).simulationBatch();
                helper.assertTrue(dormant != null && dormant.cellCount() == 64 && fixture.grid.size() == 64,
                        "Without active interest, retained runtime history must return to the configured cap");
                helper.succeed();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not isolate the weather regression fixture", exception);
            } finally {
                level.players().remove(player);
                WeatherConfig.MAX_PERSISTED_CELLS.set(previousCap);
                WeatherConfig.ACTIVE_SIMULATION_RADIUS.set(previousRadius);
            }
        });
    }

    @GameTest(template = "water_flow", batch = "weather_remediation")
    public static void vanillaCommandRetainsOnlyCurrentFootprintWhileTraveling(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            ServerLevel level = helper.getLevel();
            int previousCap = WeatherConfig.MAX_PERSISTED_CELLS.get();
            int previousRadius = WeatherConfig.ACTIVE_SIMULATION_RADIUS.get();
            FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "weather-travel-test"));
            player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(8, 3, 8)));
            try (Fixture fixture = new Fixture(level)) {
                WeatherConfig.MAX_PERSISTED_CELLS.set(64);
                WeatherConfig.ACTIVE_SIMULATION_RADIUS.set(3);
                level.players().add(player);
                Constructor<WeatherAuthority> constructor = WeatherAuthority.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                WeatherAuthority authority = constructor.newInstance();
                authority.applyVanillaCommandWeather(level, VanillaWeatherCommandAdapter.State.RAIN, 12_000);
                for (int step = 0; step < 4; step++) {
                    player.setPos(player.getX() + WeatherConfig.scheduling().cellSize() * 12.0,
                            player.getY(), player.getZ());
                    authority.onConfigurationReload();
                    var preparation = authority.prepareOptionalMaintenance(level, true);
                    helper.assertTrue(preparation.simulationBatch() == null,
                            "A live vanilla override must retain command ownership while traveling");
                    helper.assertTrue(fixture.grid.size() == 81,
                            "Vanilla command travel retained dormant footprints beyond the active footprint/cap");
                    AtmosphereCellKey center = AtmosphereCellKey.fromBlock(player.getBlockX(), player.getBlockZ(),
                            WeatherConfig.scheduling().cellSize());
                    helper.assertTrue(fixture.grid.view(center) != null
                                    && fixture.grid.view(center).sample().precipitationIntensity() > 0,
                            "Retention removed the newly active command-weather region");
                }
                helper.assertTrue(fixture.data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess())
                                .getLongArray("cellKeys").length == 64,
                        "Command weather must preserve the serialized persistence cap");
                helper.succeed();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not isolate the command-weather regression fixture", exception);
            } finally {
                level.players().remove(player);
                WeatherConfig.MAX_PERSISTED_CELLS.set(previousCap);
                WeatherConfig.ACTIVE_SIMULATION_RADIUS.set(previousRadius);
            }
        });
    }

    @GameTest(template = "water_flow", batch = "weather_remediation")
    public static void unchangedVanillaCommandInvalidatesPendingAutonomousWork(GameTestHelper helper) {
        helper.runAfterDelay(2, () -> {
            ServerLevel level = helper.getLevel();
            try (Fixture fixture = new Fixture(level)) {
                Constructor<WeatherAuthority> constructor = WeatherAuthority.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                WeatherAuthority authority = constructor.newInstance();
                AtmosphereCellKey key = AtmosphereCellKey.fromBlock(helper.absolutePos(BlockPos.ZERO).getX(),
                        helper.absolutePos(BlockPos.ZERO).getZ(), WeatherConfig.scheduling().cellSize());
                WeatherSample clear = VanillaWeatherCommandAdapter.apply(WeatherSample.CLEAR,
                        VanillaWeatherCommandAdapter.State.CLEAR);
                fixture.grid.restore(new AtmosphereView(key, clear, 0,
                        Math.max(0L, level.getGameTime() - 40L), 0));
                var batch = authority.prepareOptionalMaintenance(level, true).simulationBatch();
                helper.assertTrue(batch != null, "Expected an autonomous batch before command ownership");
                var result = authority.calculateSimulationBatch(batch);
                helper.assertTrue(authority.isSimulationResultCurrent(level, result),
                        "Control result was already stale before the vanilla command");
                helper.assertTrue(authority.applyVanillaCommandWeather(level,
                                VanillaWeatherCommandAdapter.State.CLEAR, 12_000) == 0,
                        "Fixture must exercise command ownership without a weather-value revision");
                helper.assertTrue(!authority.applySimulationResult(level, result),
                        "An autonomous batch committed after an unchanged vanilla command took ownership");
                helper.succeed();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not isolate pending command-weather work", exception);
            }
        });
    }

    /** Isolates only test-owned state within one synchronous server-thread callback. */
    private static final class Fixture implements AutoCloseable {
        private final AtmosphereSavedData data;
        private final AtmosphereGrid grid;
        private final List<AtmosphereView> previousCells;
        private final RegionalHydrologySavedData hydrology;
        private final Field receiptField;
        private final Object previousReceipts;

        private Fixture(ServerLevel level) throws ReflectiveOperationException {
            data = AtmosphereSavedData.get(level);
            grid = data.grid();
            previousCells = grid.views();
            grid.clear();
            hydrology = RegionalHydrologySavedData.get(level);
            receiptField = RegionalHydrologySavedData.class.getDeclaredField("atmosphereExchange");
            receiptField.setAccessible(true);
            previousReceipts = receiptField.get(hydrology);
            receiptField.set(hydrology, new AtmosphericWaterExchange.ReceiptBook());
        }

        @Override
        public void close() {
            grid.clear();
            previousCells.forEach(grid::restore);
            try {
                receiptField.set(hydrology, previousReceipts);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not restore fixture receipts", exception);
            }
        }
    }
}
