package com.thunder.wildernessodysseyapi.playtest;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.feedback.FeedbackConfig;
import com.thunder.wildernessodysseyapi.playtest.verification.MinecraftVerificationRelayConfig;
import com.thunder.wildernessodysseyapi.telemetry.EventTelemetryConfig;
import com.thunder.wildernessodysseyapi.telemetry.PlayerTelemetryConfig;
import com.thunder.wildernessodysseyapi.telemetry.TelemetryConfig;
import com.thunder.wildernessodysseyapi.telemetry.TelemetryQueue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Operator-only playtest diagnostics; endpoint values and credentials are never displayed. */
public final class PlaytestStatusCommand {
    private PlaytestStatusCommand() {
    }

    /** Registers status under the existing /wo command root without granting player access. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wo")
                .then(Commands.literal("playtest").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        var verification = MinecraftVerificationRelayConfig.values();
        var feedback = FeedbackConfig.values();
        var master = TelemetryConfig.values();
        var players = PlayerTelemetryConfig.values();
        var events = EventTelemetryConfig.values();
        var stats = TelemetryQueue.get(source.getServer()).stats();
        source.sendSuccess(() -> Component.literal("Playtest verification configured: "
                + yes(verification.enableServerVerificationRelay()
                && PlaytestWebhookClient.isConfigured(verification.discordVerificationWebhookUrl()))), false);
        source.sendSuccess(() -> Component.literal("Telemetry master enabled: " + yes(master.enabled())
                + "; player sessions: " + yes(players.enabled()) + "; gameplay events: " + yes(events.enabled())), false);
        source.sendSuccess(() -> Component.literal("Telemetry endpoints configured: player "
                + yes(PlaytestWebhookClient.isConfigured(players.sheetWebhookUrl()))
                + ", events " + yes(PlaytestWebhookClient.isConfigured(events.webhookUrl()))), false);
        source.sendSuccess(() -> Component.literal("Feedback enabled: " + yes(feedback.enabled())
                + "; endpoint configured: " + yes(PlaytestWebhookClient.isConfigured(feedback.webhookUrl()))), false);
        source.sendSuccess(() -> Component.literal("Telemetry pending: " + stats.pending()
                + "; retrying: " + stats.retrying() + "; in flight: " + stats.inFlight()
                + "; failed attempts: " + stats.failed() + "; dropped: " + stats.dropped()
                + "; last success: " + stats.lastSuccessOptional().map(Object::toString).orElse("never this run")), false);
        return 1;
    }

    private static String yes(boolean value) {
        return value ? "yes" : "no";
    }
}