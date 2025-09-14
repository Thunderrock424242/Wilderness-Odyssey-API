package com.thunder.wildernessodysseyapi.ModPackPatches.FAQ;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import java.util.Comparator;
import java.util.List;

/**
 * Provides the {@code /faq} command with multiple sub-commands.
 */
public class FaqCommand {
    /**
     * Registers the command and its sub commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("faq")
                .then(Commands.literal("view")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FaqManager.getIds(), builder))
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    FaqEntry entry = FaqManager.get(id);
                                    if (entry != null) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("[" + entry.category() + "] " + entry.question() + ": " + entry.answer()), false);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("FAQ not found for id: " + id));
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("search")
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String query = StringArgumentType.getString(ctx, "query");
                                    List<FaqEntry> results = FaqManager.search(query);
                                    results.sort(Comparator.comparing(FaqEntry::id));
                                    if (results.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("No FAQs found."));
                                    } else {
                                        for (FaqEntry entry : results) {
                                            ctx.getSource().sendSuccess(() -> Component.literal("- [" + entry.category() + "] " + entry.id() + ": " + entry.question()), false);
                                        }
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            for (String cat : FaqManager.getCategories()) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Category: " + cat), false);
                                for (FaqEntry entry : FaqManager.getByCategory(cat)) {
                                    ctx.getSource().sendSuccess(() -> Component.literal("- " + entry.id() + ": " + entry.question()), false);
                                }
                            }
                            return 1;
                        }))
        );
    }
}
