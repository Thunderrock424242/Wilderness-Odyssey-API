package com.thunder.wildernessodysseyapi.ModPackPatches.faq;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides the {@code /faq} command with multiple sub-commands.
 */
public class FaqCommand {
    private static final int PAGE_SIZE = 5;
    private static final long COOLDOWN_MS = 1_200L;
    private static final Map<UUID, Long> QUERY_COOLDOWN = new ConcurrentHashMap<>();

    /**
     * Registers the command and its sub commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("faq")
                .executes(ctx -> showTopicHelp(ctx.getSource()))
                .then(Commands.literal("view")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FaqManager.getIds(), builder))
                                .executes(ctx -> {
                                    if (!checkCooldown(ctx.getSource())) {
                                        return 0;
                                    }
                                    String id = StringArgumentType.getString(ctx, "id");
                                    FaqEntry entry = FaqManager.get(id);
                                    if (entry != null) {
                                        ctx.getSource().sendSuccess(() -> Component.translatable(
                                                "command.wildernessodysseyapi.faq.view",
                                                inferTopic(entry),
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
                                    if (!checkCooldown(ctx.getSource())) {
                                        return 0;
                                    }
                                    return sendSearch(ctx.getSource(), StringArgumentType.getString(ctx, "query"), 1);
                                }))
                        .then(Commands.literal("page")
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    if (!checkCooldown(ctx.getSource())) {
                                                        return 0;
                                                    }
                                                    return sendSearch(
                                                            ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "query"),
                                                            IntegerArgumentType.getInteger(ctx, "page"));
                                                })))))
                .then(Commands.literal("debug")
                        .executes(ctx -> {
                            Map<String, Integer> noResult = FaqManager.getNoResultQueries();
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "command.wildernessodysseyapi.faq.debug.summary",
                                    FaqManager.getEntryCount(),
                                    FaqManager.getTopics().size(),
                                    DateTimeFormatter.ISO_INSTANT.format(Instant.now())), false);

                            noResult.entrySet().stream()
                                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                    .limit(5)
                                    .forEach(entry -> ctx.getSource().sendSuccess(() -> Component.translatable(
                                            "command.wildernessodysseyapi.faq.debug.no_result",
                                            entry.getKey(),
                                            entry.getValue()), false));
                            return 1;
                        }))
                .then(Commands.argument("topic", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FaqManager.getTopics(), builder))
                        .executes(ctx -> {
                            if (!checkCooldown(ctx.getSource())) {
                                return 0;
                            }
                            return listTopic(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "topic"),
                                    1);
                        })
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    if (!checkCooldown(ctx.getSource())) {
                                        return 0;
                                    }
                                    return listTopic(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "topic"),
                                            IntegerArgumentType.getInteger(ctx, "page"));
                                })))
        );
    }


    private static int showTopicHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.wildernessodysseyapi.faq.usage"), false);
        for (String topic : FaqManager.getTopics()) {
            Component line = Component.translatable("command.wildernessodysseyapi.faq.list_topic", topic)
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/faq " + topic + " ")));
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int sendSearch(CommandSourceStack source, String query, int page) {
        List<FaqEntry> results = FaqManager.search(query);
        if (results.isEmpty()) {
            source.sendFailure(Component.translatable("command.wildernessodysseyapi.faq.no_results"));
            return 1;
        }

        int pageCount = Math.max(1, (int) Math.ceil((double) results.size() / PAGE_SIZE));
        int safePage = Math.min(page, pageCount);
        int from = (safePage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, results.size());

        source.sendSuccess(() -> Component.translatable(
                "command.wildernessodysseyapi.faq.page_header",
                safePage,
                pageCount,
                results.size()), false);

        for (FaqEntry entry : results.subList(from, to)) {
            source.sendSuccess(() -> clickableSearchLine(entry), false);
        }

        return 1;
    }

    private static int listTopic(CommandSourceStack source, String topic, int page) {
        List<FaqEntry> entries = FaqManager.getByTopic(topic);
        if (entries.isEmpty()) {
            source.sendFailure(Component.translatable("command.wildernessodysseyapi.faq.no_results"));
            return 1;
        }

        int pageCount = Math.max(1, (int) Math.ceil((double) entries.size() / PAGE_SIZE));
        int safePage = Math.min(page, pageCount);
        int from = (safePage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());

        source.sendSuccess(() -> Component.translatable("command.wildernessodysseyapi.faq.list_topic", topic), false);
        source.sendSuccess(() -> Component.translatable("command.wildernessodysseyapi.faq.page_header", safePage, pageCount, entries.size()), false);

        entries.subList(from, to).stream()
                .sorted(Comparator.comparing(FaqEntry::id, String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> source.sendSuccess(() -> clickableListLine(entry), false));
        return 1;
    }

    private static Component clickableSearchLine(FaqEntry entry) {
        return Component.translatable(
                "command.wildernessodysseyapi.faq.search_result",
                inferTopic(entry),
                entry.id(),
                entry.question())
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/faq view " + entry.id())));
    }

    private static Component clickableListLine(FaqEntry entry) {
        return Component.translatable(
                "command.wildernessodysseyapi.faq.list_entry",
                entry.id(),
                entry.question())
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/faq view " + entry.id())));
    }

    private static String inferTopic(FaqEntry entry) {
        String topic = FaqManager.getTopicForId(entry.id());
        if (!topic.isBlank()) {
            return topic;
        }
        return entry.category() == null ? "general" : entry.category();
    }

    private static boolean checkCooldown(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }

        long now = System.currentTimeMillis();
        Long last = QUERY_COOLDOWN.get(player.getUUID());
        if (last != null && (now - last) < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - last));
            source.sendFailure(Component.translatable("command.wildernessodysseyapi.faq.cooldown", remaining));
            return false;
        }

        QUERY_COOLDOWN.put(player.getUUID(), now);
        return true;
    }
}
