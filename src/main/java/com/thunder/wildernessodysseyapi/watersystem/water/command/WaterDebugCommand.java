package com.thunder.wildernessodysseyapi.watersystem.water.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWaterSeeder;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Locale;

/**
 * Server command for inspecting and repairing replacement water ownership.
 *
 * <p>The mutating subcommands are operator-only because they can import chunk
 * data or rewrite vanilla compatibility blocks. Read-only inspection remains
 * available to any command source that can execute normal commands.</p>
 */
public final class WaterDebugCommand {

    private WaterDebugCommand() {
    }

    /** Registers the {@code /wowater} diagnostics command tree. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wowater")
                .then(Commands.literal("inspect")
                        .executes(context -> inspect(context, sourceBlockPos(context.getSource())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> inspect(context,
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.literal("summary")
                        .executes(context -> summary(context, 4))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(context -> summary(context,
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("seed")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> seed(context, 0))
                        .then(Commands.argument("chunkRadius", IntegerArgumentType.integer(0, 4))
                                .executes(context -> seed(context,
                                        IntegerArgumentType.getInteger(context, "chunkRadius")))))
                .then(Commands.literal("repair")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> repair(context, 4))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(context -> repair(context,
                                        IntegerArgumentType.getInteger(context, "radius"))))));
    }

    private static int inspect(CommandContext<CommandSourceStack> context, BlockPos pos) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        WaterCompatibility.Snapshot snapshot = WaterCompatibility.describe(level, pos);

        source.sendSuccess(() -> Component.literal("WO water @ " + formatPos(pos)), false);
        source.sendSuccess(() -> Component.literal("  wet=" + snapshot.wet()
                + ", vanillaWater=" + snapshot.vanillaWater()
                + ", plainWaterBlock=" + snapshot.plainWaterBlock()
                + ", mobileWater=" + snapshot.mobileWater()), false);
        source.sendSuccess(() -> Component.literal("  canonical tracked=" + snapshot.canonicalTracked()
                + ", volume=" + snapshot.canonicalVolumeUnits()
                + "/" + com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk.UNITS_PER_BLOCK
                + ", fill=" + format(snapshot.fillFraction())
                + ", imported=" + snapshot.imported()
                + ", projected=" + snapshot.compatibilityProjected()), false);
        source.sendSuccess(() -> Component.literal("  canonicalSpeed=" + format(snapshot.canonicalSpeed())
                + ", mobileSpeed=" + format(snapshot.mobileSpeed())), false);
        return 1;
    }

    private static int summary(CommandContext<CommandSourceStack> context, int requestedRadius) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos center = sourceBlockPos(source);
        int radius = Math.min(requestedRadius, WaterSimulationConfig.debugCommandMaxRadius());

        int sampled = 0;
        int wet = 0;
        int vanilla = 0;
        int canonical = 0;
        int mobile = 0;
        int projected = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius)
        )) {
            sampled++;
            WaterCompatibility.Snapshot snapshot = WaterCompatibility.describe(level, pos);
            if (snapshot.wet()) wet++;
            if (snapshot.vanillaWater()) vanilla++;
            if (snapshot.canonicalTracked()) canonical++;
            if (snapshot.mobileWater()) mobile++;
            if (snapshot.compatibilityProjected()) projected++;
        }

        int finalRadius = radius;
        int finalSampled = sampled;
        int finalWet = wet;
        int finalVanilla = vanilla;
        int finalCanonical = canonical;
        int finalMobile = mobile;
        int finalProjected = projected;
        source.sendSuccess(() -> Component.literal("WO water summary radius=" + finalRadius
                + " sampled=" + finalSampled
                + " wet=" + finalWet
                + " vanilla=" + finalVanilla
                + " canonical=" + finalCanonical
                + " projected=" + finalProjected
                + " mobile=" + finalMobile), false);
        return 1;
    }

    private static int seed(CommandContext<CommandSourceStack> context, int chunkRadius) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ChunkPos center = source.getPlayerOrException().chunkPosition();
        CanonicalWaterSeeder.SeedStats total = CanonicalWaterSeeder.SeedStats.EMPTY;

        for (int chunkX = center.x - chunkRadius; chunkX <= center.x + chunkRadius; chunkX++) {
            for (int chunkZ = center.z - chunkRadius; chunkZ <= center.z + chunkRadius; chunkZ++) {
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                total = total.plus(CanonicalWaterSeeder.seedChunk(
                        level,
                        chunk,
                        WaterSimulationConfig.worldSeedMaxColumnDepth()
                ).countedChunk());
            }
        }

        CanonicalWaterSeeder.SeedStats finalTotal = total;
        source.sendSuccess(() -> Component.literal("WO water seeded chunks=" + finalTotal.loadedChunks()
                + " columns=" + finalTotal.scannedColumns()
                + " importedCells=" + finalTotal.importedCells()
                + " skippedTracked=" + finalTotal.skippedTracked()
                + " skippedWaterlogged=" + finalTotal.skippedWaterlogged()), true);
        return Math.max(1, total.importedCells());
    }

    private static int repair(CommandContext<CommandSourceStack> context, int requestedRadius) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos center = sourceBlockPos(source);
        int radius = Math.min(requestedRadius, WaterSimulationConfig.debugCommandMaxRadius());

        int repaired = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius)
        )) {
            if (CanonicalWater.isTracked(level, pos)) {
                CanonicalWater.reprojectCompatibility(level, pos);
                repaired++;
            }
        }

        int finalRadius = radius;
        int finalRepaired = repaired;
        source.sendSuccess(() -> Component.literal("WO water repaired projected cells=" + finalRepaired
                + " radius=" + finalRadius), true);
        return Math.max(1, repaired);
    }

    private static BlockPos sourceBlockPos(CommandSourceStack source) {
        return BlockPos.containing(source.getPosition());
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
