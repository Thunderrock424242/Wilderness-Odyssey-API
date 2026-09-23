
package com.thunder.wildernessodysseyapi.modpack.structure.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import com.thunder.wildernessodysseyapi.modpack.structure.ModpackStructureRegistry;
import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Commands for listing, reloading, placing, and scaffolding
 * modpack NBT structures from config.
 */
public final class ModpackStructureCommand {

    private static final int PLAYER_CLEARANCE = 6;

    private ModpackStructureCommand() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher) {

        var root = Commands.literal("modpackstructures")
                .requires(source -> source.hasPermission(2));

        // Reload structures from the config directory.
        root.then(Commands.literal("reload")
                .executes(ctx -> reload(ctx.getSource())));

        // List registered structures.
        root.then(Commands.literal("list")
                .executes(ctx -> list(ctx.getSource())));

        // Generate worldgen scaffolds.
        root.then(Commands.literal("scaffold")
                .executes(ctx -> scaffoldAll(ctx.getSource()))
                .then(Commands.argument(
                                "id",
                                ResourceLocationArgument.id())

                        .suggests(
                                ModpackStructureCommand::suggestStructures)

                        .executes(ctx -> scaffoldOne(
                                ctx.getSource(),
                                ResourceLocationArgument.getId(
                                        ctx, "id")
                        ))
                )
        );

        // Place structures.
        root.then(Commands.literal("place")
                .then(Commands.argument(
                                "id",
                                ResourceLocationArgument.id())

                        .suggests(
                                ModpackStructureCommand::suggestStructures)

                        // No position: place in front of the player.
                        .executes(ctx -> placeNearPlayer(
                                ctx.getSource(),
                                ResourceLocationArgument.getId(
                                        ctx, "id")
                        ))

                        // Explicit position.
                        .then(Commands.argument(
                                        "pos",
                                        BlockPosArgument.blockPos())

                                .executes(ctx -> place(
                                        ctx.getSource(),
                                        ResourceLocationArgument.getId(
                                                ctx, "id"),
                                        BlockPosArgument.getLoadedBlockPos(
                                                ctx, "pos"),
                                        null
                                ))

                                // Optional terrain alignment.
                                .then(Commands.argument(
                                                "alignToSurface",
                                                BoolArgumentType.bool())

                                        .executes(ctx -> place(
                                                ctx.getSource(),
                                                ResourceLocationArgument.getId(
                                                        ctx, "id"),
                                                BlockPosArgument.getLoadedBlockPos(
                                                        ctx, "pos"),
                                                BoolArgumentType.getBool(
                                                        ctx, "alignToSurface")
                                        ))
                                )
                        )
                )
        );

        dispatcher.register(root);
    }

    /**
     * Suggest all currently registered structure IDs.
     *
     * Suggestions update after /modpackstructures reload.
     */
    private static CompletableFuture<Suggestions> suggestStructures(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {

        return SharedSuggestionProvider.suggestResource(
                ModpackStructureRegistry.entries()
                        .stream()
                        .map(ModpackStructureRegistry.Entry::id),
                builder
        );
    }

    /**
     * Automatically place a structure in front of the player.
     *
     * Uses the template's dimensions to keep its footprint
     * away from the player's current position.
     */
    private static int placeNearPlayer(
            CommandSourceStack source,
            ResourceLocation id) {

        // Automatic placement requires an actual player.
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(
                    "Automatic placement requires a player. "
                            + "Specify coordinates when using the server console."
            ));
            return 0;
        }

        ModpackStructureRegistry.Entry entry =
                ModpackStructureRegistry.get(id).orElse(null);

        if (entry == null) {
            source.sendFailure(Component.literal(
                    "Unknown modpack structure id: " + id
            ));
            return 0;
        }

        ServerLevel level = source.getLevel();

        // Retrieve the actual NBT structure dimensions.
        Vec3i size = entry.placer().peekSize(level);

        int width = size.getX();
        int depth = size.getZ();

        if (width <= 0 || depth <= 0) {
            source.sendFailure(Component.literal(
                    "Could not read structure dimensions for "
                            + id + ". Check the NBT file and logs."
            ));
            return 0;
        }

        BlockPos playerPos = player.blockPosition();

        int playerX = playerPos.getX();
        int playerY = playerPos.getY();
        int playerZ = playerPos.getZ();

        // Center the structure along the perpendicular axis.
        int originX = playerX - width / 2;
        int originZ = playerZ - depth / 2;

        // Place the entire structure footprint in front
        // of the player, with a six-block gap.
        switch (player.getDirection()) {

            case NORTH -> {
                originZ = playerZ - depth - PLAYER_CLEARANCE;
            }

            case SOUTH -> {
                originZ = playerZ + PLAYER_CLEARANCE + 1;
            }

            case EAST -> {
                originX = playerX + PLAYER_CLEARANCE + 1;
            }

            case WEST -> {
                originX = playerX - width - PLAYER_CLEARANCE;
            }

            default -> {
                source.sendFailure(Component.literal(
                        "Could not determine player direction."
                ));
                return 0;
            }
        }

        BlockPos desiredOrigin = new BlockPos(
                originX,
                playerY,
                originZ
        );

        /*
         * Anchored placement interprets the requested position
         * as the leveling-marker location, not always the
         * structure's origin.
         *
         * Adjust the anchor so the structure's footprint
         * remains in front of the player.
         */
        BlockPos placementPos = desiredOrigin;

        if (entry.alignToSurface()) {

            BlockPos levelingOffset =
                    entry.placer().peekLevelingOffset(level);

            if (levelingOffset != null) {
                placementPos = desiredOrigin.offset(
                        levelingOffset
                );
            }
        }

        // Reuse the existing placement method.
        return place(
                source,
                id,
                placementPos,
                null
        );
    }

    private static int reload(CommandSourceStack source) {

        ModpackStructureRegistry.loadAll();

        int count = ModpackStructureRegistry.entries().size();

        source.sendSuccess(
                () -> Component.literal(
                        "Reloaded " + count
                                + " modpack structures from "
                                + ModpackStructureRegistry.rootDirectory()
                ),
                true
        );

        return count;
    }

    private static int list(CommandSourceStack source) {

        Collection<ModpackStructureRegistry.Entry> entries =
                ModpackStructureRegistry.entries();

        if (entries.isEmpty()) {

            source.sendSuccess(
                    () -> Component.literal(
                            ChatFormatting.YELLOW
                                    + "No modpack structures found. "
                                    + "Add .nbt files under "
                                    + ModpackStructureRegistry.rootDirectory()
                    ),
                    false
            );

            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        ChatFormatting.GOLD + "Modpack structures:"
                ),
                false
        );

        for (ModpackStructureRegistry.Entry entry : entries) {

            String line = " - " + entry.id()
                    + ChatFormatting.GRAY
                    + " (" + entry.nbtPath().getFileName()
                    + ", alignToSurface="
                    + entry.alignToSurface()
                    + ")";

            source.sendSuccess(
                    () -> Component.literal(line),
                    false
            );
        }

        return entries.size();
    }

    private static int scaffoldAll(CommandSourceStack source) {

        int generated =
                ModpackStructureRegistry.generateAllWorldgenScaffolds();

        source.sendSuccess(
                () -> Component.literal(
                        "Generated worldgen datapack scaffold for "
                                + generated
                                + " structures at "
                                + ModpackStructureRegistry.rootDirectory()
                                .resolve("generated_datapack")
                ),
                true
        );

        return generated;
    }

    private static int scaffoldOne(
            CommandSourceStack source,
            ResourceLocation id) {

        boolean ok =
                ModpackStructureRegistry.generateWorldgenScaffold(id);

        if (!ok) {

            source.sendFailure(Component.literal(
                    "Unknown structure id or scaffold generation failed: "
                            + id
            ));

            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Generated worldgen scaffold for " + id
                ),
                true
        );

        return 1;
    }

    /**
     * Place a registered structure at a requested position.
     *
     * This preserves the existing explicit-position behavior.
     */
    private static int place(
            CommandSourceStack source,
            ResourceLocation id,
            BlockPos pos,
            Boolean alignToSurfaceOverride) {

        ServerLevel level = source.getLevel();

        ModpackStructureRegistry.Entry entry =
                ModpackStructureRegistry.get(id).orElse(null);

        if (entry == null) {

            source.sendFailure(Component.literal(
                    "Unknown modpack structure id: " + id
            ));

            return 0;
        }

        boolean alignToSurface =
                alignToSurfaceOverride != null
                        ? alignToSurfaceOverride
                        : entry.alignToSurface();

        NBTStructurePlacer.PlacementResult result =
                alignToSurface
                        ? entry.placer().placeAnchored(level, pos)
                        : entry.placer().place(level, pos);

        if (result == null) {

            source.sendFailure(Component.literal(
                    "Failed to place structure "
                            + id + ". Check logs."
            ));

            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Placed " + id + " at "
                                + result.origin().getX() + ","
                                + result.origin().getY() + ","
                                + result.origin().getZ()
                                + " (alignToSurface="
                                + alignToSurface + ")"
                ),
                true
        );

        return 1;
    }
}