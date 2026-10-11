package com.thunder.wildernessodysseyapi.anomaly.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.thunder.wildernessodysseyapi.anomaly.AnomalyAtmosphereSync;
import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyBlocks;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.anomaly.AnomalyIslandPlacement;
import com.thunder.wildernessodysseyapi.worldgen.anomaly.AnomalyTerrainFeature;
import com.thunder.wildernessodysseyapi.worldgen.anomaly.AnomalyZone;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only development tools; mutations use the same preflight as world generation. */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class AnomalyDebugCommand {
    private AnomalyDebugCommand() { }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("woanomaly").requires(source -> source.hasPermission(2))
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("reset").executes(context -> {
                    AnomalyAtmosphereSync.resetPreview(context.getSource().getPlayerOrException());
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "commands.wildernessodysseyapi.anomaly.reset"), false);
                    return 1;
                }))
                .then(Commands.literal("blocks").executes(context -> previewBlocks(context.getSource())))
                .then(Commands.literal("spawn_rift").executes(context -> {
                    context.getSource().getServer().getCommands().performPrefixedCommand(
                            context.getSource(), "wildernessrift debug_open_near");
                    return 1;
                }));
        var locate = Commands.literal("locate");
        var preview = Commands.literal("zone");
        var terrain = Commands.literal("terrain");
        for (AnomalyZone zone : AnomalyZone.values()) {
            locate.then(Commands.literal(zone.biomePath()).executes(context -> {
                context.getSource().getServer().getCommands().performPrefixedCommand(
                        context.getSource(), "locate biome wildernessodysseyapi:" + zone.biomePath());
                return 1;
            }));
            preview.then(Commands.literal(zone.biomePath())
                    .executes(context -> preview(context.getSource(), zone, zone.intensity()))
                    .then(Commands.argument("intensity", DoubleArgumentType.doubleArg(0, 1))
                            .executes(context -> preview(context.getSource(), zone,
                                    DoubleArgumentType.getDouble(context, "intensity")))));
            terrain.then(Commands.literal(zone.biomePath()).executes(context -> {
                var source = context.getSource();
                BlockPos playerPos = source.getPlayerOrException().blockPosition();
                BlockPos origin = new BlockPos(Math.floorDiv(playerPos.getX(), 16) * 16,
                        playerPos.getY(), Math.floorDiv(playerPos.getZ(), 16) * 16);
                boolean placed = AnomalyTerrainFeature.placeForZone(source.getLevel(), origin, zone,
                        source.getLevel().getSeed());
                source.sendSuccess(() -> Component.translatable(
                        "commands.wildernessodysseyapi.anomaly.terrain", zone.biomePath(),
                        origin.toShortString(), placed), true);
                return placed ? 1 : 0;
            }));
        }
        root.then(locate).then(preview).then(terrain);
        var place = Commands.literal("island");
        var inspect = Commands.literal("inspect_island");
        for (ResourceLocation template : AnomalyIslandPlacement.templates()) {
            place.then(Commands.literal(template.getPath())
                    .then(Commands.argument("position", BlockPosArgument.blockPos()).executes(context ->
                            island(context.getSource(), template,
                                    BlockPosArgument.getBlockPos(context, "position"), true))));
            inspect.then(Commands.literal(template.getPath())
                    .then(Commands.argument("position", BlockPosArgument.blockPos()).executes(context ->
                            island(context.getSource(), template,
                                    BlockPosArgument.getBlockPos(context, "position"), false))));
        }
        dispatcher.register(root.then(place).then(inspect));
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var payload = AnomalyAtmosphereSync.snapshot(player);
        String biome = source.getLevel().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("unknown");
        source.sendSuccess(() -> Component.translatable("commands.wildernessodysseyapi.anomaly.status",
                biome, payload.zone(), payload.intensity(), payload.riftIntensity()), false);
        return 1;
    }

    private static int preview(CommandSourceStack source, AnomalyZone zone, double intensity)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        AnomalyAtmosphereSync.preview(source.getPlayerOrException(), zone, intensity);
        source.sendSuccess(() -> Component.translatable("commands.wildernessodysseyapi.anomaly.preview",
                zone.biomePath(), Math.round(intensity * 100)), false);
        return 1;
    }

    private static int previewBlocks(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        for (var block : AnomalyBlocks.terrainBlocks()) {
            ItemStack stack = new ItemStack(block);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.translatable("commands.wildernessodysseyapi.anomaly.blocks"), false);
        return 1;
    }

    private static int island(CommandSourceStack source, ResourceLocation template, BlockPos origin, boolean place) {
        var result = place ? AnomalyIslandPlacement.place(source.getLevel(), template, origin)
                : AnomalyIslandPlacement.inspect(source.getLevel(), template, origin);
        if (!result.allowed()) {
            source.sendFailure(Component.translatable("commands.wildernessodysseyapi.anomaly.island_rejected",
                    result.reason(), result.detail()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(place
                        ? "commands.wildernessodysseyapi.anomaly.island_placed"
                        : "commands.wildernessodysseyapi.anomaly.island_clear",
                template.getPath(), origin.toShortString()), place);
        return 1;
    }
}
