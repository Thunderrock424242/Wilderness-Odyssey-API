package com.thunder.wildernessodysseyapi.cinematic.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.cinematic.CinematicManager;
import com.thunder.wildernessodysseyapi.cinematic.CinematicPlaybackOptions;
import com.thunder.wildernessodysseyapi.cinematic.CinematicSequence;
import com.thunder.wildernessodysseyapi.cinematic.CinematicSequenceRegistry;
import com.thunder.wildernessodysseyapi.cinematic.CinematicStopReason;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Permission-gated developer playback commands under {@code /wo sequence}. */
public final class CinematicCommand {
    private CinematicCommand() {
    }

    /** Registers developer replay without mutating permanent story completion. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wo")
                .then(Commands.literal("sequence")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("play")
                                .then(Commands.argument("sequence", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                                CinematicSequenceRegistry.ids(), builder
                                        ))
                                        .executes(CinematicCommand::play)))
                        .then(Commands.literal("stop").executes(CinematicCommand::stop))));
    }

    private static int play(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String requested = StringArgumentType.getString(context, "sequence");
        ResourceLocation sequenceId = ResourceLocation.tryParse(requested);
        if (sequenceId == null) {
            context.getSource().sendFailure(Component.literal("Invalid cinematic id: " + requested));
            return 0;
        }
        if (sequenceId.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            sequenceId = ResourceLocation.fromNamespaceAndPath(
                    com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID,
                    sequenceId.getPath()
            );
        }

        CinematicSequence sequence = CinematicSequenceRegistry.get(sequenceId).orElse(null);
        if (sequence == null) {
            context.getSource().sendFailure(Component.literal("Unknown cinematic: " + sequenceId));
            return 0;
        }
        var anchor = sequence.findDeveloperAnchor(player);
        if (anchor.isEmpty()) {
            context.getSource().sendFailure(Component.literal(
                    "No compatible cinematic actor was found near the player."
            ));
            return 0;
        }

        CinematicManager.PlayResult result = CinematicManager.play(
                player,
                sequence,
                CinematicPlaybackOptions.developerReplay(anchor.get())
        );
        if (result.started()) {
            context.getSource().sendSuccess(result::message, false);
            return 1;
        }
        context.getSource().sendFailure(result.message());
        return 0;
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!CinematicManager.stop(player, CinematicStopReason.MANUAL_CANCEL)) {
            context.getSource().sendFailure(Component.literal("No cinematic is active."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Stopped the cinematic and restored player controls."
        ), false);
        return 1;
    }
}
