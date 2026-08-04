package com.thunder.wildernessodysseyapi.watersystem.water.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.WaterCompatibilityRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.ExistingWorldWaterConverter;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedSimulationDiagnostics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;

/**
 * Server command for inspecting and repairing replacement water ownership.
 *
 * <p>The repair subcommand is operator-only because it can rewrite missing
 * compatibility projections for sparse runtime cells. Read-only inspection
 * remains available to any command source that can execute normal commands.</p>
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
                .then(Commands.literal("watershed")
                        .executes(context -> watershed(context, sourceBlockPos(context.getSource())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> watershed(context,
                                        BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.literal("authority")
                        .executes(context -> authority(context, 16))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(context -> authority(context,
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("compat")
                        .executes(WaterDebugCommand::compatibilityStatus))
                .then(Commands.literal("mode")
                        .executes(WaterDebugCommand::authorityModeStatus)
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("on")
                                        .then(Commands.argument(
                                                        "radius",
                                                        IntegerArgumentType.integer(
                                                                1,
                                                                ExistingWorldWaterConverter.MAX_RADIUS
                                                        )
                                                )
                                                .executes(context -> enableAuthorityMode(
                                                        context,
                                                        IntegerArgumentType.getInteger(context, "radius")
                                                ))))
                                .then(Commands.literal("off")
                                        .executes(WaterDebugCommand::refuseAuthorityDisable))))
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
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("convert")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> convertExistingWater(
                                context,
                                ExistingWorldWaterConverter.DEFAULT_RADIUS
                        ))
                        .then(Commands.argument(
                                        "radius",
                                        IntegerArgumentType.integer(1, ExistingWorldWaterConverter.MAX_RADIUS)
                                )
                                .executes(context -> convertExistingWater(
                                        context,
                                        IntegerArgumentType.getInteger(context, "radius")
                                )))));
    }

    private static int inspect(CommandContext<CommandSourceStack> context, BlockPos pos) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        WaterCompatibility.Snapshot snapshot = WaterCompatibility.describe(level, pos);

        source.sendSuccess(() -> Component.literal("WO water @ " + formatPos(pos)), false);
        source.sendSuccess(() -> Component.literal("  authority source=" + snapshot.authoritySource()
                + ", owned=" + snapshot.authorityOwned()
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
                + ", hosted=" + snapshot.hostedWater()
                + ", sleeping=" + snapshot.sleeping()), false);
        source.sendSuccess(() -> Component.literal("  canonicalSpeed=" + format(snapshot.canonicalSpeed())
                + ", mobileSpeed=" + format(snapshot.mobileSpeed())), false);
        return 1;
    }

    /** Reports the authoritative watershed cell containing a loaded position. */
    private static int watershed(CommandContext<CommandSourceStack> context, BlockPos pos) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        WatershedConditions conditions = WaterServices.access().getWatershedConditions(level, pos);
        var localFlow = WaterServices.access().getLocalWatershedFlow(level, pos);
        WatershedSimulationDiagnostics.Snapshot diagnostics =
                WatershedSimulationDiagnostics.snapshot(level);

        source.sendSuccess(() -> Component.literal("WO watershed @ chunk "
                + (pos.getX() >> 4) + ", " + (pos.getZ() >> 4)), false);
        source.sendSuccess(() -> Component.literal("  basin="
                + Long.toUnsignedString(conditions.basinId(), 16)
                + ", feature=" + conditions.waterFeature()
                + ", averageElevation=" + conditions.averageTerrainElevation()
                + ", downstream=" + conditions.downstreamDirection()
                + ", accumulation=" + format(conditions.drainageAccumulation())), false);
        source.sendSuccess(() -> Component.literal("  rainfall=" + format(conditions.recentRainfall())
                + ", snowmelt=" + format(conditions.recentSnowmelt())
                + ", saturation=" + format(conditions.soilSaturation())
                + ", runoff=" + format(conditions.storedRunoff())
                + ", discharge=" + format(conditions.riverDischarge())), false);
        source.sendSuccess(() -> Component.literal("  levelOffset=" + format(conditions.waterLevelOffset())
                + ", floodRisk=" + format(conditions.floodRisk())
                + ", threshold=" + format(conditions.floodThreshold())
                + ", flooding=" + conditions.flooding()
                + ", temporaryCells=" + conditions.activeTemporaryFloodCells()), false);
        source.sendSuccess(() -> Component.literal("  current=" + format(conditions.currentX())
                + ", " + format(conditions.currentZ())
                + " (strength=" + format(conditions.currentStrength()) + ")"
                + ", sediment=" + format(conditions.sediment())
                + ", clarity=" + format(conditions.clarity())
                + ", debris=" + format(conditions.debris())), false);
        source.sendSuccess(() -> Component.literal("  localCell=" + localFlow.cell()
                + ", localDirection=" + localFlow.direction()
                + ", contributingCells=" + localFlow.contributingCells()
                + ", confluence=" + localFlow.confluence()
                + ", localCurrent=" + format(localFlow.currentX())
                + ", " + format(localFlow.currentZ())), false);
        source.sendSuccess(() -> Component.literal("  scheduler queued=" + diagnostics.queuedChunks()
                + ", processed=" + diagnostics.processedChunks()
                + ", initialized=" + diagnostics.initializedChunks()
                + ", placed=" + diagnostics.floodPlacements()
                + ", removed=" + diagnostics.floodRemovals()
                + ", activeFlood=" + diagnostics.activeFloodCells()
                + ", elapsedMicros=" + diagnostics.elapsedMicros()), false);
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
        int finalHosted = hosted;
        int finalMobile = mobile;
        int finalProjected = projected;
        source.sendSuccess(() -> Component.literal("WO water summary radius=" + finalRadius
                + " sampled=" + finalSampled
                + " wet=" + finalWet
                + " tagWater=" + finalTagWater
                + " canonical=" + finalCanonical
                + " authorityOwned=" + finalAuthorityOwned
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
                + ", generated=" + stats.generated()
                + ", wildernessProjection=" + stats.wildernessProjection()
                + ", vanillaTagged=" + stats.vanillaTagged()
                + ", externalTagged=" + stats.externalTagged()
                + ", hostedTagged=" + stats.hostedTagged()
                + ", mobile=" + stats.mobile()), false);
        source.sendSuccess(() -> Component.literal("  Action: projectionGaps=" + stats.projectionGaps()
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
        int generated = 0;
        int wildernessProjection = 0;
        int vanillaTagged = 0;
        int externalTagged = 0;
        int hostedTagged = 0;
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
            if (snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.GENERATED) {
                generated++;
            }
            if (snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.WILDERNESS_PROJECTION) {
                wildernessProjection++;
            }
            if (snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.VANILLA_TAGGED_WATER) {
                vanillaTagged++;
            }
            if (snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.EXTERNAL_TAGGED_WATER) {
                externalTagged++;
            }
            if (snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.HOSTED_TAGGED_WATER) {
                hostedTagged++;
            }
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
                generated,
                wildernessProjection,
                vanillaTagged,
                externalTagged,
                hostedTagged,
                projectionGaps,
                mobile
        );
    }

    private static int compatibilityStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("WO water compatibility"), false);
        source.sendSuccess(() -> Component.literal("  flags: entity="
                + onOff(WaterSimulationConfig.entityWaterCompatEnabled())
                + ", hydrodynamics=" + onOff(WaterSimulationConfig.entityHydrodynamicsEnabled())
                + ", bucket=" + onOff(WaterSimulationConfig.vanillaBucketCompatEnabled())
                + ", boat=" + onOff(WaterSimulationConfig.vanillaBoatCompatEnabled())
                + ", fishing=" + onOff(WaterSimulationConfig.fishingCompatEnabled())
                + ", structures=" + onOff(WaterSimulationConfig.structureWaterMarkersEnabled())
                + ", fluidHandlers=" + onOff(WaterSimulationConfig.fluidHandlerCompatEnabled())
                + ", create=" + onOff(WaterSimulationConfig.createWaterCompatEnabled())), false);
        for (WaterCompatibilityRegistry.AdapterStatus status : WaterCompatibilityRegistry.statuses()) {
            source.sendSuccess(() -> Component.literal("  " + status.id()
                    + ": level=" + status.compatibilityLevel()
                    + ", available=" + status.available()
                    + ", initialized=" + status.initialized()), false);
        }
        return Math.max(1, WaterCompatibilityRegistry.statuses().size());
    }

    private static int authorityModeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        WildernessWaterRules.ModeStatus status = WildernessWaterRules.status(source.getLevel());
        source.sendSuccess(() -> Component.literal("WO water authority mode"
                + " active=" + onOff(status.active())
                + ", gamerule=" + onOff(status.gameRule())
                + ", startupConfig=" + onOff(status.startupConfig())), false);
        source.sendSuccess(() -> Component.literal(
                "  Bare gamerule changes are restored. Use /wowater mode set on <radius> for verified activation."
        ), false);
        return status.active() ? 1 : 0;
    }

    private static int enableAuthorityMode(
            CommandContext<CommandSourceStack> context,
            int requestedRadius
    ) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        WildernessWaterRules.ModeStatus modeStatus = WildernessWaterRules.status(level);
        if (modeStatus.active()) {
            source.sendSuccess(() -> Component.literal(
                    "WO water authority is already ON; use /wowater convert <radius> for additional loaded areas."
            ), false);
            return 1;
        }
        if (!modeStatus.startupConfig()) {
            source.sendFailure(Component.literal(
                    "WO water activation refused: enable the server water-simulation master config, "
                            + "restart, then run this bounded activation command again."
            ));
            return 0;
        }

        BlockPos center = sourceBlockPos(source);
        ExistingWorldWaterConverter.ActivationPreflight preflight =
                ExistingWorldWaterConverter.preflightActivation(level, center, requestedRadius);
        if (!preflight.successful()) {
            source.sendFailure(Component.literal("WO water activation refused by preflight: unloadedColumns="
                    + preflight.unloadedColumns()
                    + ", invalidWater=" + preflight.invalidWater()
                    + ", mismatchedTrackedWater=" + preflight.mismatchedTrackedWater()
                    + ", capacityExceededChunks=" + preflight.capacityExceededChunks()));
            return 0;
        }

        // Stage canonical cells without changing physical blocks. If proof
        // fails, authority remains OFF and the still-vanilla world is safe.
        ExistingWorldWaterConverter.ConversionResult staged =
                ExistingWorldWaterConverter.stageLoadedForActivation(level, center, preflight);
        ExistingWorldWaterConverter.StagingVerification stagingVerification =
                ExistingWorldWaterConverter.verifyStaged(level, center, preflight.radius());
        if (!stagingVerification.successful()) {
            source.sendFailure(Component.literal("WO water activation refused after safe staging: "
                    + stagingVerification.mismatches()
                    + " canonical cells did not match their vanilla water blocks; authority remains OFF."));
            return 0;
        }

        WildernessWaterRules.enableAfterExplicitConversion(level);
        int projected = ExistingWorldWaterConverter.projectStaged(
                level, center, preflight.radius());
        ExistingWorldWaterConverter.ConversionVerification projectionVerification =
                ExistingWorldWaterConverter.verifyLoaded(level, center, preflight.radius());
        if (!projectionVerification.successful()) {
            source.sendFailure(Component.literal("WO water authority is ON, but projection verification found "
                    + projectionVerification.remainingVanillaWater()
                    + " remaining vanilla blocks. Run /wowater convert " + preflight.radius()
                    + " before leaving this area."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("WO water authority enabled and persisted: radius="
                + staged.radius()
                + " staged=" + staged.converted()
                + " projected=" + projected
                + " verified=" + projectionVerification.inspected()), true);
        return 1;
    }

    private static int refuseAuthorityDisable(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!WildernessWaterRules.status(source.getLevel()).active()) {
            source.sendSuccess(() -> Component.literal("WO water authority is already OFF."), false);
            return 1;
        }
        source.sendFailure(Component.literal(
                "WO water authority cannot be disabled automatically: canonical/generated water may exist in "
                        + "unloaded chunks. A world-wide rollback tool is required before ownership can be released."
        ));
        return 0;
    }

    private static int shipcheck(CommandContext<CommandSourceStack> context, int requestedRadius) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (!WildernessWaterRules.isEnabled(level)) {
            source.sendSuccess(() -> Component.literal(
                    "WO water shipcheck skipped: persisted Wilderness water authority is disabled."
            ), false);
            return 0;
        }
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
                + ", vanilla non-authoritative=" + stats.vanillaPlainWater()
                + ", external non-authoritative=" + stats.externalTaggedWater()
                + ", hosted safe=" + stats.hostedWater()
                + ", projected=" + stats.projected()), false);
        source.sendSuccess(() -> Component.literal("  Action items: projection gaps="
                + stats.projectionGaps()), false);
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
        int externalTaggedWater = 0;
        int hostedWater = 0;
        int projected = 0;
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
            if (snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.EXTERNAL_TAGGED_WATER
                    || snapshot.authoritySource() == WildernessWaterAuthority.WaterSource.HOSTED_TAGGED_WATER) {
                externalTaggedWater++;
            }
            if (snapshot.hostedWater()) hostedWater++;
            if (snapshot.compatibilityProjected()) projected++;
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
                externalTaggedWater,
                hostedWater,
                projected,
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

    private static int convertExistingWater(
            CommandContext<CommandSourceStack> context,
            int requestedRadius
    ) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (!WildernessWaterRules.isEnabled(level)) {
            source.sendFailure(Component.literal(
                    "WO water conversion requires an active persisted authority mode."
            ));
            return 0;
        }

        ExistingWorldWaterConverter.ConversionResult result =
                ExistingWorldWaterConverter.convertLoaded(level, sourceBlockPos(source), requestedRadius);
        source.sendSuccess(() -> Component.literal("WO water explicit conversion radius=" + result.radius()
                + " inspected=" + result.inspected()
                + " vanillaWater=" + result.vanillaWater()
                + " converted=" + result.converted()
                + " reprojected=" + result.reprojected()
                + " unloadedColumnsSkipped=" + result.unloadedColumns()), true);
        source.sendSuccess(() -> Component.literal(
                "  Only currently loaded blocks were inspected; no completed chunk was loaded or scanned automatically."
        ), false);
        return Math.max(1, result.converted() + result.reprojected());
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
            int generated,
            int wildernessProjection,
            int vanillaTagged,
            int externalTagged,
            int hostedTagged,
            int projectionGaps,
            int mobile
    ) {
        private String stateLabel() {
            if (projectionGaps > 0) {
                return "PROJECTION_GAPS";
            }
            if (wet > 0 && authorityOwned == wet) {
                return replacementSafe == wet ? "WILDERNESS_OWNED" : "WILDERNESS_OWNED_HOSTED";
            }
            if (vanillaTagged > 0 || externalTagged > 0 || hostedTagged > 0) {
                return "NON_AUTHORITATIVE_WATER_PRESENT";
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
            if (vanillaTagged > 0 || externalTagged > 0 || hostedTagged > 0) {
                return "vanilla or externally tagged water is non-authoritative in this phase";
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
            int externalTaggedWater,
            int hostedWater,
            int projected,
            int projectionGaps
    ) {
        private String stateLabel() {
            if (projectionGaps > 0) {
                return "ACTION_NEEDED";
            }
            if (vanillaPlainWater > 0 || externalTaggedWater > 0) {
                return "EXTERNAL_WATER_PRESENT";
            }
            return "CLEAN";
        }

        private String advice() {
            if (projectionGaps > 0) {
                return "run /wowater repair nearby, then inspect any remaining gap positions";
            }
            if (vanillaPlainWater > 0 || externalTaggedWater > 0) {
                return "existing-world and external-water conversion is intentionally deferred";
            }
            if (wet == 0) {
                return "no local water sampled";
            }
            return "local water ownership looks ready for visual testing";
        }
    }

}
