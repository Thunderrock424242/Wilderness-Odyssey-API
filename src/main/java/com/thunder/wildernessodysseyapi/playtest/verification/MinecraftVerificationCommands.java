package com.thunder.wildernessodysseyapi.playtest.verification;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class MinecraftVerificationCommands {
    private static final MinecraftVerificationRelayClient RELAY_CLIENT = new MinecraftVerificationRelayClient();

    private MinecraftVerificationCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wo")
                .then(Commands.literal("link")
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal("Enter the code from Discord: /wo link <code>"));
                            return 0;
                        })
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(context -> link(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "code")
                                )))));
    }

    private static int link(CommandSourceStack source, String code) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can link a Minecraft account."));
            return 0;
        }

        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isBlank()) {
            player.sendSystemMessage(Component.literal("Enter the code from Discord: /wo link <code>"));
            return 0;
        }

        MinecraftVerificationRelayConfig.Values config = MinecraftVerificationRelayConfig.values();
        if (!config.enableServerVerificationRelay() || config.discordVerificationWebhookUrl() == null
                || config.discordVerificationWebhookUrl().isBlank()) {
            player.sendSystemMessage(Component.literal("Verification relay is not configured. Please tell staff to enable it on the server."));
            return 0;
        }

        String minecraftUuid = player.getUUID().toString();
        String minecraftName = player.getGameProfile().getName();
        MinecraftServer server = player.serverLevel().getServer();

        player.sendSystemMessage(Component.literal("Sending your verification request to Discord..."));
        RELAY_CLIENT.sendVerification(
                        config.discordVerificationWebhookUrl(),
                        config.requestTimeoutSeconds(),
                        normalizedCode,
                        minecraftUuid,
                        minecraftName
                )
                .whenComplete((result, throwable) -> server.execute(() -> {
                    if (throwable != null) {
                        player.sendSystemMessage(Component.literal("Verification relay failed. Please try again or tell staff."));
                        return;
                    }
                    if (result.sent()) {
                        player.sendSystemMessage(Component.literal("Verification request sent. Check Discord for link status."));
                    } else {
                        player.sendSystemMessage(Component.literal(result.message()));
                    }
                }));
        return 1;
    }
}
