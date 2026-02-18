package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.thunder.wildernessodysseyapi.worldgen.modpack.ModpackStructureRegistry;
import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

/**
 * Commands for listing/reloading/placing modpack NBT structures from config.
 */
public final class ModpackStructureCommand {
    private ModpackStructureCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("modpackstructures")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("scaffold")
                        .executes(ctx -> scaffoldAll(ctx.getSource()))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(ctx -> scaffoldOne(ctx.getSource(), ResourceLocationArgument.getId(ctx, "id")))))
                .then(Commands.literal("place")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(ctx -> place(ctx.getSource(), ResourceLocationArgument.getId(ctx, "id"),
                                        BlockPos.containing(ctx.getSource().getPosition()), null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> place(ctx.getSource(),
                                                ResourceLocationArgument.getId(ctx, "id"),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                null))
                                        .then(Commands.argument("alignToSurface", BoolArgumentType.bool())
                                                .executes(ctx -> place(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "id"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"),
                                                        BoolArgumentType.getBool(ctx, "alignToSurface"))))))));
    }

    private static int reload(CommandSourceStack source) {
        ModpackStructureRegistry.loadAll();
        int count = ModpackStructureRegistry.entries().size();
        source.sendSuccess(() -> Component.literal("Reloaded " + count + " modpack structures from "
                + ModpackStructureRegistry.rootDirectory()), true);
        return count;
    }

    private static int list(CommandSourceStack source) {
        Collection<ModpackStructureRegistry.Entry> entries = ModpackStructureRegistry.entries();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal(ChatFormatting.YELLOW
                    + "No modpack structures found. Add .nbt files under "
                    + ModpackStructureRegistry.rootDirectory()), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(ChatFormatting.GOLD + "Modpack structures:"), false);
        for (ModpackStructureRegistry.Entry entry : entries) {
            String line = " - " + entry.id() + ChatFormatting.GRAY + " (" + entry.nbtPath().getFileName()
                    + ", alignToSurface=" + entry.alignToSurface() + ")";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return entries.size();
    }

    private static int scaffoldAll(CommandSourceStack source) {
        int generated = ModpackStructureRegistry.generateAllWorldgenScaffolds();
        source.sendSuccess(() -> Component.literal("Generated worldgen datapack scaffold for " + generated
                + " structures at " + ModpackStructureRegistry.rootDirectory().resolve("generated_datapack")), true);
        return generated;
    }

    private static int scaffoldOne(CommandSourceStack source, ResourceLocation id) {
        boolean ok = ModpackStructureRegistry.generateWorldgenScaffold(id);
        if (!ok) {
            source.sendFailure(Component.literal("Unknown structure id or scaffold generation failed: " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Generated worldgen scaffold for " + id), true);
        return 1;
    }

    private static int place(CommandSourceStack source, ResourceLocation id, BlockPos pos, Boolean alignToSurfaceOverride) {
        ServerLevel level = source.getLevel();
        ModpackStructureRegistry.Entry entry = ModpackStructureRegistry.get(id).orElse(null);
        if (entry == null) {
            source.sendFailure(Component.literal("Unknown modpack structure id: " + id));
            return 0;
        }

        boolean alignToSurface = alignToSurfaceOverride != null ? alignToSurfaceOverride : entry.alignToSurface();
        NBTStructurePlacer.PlacementResult result = alignToSurface
                ? entry.placer().placeAnchored(level, pos)
                : entry.placer().place(level, pos);

        if (result == null) {
            source.sendFailure(Component.literal("Failed to place structure " + id + ". Check logs."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Placed " + id + " at "
                + result.origin().getX() + "," + result.origin().getY() + "," + result.origin().getZ()
                + " (alignToSurface=" + alignToSurface + ")"), true);
        return 1;
    }
}
