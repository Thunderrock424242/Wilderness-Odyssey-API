package com.thunder.wildernessodysseyapi.feedback;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thunder.wildernessodysseyapi.playtest.PlaytestReplies;
import com.thunder.wildernessodysseyapi.playtest.PlaytestRequestLimiter;
import com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Registers bounded asynchronous feedback with server-thread completion messages. */
public final class FeedbackCommand {
    private static final FeedbackSubmissionService SERVICE = new FeedbackSubmissionService();

    private FeedbackCommand() {
    }

    /** Validates input on the logical server and reports success only after HTTP acknowledgement. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        PlaytestRequestLimiter limiter = new PlaytestRequestLimiter();
        dispatcher.register(Commands.literal("feedback")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(message("players_only"));
                                return 0;
                            }
                            var config = FeedbackConfig.values();
                            if (!config.enabled()) {
                                player.sendSystemMessage(message("disabled"));
                                return 0;
                            }
                            if (!PlaytestWebhookClient.isConfigured(config.webhookUrl())) {
                                player.sendSystemMessage(message("not_configured"));
                                return 0;
                            }
                            String text = StringArgumentType.getString(context, "message").trim();
                            if (text.isBlank()) {
                                player.sendSystemMessage(message("empty"));
                                return 0;
                            }
                            if (text.length() > config.maxMessageLength()) {
                                player.sendSystemMessage(Component.translatable(
                                        "command.wildernessodysseyapi.feedback.too_long", config.maxMessageLength()));
                                return 0;
                            }
                            var playerId = player.getUUID();
                            if (!limiter.tryAcquire(playerId, config.cooldownSeconds())) {
                                player.sendSystemMessage(Component.translatable(
                                        "command.wildernessodysseyapi.feedback.cooldown", config.cooldownSeconds()));
                                return 0;
                            }
                            var server = source.getServer();
                            var connection = player.connection;
                            player.sendSystemMessage(message("sending"));
                            var submission = SERVICE.submit(player.getGameProfile().getName(),
                                    playerId.toString(), text, config);
                            submission.whenComplete((result, error) -> {
                                if (result == PlaytestWebhookClient.Result.RATE_LIMITED) {
                                    limiter.pause(60);
                                }
                                limiter.complete(playerId);
                            });
                            PlaytestReplies.deliver(submission, server::execute, result -> {
                                var connectedPlayer = server.getPlayerList().getPlayer(playerId);
                                if (!server.isStopped() && connectedPlayer != null && connectedPlayer.connection == connection) {
                                    connectedPlayer.sendSystemMessage(message(
                                            result == PlaytestWebhookClient.Result.DELIVERED ? "sent" : "failed"));
                                }
                            });
                            return 1;
                        })));
    }

    private static Component message(String key) {
        return Component.translatable("command.wildernessodysseyapi.feedback." + key);
    }
}