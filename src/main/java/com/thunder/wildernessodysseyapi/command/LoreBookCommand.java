package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookConfig;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Optional;

public class LoreBookCommand {
    private static final SuggestionProvider<CommandSourceStack> LORE_ID_SUGGESTIONS = (context, builder) -> {
        for (LoreBookConfig.LoreBookEntry entry : LoreBookManager.config().books()) {
            if (entry.id() != null) {
                builder.suggest(entry.id());
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lorebook")
                .then(Commands.literal("retrieve")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests(LORE_ID_SUGGESTIONS)
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.literal("Players only."));
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(context, "id");
                                    if (!LoreBookManager.hasCollected(player, id)) {
                                        source.sendFailure(Component.literal("You have not discovered that lore book yet."));
                                        return 0;
                                    }
                                    Optional<LoreBookConfig.LoreBookEntry> entry = LoreBookManager.config().books().stream()
                                            .filter(book -> id.equals(book.id()))
                                            .findFirst();
                                    if (entry.isEmpty()) {
                                        source.sendFailure(Component.literal("Unknown lore book id."));
                                        return 0;
                                    }
                                    giveBook(player, entry.get());
                                    source.sendSuccess(() -> Component.literal("Lore book retrieved."), false);
                                    return 1;
                                })))
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .suggests(LORE_ID_SUGGESTIONS)
                                        .executes(context -> {
                                            String id = StringArgumentType.getString(context, "id");
                                            Optional<LoreBookConfig.LoreBookEntry> entry = LoreBookManager.config().books().stream()
                                                    .filter(book -> id.equals(book.id()))
                                                    .findFirst();
                                            if (entry.isEmpty()) {
                                                context.getSource().sendFailure(Component.literal("Unknown lore book id."));
                                                return 0;
                                            }
                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
                                            for (ServerPlayer player : players) {
                                                giveBook(player, entry.get());
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("Lore book given."), true);
                                            return players.size();
                                        }))))
                .then(Commands.literal("give_next")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> {
                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
                                    int given = 0;
                                    for (ServerPlayer player : players) {
                                        Optional<LoreBookConfig.LoreBookEntry> entry = LoreBookManager.nextEntry(player);
                                        if (entry.isPresent()) {
                                            giveBook(player, entry.get());
                                            given++;
                                        }
                                    }
                                    if (given == 0) {
                                        context.getSource().sendFailure(Component.literal("No pending lore books for targets."));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("Next lore books delivered."), true);
                                    return given;
                                }))));
    }

    private static void giveBook(ServerPlayer player, LoreBookConfig.LoreBookEntry entry) {
        player.getInventory().placeItemBackInInventory(LoreBookManager.createBookStack(entry));
        LoreBookManager.markCollected(player, entry.id());
    }
}
