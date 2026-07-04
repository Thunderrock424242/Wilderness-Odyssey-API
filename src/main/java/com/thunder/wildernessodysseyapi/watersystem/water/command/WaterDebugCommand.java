package com.thunder.wildernessodysseyapi.watersystem.water.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWaterMigrationQueue;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWaterSeeder;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
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
 * data or rewrite compatibility projection blocks. Read-only inspection remains
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
                .then(Commands.literal("authority")
                        .executes(context -> authority(context, 16))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(context -> authority(context,
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("seed")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> seed(context, 0))
                        .then(Commands.argument("chunkRadius", IntegerArgumentType.integer(0, 4))
                                .executes(context -> seed(context,
                                        IntegerArgumentType.getInteger(context, "chunkRadius")))))
                .then(Commands.literal("migration")
                        .executes(WaterDebugCommand::migrationStatus))
                .then(Commands.literal("visible")
                        .executes(context -> visibleReadiness(context, 2))
                        .then(Commands.argument("chunkRadius", IntegerArgumentType.integer(0, 4))
                                .executes(context -> visibleReadiness(context,
                                        IntegerArgumentType.getInteger(context, "chunkRadius")))))
                .then(Commands.literal("shipcheck")
                        .executes(context -> shipcheck(context, 16))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(context -> shipcheck(context,
                                        IntegerArgumentType.getInteger(context, "radius")))))
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
        source.sendSuccess(() -> Component.literal("  authority source=" + snapshot.authoritySource()
                + ", owned=" + snapshot.authorityOwned()
                + ", migrationCandidate=" + snapshot.migrationCandidate()
                + ", replacementSafe=" + snapshot.replacementSurfaceSafe()
                + ", volume=" + snapshot.authorityVolumeUnits()
                + ", fill=" + format(snapshot.authorityFillFraction())), false);
        source.sendSuccess(() -> Component.literal("  wet=" + snapshot.wet()
                + ", tagWater=" + snapshot.tagWater()
                + ", vanillaBlock=" + snapshot.vanillaWaterBlock()
                + ", wildernessBlock=" + snapshot.wildernessWaterBlock()
                + ", plainProjection=" + snapshot.plainProjectionBlock()
                + ", mobileWater=" + snapshot.mobileWater()), false);
        source.sendSuccess(() -> Component.literal("  canonical tracked=" + snapshot.canonicalTracked()
                + ", volume=" + snapshot.canonicalVolumeUnits()
                + "/" + com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk.UNITS_PER_BLOCK
                + ", fill=" + format(snapshot.fillFraction())
                + ", imported=" + snapshot.imported()
                + ", projected=" + snapshot.compatibilityProjected()
                + ", hosted=" + snapshot.hostedWater()), false);
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
        int tagWater = 0;
        int canonical = 0;
        int authorityOwned = 0;
        int migrationCandidates = 0;
        int hosted = 0;
        int mobile = 0;
        int projected = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius)
        )) {
            sampled++;
            WaterCompatibility.Snapshot snapshot = WaterCompatibility.describe(level, pos);
            if (snapshot.wet()) wet++;
            if (snapshot.tagWater()) tagWater++;
            if (snapshot.canonicalTracked()) canonical++;
            if (snapshot.authorityOwned()) authorityOwned++;
            if (snapshot.migrationCandidate()) migrationCandidates++;
            if (snapshot.hostedWater()) hosted++;
            if (snapshot.mobileWater()) mobile++;
            if (snapshot.compatibilityProjected()) projected++;
        }

        int finalRadius = radius;
        int finalSampled = sampled;
        int finalWet = wet;
        int finalTagWater = tagWater;
        int finalCanonical = canonical;
        int finalAuthorityOwned = authorityOwned;
        int finalMigrationCandidates = migrationCandidates;
        int finalHosted = hosted;
        int finalMobile = mobile;
        int finalProjected = projected;
        source.sendSuccess(() -> Component.literal("WO water summary radius=" + finalRadius
                + " sampled=" + finalSampled
                + " wet=" + finalWet
                + " tagWater=" + finalTagWater
                + " canonical=" + finalCanonical
                + " authorityOwned=" + finalAuthorityOwned
                + " pendingMigration=" + finalMigrationCandidates
                + " hosted=" + finalHosted
                + " projected=" + finalProjected
                + " mobile=" + finalMobile), false);
        return 1;
    }

    private static int authority(CommandContext<CommandSourceStack> context, int requestedRadius) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos center = sourceBlockPos(source);
        int radius = Math.min(requestedRadius, WaterSimulationConfig.debugCommandMaxRadius());
        AuthorityStats stats = scanAuthority(level, center, radius);

        source.sendSuccess(() -> Component.literal("WO water authority radius=" + radius
                + " state=" + stats.stateLabel()), false);
        source.sendSuccess(() -> Component.literal("  Coverage: sampled=" + stats.sampled()
                + ", wet=" + stats.wet()
                + ", owned=" + stats.authorityOwned()
                + ", ownerCoverage=" + stats.ownerCoverageLabel()
                + ", replacementSafe=" + stats.replacementSafe()), false);
        source.sendSuccess(() -> Component.literal("  Sources: canonical=" + stats.canonical()
                + ", canonicalHosted=" + stats.canonicalHosted()
                + ", wildernessProjection=" + stats.wildernessProjection()
                + ", vanillaPending=" + stats.vanillaPending()
                + ", hostedPending=" + stats.hostedTagged()
                + ", mobile=" + stats.mobile()), false);
        source.sendSuccess(() -> Component.literal("  Action: pendingImport=" + stats.pendingImport()
                + ", pendingHostedImport=" + stats.pendingHostedImport()
                + ", projectionGaps=" + stats.projectionGaps()
                + ", advice=" + stats.advice()), false);
        return Math.max(1, stats.wet());
    }

    private static AuthorityStats scanAuthority(ServerLevel level, BlockPos center, int radius) {
        int sampled = 0;
        int wet = 0;
        int authorityOwned = 0;
        int replacementSafe = 0;
        int canonical = 0;
        int canonicalHosted = 0;
        int wildernessProjection = 0;
        int vanillaPending = 0;
        int hostedTagged = 0;
        int pendingImport = 0;
        int pendingHostedImport = 0;
        int projectionGaps = 0;
        int mobile = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius)
        )) {
            sampled++;
            WaterCompatibility.Snapshot snapshot = WaterCompatibility.describe(level, pos);
            if (snapshot.wet()) wet++;
            if (snapshot.authorityOwned()) authorityOwned++;
            if (snapshot.replacementSurfaceSafe()) replacementSafe++;
            if (snapshot.canonicalWater()) canonical++;
            if (snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.CANONICAL_HOSTED) {
                canonicalHosted++;
            }
            if (snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.WILDERNESS_PROJECTION) {
                wildernessProjection++;
            }
            if (snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.VANILLA_MIGRATION_SOURCE) {
                vanillaPending++;
            }
            if (snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.HOSTED_TAGGED_WATER) {
                hostedTagged++;
            }
            boolean pendingHosted = snapshot.nonPlainTaggedWater() && !snapshot.hostedWater();
            if (snapshot.pendingCanonicalImport() && !pendingHosted) pendingImport++;
            if (pendingHosted) pendingHostedImport++;
            if (snapshot.projectionGap()) projectionGaps++;
            if (snapshot.mobileWater()) mobile++;
        }

        return new AuthorityStats(
                sampled,
                wet,
                authorityOwned,
                replacementSafe,
                canonical,
                canonicalHosted,
                wildernessProjection,
                vanillaPending,
                hostedTagged,
                pendingImport,
                pendingHostedImport,
                projectionGaps,
                mobile
        );
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
                chunk.getData(ModAttachments.CHUNK_DATA).markWaterFinalized();
            }
        }

        CanonicalWaterSeeder.SeedStats finalTotal = total;
        source.sendSuccess(() -> Component.literal("WO water seeded chunks=" + finalTotal.loadedChunks()
                + " columns=" + finalTotal.scannedColumns()
                + " importedCells=" + finalTotal.importedCells()
                + " hostedCells=" + finalTotal.hostedWaterCells()
                + " convertedBlocks=" + finalTotal.convertedBlocks()
                + " skippedTracked=" + finalTotal.skippedTracked()
                + " skippedWaterlogged=" + finalTotal.skippedWaterlogged()), true);
        return Math.max(1, total.importedCells() + total.convertedBlocks());
    }

    private static int migrationStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CanonicalWaterMigrationQueue.MigrationStatus status = CanonicalWaterMigrationQueue.status();
        CanonicalWaterMigrationQueue.TickResult lastTick = status.lastTick();

        source.sendSuccess(() -> Component.literal("WO water migration"), false);
        source.sendSuccess(() -> Component.literal("  Status: seeding=" + onOff(status.seedingEnabled())
                + ", block conversion=" + onOff(status.blockConversionEnabled())
                + ", queued chunks=" + status.queuedChunks()), false);
        source.sendSuccess(() -> Component.literal("  Totals: touched=" + status.touchedChunks()
                + ", completed=" + status.completedChunks()
                + ", imported=" + status.importedCells()
                + ", hosted=" + status.hostedWaterCells()
                + ", converted=" + status.convertedBlocks()), false);
        source.sendSuccess(() -> Component.literal("  Last tick: chunks=" + lastTick.touchedChunks()
                + ", columns=" + lastTick.scannedColumns()
                + ", imported=" + lastTick.importedCells()
                + ", hosted=" + lastTick.hostedWaterCells()
                + ", converted=" + lastTick.convertedBlocks()), false);
        source.sendSuccess(() -> Component.literal("  Player priority: radius=" + status.playerScanRadius()
                + ", effective=" + status.lastPlayerScanRadius()
                + ", requested=" + status.lastPlayerScanRequestedViewDistance()
                + ", serverView=" + status.lastPlayerScanServerViewDistance()
                + ", interval=" + status.playerScanIntervalTicks() + " ticks"
                + ", last checked=" + status.lastPlayerScanCheckedChunks()
                + ", last queued=" + status.lastPlayerScanQueuedChunks()
                + ", last promoted=" + status.lastPlayerScanPromotedChunks()
                + ", total queued=" + status.playerScanQueuedChunks()
                + ", total promoted=" + status.playerScanPromotedChunks()), false);
        CanonicalWaterMigrationQueue.TickResult visible = status.lastVisibleFinalization();
        source.sendSuccess(() -> Component.literal("  Visible finalization: enabled="
                + onOff(status.visibleFinalizationEnabled())
                + ", last chunks=" + visible.touchedChunks()
                + ", columns=" + visible.scannedColumns()
                + ", imported=" + visible.importedCells()
                + ", hosted=" + visible.hostedWaterCells()
                + ", converted=" + visible.convertedBlocks()
                + ", total chunks=" + status.visibleFinalizationTouchedChunks()
                + ", total completed=" + status.visibleFinalizationCompletedChunks()
                + ", total converted=" + status.visibleFinalizationConvertedBlocks()
                + ", budget misses=" + status.visibleFinalizationBudgetMisses()
                + ", skipped finalized=" + status.visibleFinalizationSkippedFinalizedChunks()), false);
        source.sendSuccess(() -> Component.literal("  Queue health: skipped unloaded="
                + status.skippedUnloadedChunks()
                + ", skipped finalized=" + status.skippedFinalizedChunks()
                + ", dropped=" + status.droppedChunks()), false);
        return Math.max(1, status.queuedChunks());
    }

    private static int visibleReadiness(CommandContext<CommandSourceStack> context, int chunkRadius) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ChunkPos center = source.getPlayerOrException().chunkPosition();
        VisibleWaterReadinessStats stats = scanVisibleReadiness(level, center, chunkRadius);

        source.sendSuccess(() -> Component.literal("WO visible water readiness chunkRadius=" + chunkRadius
                + " state=" + stats.stateLabel()), false);
        source.sendSuccess(() -> Component.literal("  Chunks: loaded=" + stats.loadedChunks()
                + ", finalized=" + stats.finalizedChunks()
                + ", queued=" + stats.queuedChunks()
                + ", unfinished=" + stats.unfinishedChunks()), false);
        source.sendSuccess(() -> Component.literal("  Pending vanilla: chunks=" + stats.pendingPlainChunks()
                + ", columns=" + stats.pendingPlainColumns()
                + ", blocks=" + stats.pendingPlainBlocks()
                + ", hosted tagged cells=" + stats.hostedTaggedCells()), false);
        source.sendSuccess(() -> Component.literal("  Advice: " + stats.advice()), false);
        return Math.max(1, stats.unfinishedChunks() + stats.pendingPlainBlocks());
    }

    private static VisibleWaterReadinessStats scanVisibleReadiness(
            ServerLevel level,
            ChunkPos center,
            int chunkRadius
    ) {
        int loadedChunks = 0;
        int finalizedChunks = 0;
        int queuedChunks = 0;
        int unfinishedChunks = 0;
        int pendingPlainChunks = 0;
        int pendingPlainColumns = 0;
        int pendingPlainBlocks = 0;
        int hostedTaggedCells = 0;

        for (int chunkX = center.x - chunkRadius; chunkX <= center.x + chunkRadius; chunkX++) {
            for (int chunkZ = center.z - chunkRadius; chunkZ <= center.z + chunkRadius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                loadedChunks++;
                boolean finalized = CanonicalWaterMigrationQueue.isChunkWaterFinalized(chunk);
                boolean queued = CanonicalWaterMigrationQueue.isQueued(level, chunk.getPos());
                CanonicalWaterSeeder.PendingPlainWaterStats pending =
                        CanonicalWaterSeeder.scanPendingPlainWater(
                                level,
                                chunk,
                                WaterSimulationConfig.worldSeedMaxColumnDepth()
                        );
                if (finalized) {
                    finalizedChunks++;
                }
                if (queued) {
                    queuedChunks++;
                }
                if (!finalized || pending.hasPendingPlainWater()) {
                    unfinishedChunks++;
                }
                if (pending.hasPendingPlainWater()) {
                    pendingPlainChunks++;
                    pendingPlainColumns += pending.pendingColumns();
                    pendingPlainBlocks += pending.pendingBlocks();
                }
                hostedTaggedCells += pending.hostedTaggedCells();
            }
        }

        return new VisibleWaterReadinessStats(
                loadedChunks,
                finalizedChunks,
                queuedChunks,
                unfinishedChunks,
                pendingPlainChunks,
                pendingPlainColumns,
                pendingPlainBlocks,
                hostedTaggedCells
        );
    }

    private static int shipcheck(CommandContext<CommandSourceStack> context, int requestedRadius) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos center = sourceBlockPos(source);
        int radius = Math.min(requestedRadius, WaterSimulationConfig.debugCommandMaxRadius());
        ShipCheckStats stats = scanShipCheck(level, center, radius);

        source.sendSuccess(() -> Component.literal("WO water shipcheck radius=" + radius
                + " state=" + stats.stateLabel()), false);
        source.sendSuccess(() -> Component.literal("  Coverage: sampled=" + stats.sampled()
                + ", wet=" + stats.wet()
                + ", tag water=" + stats.tagWater()
                + ", canonical=" + stats.canonicalWater()
                + ", mobile=" + stats.mobileWater()), false);
        source.sendSuccess(() -> Component.literal("  Ownership: Wilderness blocks="
                + stats.wildernessPlainWater()
                + ", vanilla pending=" + stats.vanillaPlainWater()
                + ", hosted safe=" + stats.hostedWater()
                + ", projected=" + stats.projected()), false);
        source.sendSuccess(() -> Component.literal("  Action items: pending import="
                + stats.pendingImport()
                + ", pending hosted import=" + stats.pendingHostedImport()
                + ", pending conversion=" + stats.pendingConversion()
                + ", projection gaps=" + stats.projectionGaps()), false);
        source.sendSuccess(() -> Component.literal("  Advice: " + stats.advice()), false);
        return Math.max(1, stats.wet());
    }

    private static ShipCheckStats scanShipCheck(ServerLevel level, BlockPos center, int radius) {
        int sampled = 0;
        int wet = 0;
        int tagWater = 0;
        int canonicalWater = 0;
        int mobileWater = 0;
        int vanillaPlainWater = 0;
        int wildernessPlainWater = 0;
        int hostedWater = 0;
        int projected = 0;
        int pendingImport = 0;
        int pendingHostedImport = 0;
        int pendingConversion = 0;
        int projectionGaps = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius)
        )) {
            sampled++;
            WaterCompatibility.Snapshot snapshot = WaterCompatibility.describe(level, pos);
            if (snapshot.wet()) wet++;
            if (snapshot.tagWater()) tagWater++;
            if (snapshot.canonicalWater()) canonicalWater++;
            if (snapshot.mobileWater()) mobileWater++;
            if (snapshot.vanillaWaterBlock()) vanillaPlainWater++;
            if (snapshot.wildernessWaterBlock()) wildernessPlainWater++;
            if (snapshot.hostedWater()) hostedWater++;
            if (snapshot.compatibilityProjected()) projected++;
            boolean pendingHosted = snapshot.nonPlainTaggedWater() && !snapshot.hostedWater();
            if (snapshot.pendingCanonicalImport() && !pendingHosted) pendingImport++;
            if (pendingHosted) pendingHostedImport++;
            if (snapshot.pendingPlainVanillaConversion()) pendingConversion++;
            if (snapshot.projectionGap()) projectionGaps++;
        }

        return new ShipCheckStats(
                sampled,
                wet,
                tagWater,
                canonicalWater,
                mobileWater,
                vanillaPlainWater,
                wildernessPlainWater,
                hostedWater,
                projected,
                pendingImport,
                pendingHostedImport,
                pendingConversion,
                projectionGaps
        );
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

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
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

    /** Local authority report for deciding whether visible water is Wilderness-owned yet. */
    private record AuthorityStats(
            int sampled,
            int wet,
            int authorityOwned,
            int replacementSafe,
            int canonical,
            int canonicalHosted,
            int wildernessProjection,
            int vanillaPending,
            int hostedTagged,
            int pendingImport,
            int pendingHostedImport,
            int projectionGaps,
            int mobile
    ) {
        private String stateLabel() {
            if (projectionGaps > 0) {
                return "PROJECTION_GAPS";
            }
            if (vanillaPending > 0 || pendingImport > 0 || pendingHostedImport > 0) {
                return "MIGRATING";
            }
            if (wet > 0 && authorityOwned == wet) {
                return replacementSafe == wet ? "WILDERNESS_OWNED" : "WILDERNESS_OWNED_HOSTED";
            }
            return wet == 0 ? "DRY" : "MIXED";
        }

        private String ownerCoverageLabel() {
            if (wet <= 0) {
                return "n/a";
            }
            return Math.round(authorityOwned * 100.0f / wet) + "%";
        }

        private String advice() {
            if (projectionGaps > 0) {
                return "run /wowater repair nearby";
            }
            if (vanillaPending > 0 || pendingImport > 0 || pendingHostedImport > 0) {
                return "wait for automatic migration or run /wowater seed 1";
            }
            if (wet == 0) {
                return "no water sampled";
            }
            if (mobile > 0 && authorityOwned < wet) {
                return "mobile SPH water is present; wait for it to settle into canonical cells";
            }
            return "local water is owned by Wilderness authority";
        }
    }

    /** Compact local readiness summary for ship-track water validation. */
    private record ShipCheckStats(
            int sampled,
            int wet,
            int tagWater,
            int canonicalWater,
            int mobileWater,
            int vanillaPlainWater,
            int wildernessPlainWater,
            int hostedWater,
            int projected,
            int pendingImport,
            int pendingHostedImport,
            int pendingConversion,
            int projectionGaps
    ) {
        private String stateLabel() {
            if (projectionGaps > 0) {
                return "ACTION_NEEDED";
            }
            if (pendingImport > 0 || pendingConversion > 0 || pendingHostedImport > 0) {
                return "MIGRATING";
            }
            return "CLEAN";
        }

        private String advice() {
            if (projectionGaps > 0) {
                return "run /wowater repair nearby, then inspect any remaining gap positions";
            }
            if (pendingImport > 0 || pendingConversion > 0 || pendingHostedImport > 0) {
                return "wait for automatic migration, or use /wowater seed 1 for a local force pass";
            }
            if (wet == 0) {
                return "no local water sampled";
            }
            return "local water ownership looks ready for visual testing";
        }
    }

    /** Chunk-level readiness report for visible generated-water takeover. */
    private record VisibleWaterReadinessStats(
            int loadedChunks,
            int finalizedChunks,
            int queuedChunks,
            int unfinishedChunks,
            int pendingPlainChunks,
            int pendingPlainColumns,
            int pendingPlainBlocks,
            int hostedTaggedCells
    ) {
        private String stateLabel() {
            if (loadedChunks <= 0) {
                return "NO_LOADED_CHUNKS";
            }
            if (pendingPlainBlocks > 0) {
                return "VANILLA_WATER_PENDING";
            }
            if (unfinishedChunks > 0 || queuedChunks > 0) {
                return "FINALIZING";
            }
            return "WILDERNESS_READY";
        }

        private String advice() {
            if (loadedChunks <= 0) {
                return "no loaded chunks were sampled";
            }
            if (pendingPlainBlocks > 0) {
                return "wait for visible finalization or run /wowater seed 1 nearby";
            }
            if (unfinishedChunks > 0 || queuedChunks > 0) {
                return "loaded chunks are queued/finalizing but no plain vanilla water leak was found";
            }
            return "visible loaded water has completed Wilderness takeover";
        }
    }
}
