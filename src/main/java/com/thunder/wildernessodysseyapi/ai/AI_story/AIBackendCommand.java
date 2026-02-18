package com.thunder.wildernessodysseyapi.ai.AI_story;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class AIBackendCommand {

    private AIBackendCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atlasbackend")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(context -> sendStatus(context.getSource())))
                .then(Commands.literal("start")
                        .executes(context -> triggerStart(context.getSource())))
                .then(Commands.literal("probe")
                        .executes(context -> runProbe(context.getSource()))));
    }

    private static int sendStatus(CommandSourceStack source) {
        AIClient.BackendStatus status = AIChatListener.getClient().getBackendStatus();
        source.sendSuccess(() -> Component.literal("[AtlasBackend] enabled=" + status.enabled()
                + ", clientInitialized=" + status.clientInitialized()
                + ", autoStart=" + status.autoStart()
                + ", serverRunning=" + status.serverRunning()
                + ", endpoint=" + status.endpoint()
                + ", model=" + status.model()
                + ", reachable=" + status.reachable()), false);
        return 1;
    }

    private static int triggerStart(CommandSourceStack source) {
        boolean started = AIChatListener.getClient().triggerBackendStart();
        if (started) {
            source.sendSuccess(() -> Component.literal("[AtlasBackend] Start command issued."), true);
            return 1;
        }
        source.sendFailure(Component.literal("[AtlasBackend] Start skipped. Configure local_model.auto_start and start command/bundled resource."));
        return 0;
    }

    private static int runProbe(CommandSourceStack source) {
        AIClient.BackendStatus status = AIChatListener.getClient().getBackendStatus();
        if (status.reachable()) {
            source.sendSuccess(() -> Component.literal("[AtlasBackend] Probe succeeded: " + status.endpoint()), false);
            return 1;
        }
        ModConstants.LOGGER.warn("[AtlasBackend] Probe failed for endpoint {}", status.endpoint());
        source.sendFailure(Component.literal("[AtlasBackend] Probe failed. Verify your local sidecar service is running."));
        return 0;
    }
}
