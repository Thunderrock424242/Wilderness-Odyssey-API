package com.thunder.wildernessodysseyapi.watersystem.water.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.WaterCompatibilityRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
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
                .then(Commands.literal("authority")
                        .executes(context -> authority(context, 16))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(context -> authority(context,
                                        IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("compat")
                        .executes(WaterDebugCommand::compatibilityStatus))
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

    private static int shipcheck(CommandContext<CommandSourceStack> context, int requestedRadius) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (!WildernessWaterRules.isEnabled(level)) {
            source.sendSuccess(() -> Component.literal(
                    "WO water repair skipped: Wilderness Odyssey water is disabled by config or gamerule."
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
