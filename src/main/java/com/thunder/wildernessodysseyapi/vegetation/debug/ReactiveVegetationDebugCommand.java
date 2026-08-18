package com.thunder.wildernessodysseyapi.vegetation.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactivePlantRegistry;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactivePlantTrait;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactiveVegetationServices;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.simulation.ReactiveVegetationScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Operator diagnostics for regional climate, work budgets, and compatibility registration. */
public final class ReactiveVegetationDebugCommand {

    private ReactiveVegetationDebugCommand() {
    }

    /** Registers the permission-gated {@code /wilderness vegetation} command tree. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wilderness")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("vegetation")
                        .then(Commands.literal("sample")
                                .executes(context -> sample(context.getSource())))
                        .then(Commands.literal("stats")
                                .executes(context -> stats(context.getSource())))
                        .then(Commands.literal("registry")
                                .executes(context -> registry(context.getSource())))));
    }

    private static int sample(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos position = BlockPos.containing(source.getPosition());
        VegetationClimateState state = ReactiveVegetationServices.climateAt(level, position).orElse(null);
        if (state == null) {
            source.sendFailure(Component.literal(
                    "This loaded chunk has not completed a reactive vegetation update yet."
            ));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Vegetation climate at chunk %d,%d: moisture %.3f, recent rain %.3f, drought %.3f, storm %.3f, season %s, mushroom opportunity %.3f; last climate %d, last plant pass %d, plants processed %d, average %.2f us.",
                position.getX() >> 4,
                position.getZ() >> 4,
                state.moisture(),
                state.recentRainfall(),
                state.droughtLevel(),
                state.stormIntensity(),
                state.seasonState().name().toLowerCase(Locale.ROOT),
                state.mushroomOpportunity(),
                state.lastClimateUpdateTick(),
                state.lastVegetationUpdateTick(),
                state.plantsProcessed(),
                state.averageProcessingMicros()
        )), false);
        return 1;
    }

    private static int stats(CommandSourceStack source) {
        ReactiveVegetationScheduler.Diagnostics diagnostics =
                ReactiveVegetationScheduler.diagnostics(source.getLevel());
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Vegetation scheduler: %d loaded, %d scheduled; tick %d processed %d chunks, %d bounded samples, %d registered plants, %d visual state changes; average chunk pass %.2f us.",
                diagnostics.loadedChunks(),
                diagnostics.scheduledChunks(),
                diagnostics.tick(),
                diagnostics.chunksProcessed(),
                diagnostics.sampleAttempts(),
                diagnostics.plantsProcessed(),
                diagnostics.blockStateChanges(),
                diagnostics.averageChunkProcessingMicros()
        )), false);
        return diagnostics.loadedChunks();
    }

    private static int registry(CommandSourceStack source) {
        Map<ReactivePlantTrait, Integer> traits = new EnumMap<>(ReactivePlantTrait.class);
        ReactivePlantRegistry.registrations().forEach((block, definition) -> {
            for (ReactivePlantTrait trait : definition.traits()) {
                traits.merge(trait, 1, Integer::sum);
            }
        });
        String examples = ReactivePlantRegistry.registrations().keySet().stream()
                .map(BuiltInRegistries.BLOCK::getKey)
                .filter(id -> id != null && !id.equals(ResourceLocation.withDefaultNamespace("air")))
                .limit(8)
                .map(ResourceLocation::toString)
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        source.sendSuccess(() -> Component.literal(
                "Reactive plants: " + ReactivePlantRegistry.registrations().size()
                        + " blocks, traits " + traits + "; examples: " + examples
        ), false);
        return ReactivePlantRegistry.registrations().size();
    }
}
