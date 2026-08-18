package com.thunder.wildernessodysseyapi.vegetation.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Immutable context for one selected registered plant.
 *
 * @param level loaded authoritative server level
 * @param position selected plant position
 * @param state current plant block state
 * @param climate cached chunk-level vegetation climate
 * @param suitableDaylight whether the plant has suitable current daylight
 * @param flowerNightClosing whether configuration enables night closure
 * @param flowerWeatherClosing whether configuration enables storm closure
 * @param randomBits deterministic bits assigned to this bounded attempt
 */
public record ReactivePlantUpdateContext(
        ServerLevel level,
        BlockPos position,
        BlockState state,
        VegetationClimateState climate,
        boolean suitableDaylight,
        boolean flowerNightClosing,
        boolean flowerWeatherClosing,
        long randomBits
) {

    /** Validates live references while normalizing the immutable state inputs. */
    public ReactivePlantUpdateContext {
        level = Objects.requireNonNull(level, "level");
        position = Objects.requireNonNull(position, "position").immutable();
        state = Objects.requireNonNull(state, "state");
        climate = climate == null ? VegetationClimateState.DEFAULT : climate;
    }

    /** Returns whether a registered flower should currently be open. */
    public boolean flowerShouldBeOpen() {
        return ReactivePlantPolicy.flowerShouldOpen(
                climate,
                suitableDaylight,
                flowerNightClosing,
                flowerWeatherClosing
        );
    }

    /** Returns the cached regional opportunity for mushroom growth or spread. */
    public double mushroomOpportunity() {
        return climate.mushroomOpportunity();
    }

    /** Returns a deterministic chance decision without allocating a random generator. */
    public boolean roll(double chance) {
        double bounded = Math.max(0.0, Math.min(1.0, Double.isFinite(chance) ? chance : 0.0));
        double value = (randomBits >>> 11) * 0x1.0p-53;
        return value < bounded;
    }
}
