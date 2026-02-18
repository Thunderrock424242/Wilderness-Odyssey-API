package com.thunder.wildernessodysseyapi.feedback;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

public final class FeedbackCommand {
    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private FeedbackCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("feedback")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.translatable("command.wildernessodysseyapi.feedback.players_only"));
                                return 0;
                            }
                            FeedbackConfig.FeedbackConfigValues config = FeedbackConfig.values();
                            if (!config.enabled()) {
                                player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.feedback.disabled"));
                                return 0;
                            }
                            if (config.webhookUrl() == null || config.webhookUrl().isBlank()) {
                                player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.feedback.not_configured"));
                                return 0;
                            }
                            String message = StringArgumentType.getString(context, "message").trim();
                            if (message.isBlank()) {
                                player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.feedback.empty"));
                                return 0;
                            }
                            int maxLength = config.maxMessageLength();
                            if (message.length() > maxLength) {
                                player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.feedback.too_long", maxLength));
                                return 0;
                            }
                            submitFeedback(player, message, config);
                            player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.feedback.sent"));
                            return 1;
                        })));
    }

    private static void submitFeedback(ServerPlayer player, String message, FeedbackConfig.FeedbackConfigValues config) {
        AsyncTaskManager.submitIoTask("feedback-webhook", () -> {
            try {
                JsonObject payload = new JsonObject();
                String content = "Feedback from **" + player.getGameProfile().getName()
                        + "** (`" + player.getUUID() + "`):\n" + message;
                payload.addProperty("content", content);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(config.webhookUrl()))
                        .timeout(Duration.ofSeconds(config.requestTimeoutSeconds()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    LOGGER.warn("[Feedback] Webhook returned status {}", response.statusCode());
                }
            } catch (Exception ex) {
                LOGGER.warn("[Feedback] Failed to send feedback: {}", ex.getMessage());
            }
            return Optional.empty();
        });
    }
}
