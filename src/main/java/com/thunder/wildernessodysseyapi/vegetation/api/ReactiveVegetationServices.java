package com.thunder.wildernessodysseyapi.vegetation.api;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.vegetation.client.ClientVegetationClimateStore;
import com.thunder.wildernessodysseyapi.vegetation.config.VegetationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Optional;

/**
 * Public query and vanilla-random-tick boundary for reactive vegetation.
 *
 * <p>Every query uses an already loaded chunk and its compact attachment. A
 * plant opting into vanilla random ticks therefore consults regional state,
 * never the localized-weather authority or a block scan.</p>
 */
public final class ReactiveVegetationServices {

    private ReactiveVegetationServices() {
    }

    /** Returns synchronized or server-owned climate without forcing a chunk load. */
    public static Optional<VegetationClimateState> climateAt(Level level, BlockPos position) {
        if (level == null || position == null) {
            return Optional.empty();
        }
        if (level.isClientSide) {
            return ClientVegetationClimateStore.stateAt(level, position);
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) {
            return Optional.empty();
        }
        return chunk.getExistingData(ModAttachments.REACTIVE_VEGETATION)
                .map(state -> state.snapshot());
    }

    /** Returns cached mushroom favorability, or zero outside an initialized loaded chunk. */
    public static double mushroomOpportunity(Level level, BlockPos position) {
        return climateAt(level, position)
                .map(VegetationClimateState::mushroomOpportunity)
                .orElse(0.0);
    }

    /**
     * Lets an opt-in custom block reuse regional state from its vanilla random tick.
     *
     * <p>The behavior may change properties only on the same block. This keeps
     * reactions visual, avoids neighbor-update storms, and prevents a
     * compatibility definition from replacing vegetation wholesale.</p>
     */
    public static PlantUpdateResult processRandomTick(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            long randomBits
    ) {
        Optional<VegetationClimateState> climate = climateAt(level, position);
        if (climate.isEmpty()) {
            return PlantUpdateResult.NOT_REGISTERED;
        }
        return processSelectedPlant(level, position, state, climate.get(), randomBits);
    }

    /** Processes one scheduler-selected position against its already sampled chunk climate. */
    public static PlantUpdateResult processSelectedPlant(
            ServerLevel level,
            BlockPos position,
            BlockState state,
            VegetationClimateState climate,
            long randomBits
    ) {
        Optional<ReactivePlantDefinition> definition = ReactivePlantRegistry.definition(state);
        if (definition.isEmpty()) {
            return PlantUpdateResult.NOT_REGISTERED;
        }
        ReactivePlantUpdateContext context = new ReactivePlantUpdateContext(
                level,
                position,
                state,
                climate,
                suitableDaylight(level, position),
                VegetationConfig.FLOWER_NIGHT_CLOSING.get(),
                VegetationConfig.FLOWER_WEATHER_CLOSING.get(),
                randomBits
        );
        BlockState desired = ReactivePlantRegistry.resolve(definition.get(), context);
        if (desired == state || desired.equals(state)) {
            return PlantUpdateResult.PROCESSED_UNCHANGED;
        }
        if (desired.getBlock() != state.getBlock() || !desired.canSurvive(level, position)) {
            return PlantUpdateResult.PROCESSED_UNCHANGED;
        }
        boolean changed = level.setBlock(position, desired, Block.UPDATE_CLIENTS);
        return new PlantUpdateResult(true, changed);
    }

    private static boolean suitableDaylight(ServerLevel level, BlockPos position) {
        BlockPos lightPosition = position.above();
        return level.dimensionType().hasSkyLight()
                && level.isDay()
                && level.canSeeSky(lightPosition)
                && level.getMaxLocalRawBrightness(lightPosition) >= 9;
    }

    /** Outcome used by diagnostics without exposing internal scheduler state. */
    public record PlantUpdateResult(boolean registered, boolean stateChanged) {
        private static final PlantUpdateResult NOT_REGISTERED = new PlantUpdateResult(false, false);
        private static final PlantUpdateResult PROCESSED_UNCHANGED = new PlantUpdateResult(true, false);
    }
}
