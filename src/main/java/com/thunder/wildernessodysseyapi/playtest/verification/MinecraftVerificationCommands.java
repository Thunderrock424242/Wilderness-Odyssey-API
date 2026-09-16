package com.thunder.wildernessodysseyapi.playtest.verification;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thunder.wildernessodysseyapi.playtest.PlaytestReplies;
import com.thunder.wildernessodysseyapi.playtest.PlaytestRequestLimiter;
import com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Registers account-link delivery on the logical server, including dedicated servers. */
public final class MinecraftVerificationCommands {
    private static final MinecraftVerificationRelayClient RELAY_CLIENT = new MinecraftVerificationRelayClient();

    private MinecraftVerificationCommands() {
    }

    /** Each dispatcher owns its limits, preventing state leaking between server lifetimes. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        PlaytestRequestLimiter limiter = new PlaytestRequestLimiter();
        dispatcher.register(Commands.literal("wo")
                .then(Commands.literal("link")
                        .executes(context -> {
                            context.getSource().sendFailure(Component.translatable("command.wildernessodysseyapi.link.usage"));
                            return 0;
                        })
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(context -> link(context.getSource(),
                                        StringArgumentType.getString(context, "code"), limiter)))));
    }

    private static int link(CommandSourceStack source, String code, PlaytestRequestLimiter limiter) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.wildernessodysseyapi.link.players_only"));
            return 0;
        }
        if (!MinecraftVerificationRelayClient.validCode(code)) {
            player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.link.invalid_code"));
            return 0;
        }
        var config = MinecraftVerificationRelayConfig.values();
        if (!config.enableServerVerificationRelay()
                || !PlaytestWebhookClient.isConfigured(config.discordVerificationWebhookUrl())) {
            player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.link.not_configured"));
            return 0;
        }
        var playerId = player.getUUID();
        if (!limiter.tryAcquire(playerId, config.cooldownSeconds())) {
            player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.link.cooldown", config.cooldownSeconds()));
            return 0;
        }
        var server = source.getServer();
        var connection = player.connection;
        player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.link.sending"));
        var submission = RELAY_CLIENT.sendVerification(config.discordVerificationWebhookUrl(),
                config.requestTimeoutSeconds(), code, playerId.toString(), player.getGameProfile().getName());
        submission.whenComplete((result, error) -> {
            if (result == PlaytestWebhookClient.Result.RATE_LIMITED) {
                limiter.pause(60);
            }
            limiter.complete(playerId);
        });
        PlaytestReplies.deliver(submission, server::execute, result -> {
            // A reconnect must not receive a reply belonging to the previous player session.
            var connectedPlayer = server.getPlayerList().getPlayer(playerId);
            if (server.isStopped() || connectedPlayer == null || connectedPlayer.connection != connection) {
                return;
            }
            String key = switch (result) {
                case DELIVERED -> "sent";
                case NOT_CONFIGURED -> "not_configured";
                case REJECTED -> "rejected";
                case RATE_LIMITED -> "rate_limited";
                default -> "failed";
            };
            connectedPlayer.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.link." + key));
        });
        return 1;
    }
}