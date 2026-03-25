package com.thunder.wildernessodysseyapi.ModPackPatches.faq;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
                                        ctx.getSource().sendSuccess(() -> Component.translatable(
                                                "command.wildernessodysseyapi.faq.view",
                                                entry.category(),
                                                entry.question(),
                                                entry.answer()), false);
                                    } else {
                                        ctx.getSource().sendFailure(Component.translatable(
                                                "command.wildernessodysseyapi.faq.not_found",
                                                id));
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("search")
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String query = StringArgumentType.getString(ctx, "query");
                                    List<FaqEntry> results = FaqManager.search(query);
                                    if (results.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.translatable("command.wildernessodysseyapi.faq.no_results"));
                                    } else {
                                        for (FaqEntry entry : results) {
                                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                                    "command.wildernessodysseyapi.faq.search_result",
                                                    entry.category(),
                                                    entry.id(),
                                                    entry.question()), false);
                                        }
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("categories")
                        .executes(ctx -> {
                            for (String cat : FaqManager.getCategories()) {
                                ctx.getSource().sendSuccess(() -> Component.translatable(
                                        "command.wildernessodysseyapi.faq.list_category",
                                        cat), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("list")
                        .executes(ctx -> listCategory(ctx.getSource(), null))
                        .then(Commands.argument("category", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FaqManager.getCategories(), builder))
                                .executes(ctx -> listCategory(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "category")))))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> reloadFaq(ctx.getSource())))
        );
    }

    private static int listCategory(CommandSourceStack source, String category) {
        if (category != null) {
            List<FaqEntry> entries = FaqManager.getByCategory(category);
            if (entries.isEmpty()) {
                source.sendFailure(Component.translatable("command.wildernessodysseyapi.faq.no_results"));
                return 1;
            }
            source.sendSuccess(() -> Component.translatable("command.wildernessodysseyapi.faq.list_category", category), false);
            entries.stream()
                    .sorted(Comparator.comparing(FaqEntry::id))
                    .forEach(entry -> source.sendSuccess(() -> Component.translatable(
                            "command.wildernessodysseyapi.faq.list_entry",
                            entry.id(),
                            entry.question()), false));
            return 1;
        }

        for (String cat : FaqManager.getCategories()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.wildernessodysseyapi.faq.list_category",
                    cat), false);
            for (FaqEntry entry : FaqManager.getByCategory(cat)) {
                source.sendSuccess(() -> Component.translatable(
                        "command.wildernessodysseyapi.faq.list_entry",
                        entry.id(),
                        entry.question()), false);
            }
        }
        return 1;
    }

    private static int reloadFaq(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Reloading datapacks (including FAQ entries)..."), true);
        CompletableFuture<?> reloadFuture = source.getServer().reloadResources(source.getServer().getPackRepository().getSelectedIds());
        reloadFuture.whenComplete((ignored, throwable) -> source.getServer().execute(() -> {
            if (throwable == null) {
                source.sendSuccess(() -> Component.literal("FAQ reload complete."), true);
            } else {
                source.sendFailure(Component.literal("FAQ reload failed. Check server logs."));
            }
        }));
        return 1;
    }
}
