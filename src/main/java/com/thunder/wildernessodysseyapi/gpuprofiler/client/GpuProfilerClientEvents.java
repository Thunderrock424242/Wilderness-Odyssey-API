package com.thunder.wildernessodysseyapi.gpuprofiler.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@EventBusSubscriber(value = Dist.CLIENT, modid = ModConstants.MOD_ID)
public final class GpuProfilerClientEvents {

    private static final int DEFAULT_LIMIT = 10;

    private GpuProfilerClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wovram")
                .executes(context -> showStatus(context.getSource()))
                .then(Commands.literal("start").executes(context -> start(context.getSource())))
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                .then(Commands.literal("status").executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("top")
                        .executes(context -> showTop(context.getSource(), DEFAULT_LIMIT))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 25))
                                .executes(context -> showTop(context.getSource(), IntegerArgumentType.getInteger(context, "limit")))))
                .then(Commands.literal("snapshot")
                        .executes(context -> snapshot(context.getSource(), null))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> snapshot(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("diff")
                        .executes(context -> showDiff(context.getSource(), DEFAULT_LIMIT))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 25))
                                .executes(context -> showDiff(context.getSource(), IntegerArgumentType.getInteger(context, "limit")))))
                .then(Commands.literal("export").executes(context -> export(context.getSource()))));
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        GpuProfiler.onFrame();
    }

    private static int start(CommandSourceStack source) {
        GpuProfiler.StartResult result = GpuProfiler.start();
        send(source, result.message());
        if (result.started()) {
            send(source, "Reproduce the VRAM increase, then run /wovram top, /wovram diff, or /wovram stop.");
        }
        return result.started() ? 1 : 0;
    }

    private static int stop(CommandSourceStack source) {
        GpuProfiler.StopResult result = GpuProfiler.stop();
        send(source, result.message());
        sendStatus(source, result.status());
        return result.stopped() ? 1 : 0;
    }

    private static int showStatus(CommandSourceStack source) {
        GpuProfiler.Status status = GpuProfiler.status();
        if (!status.hasSession()) {
            send(source, "WO VRAM profiler is idle. Run /wovram start to begin a session.");
            return 0;
        }
        sendStatus(source, status);
        return 1;
    }

    private static void sendStatus(CommandSourceStack source, GpuProfiler.Status status) {
        String state = status.active() ? "running" : "stopped";
        send(source, "WO VRAM profiler: " + state + " for " + formatDuration(status.elapsedNanos()));
        send(source, "Tracked live allocations: " + GpuProfiler.formatBytes(status.trackedBytes())
                + " across " + status.resourceCount() + " objects (" + status.allocationEvents() + " allocation events, "
                + status.deletionEvents() + " deletes).");
        if (status.hardwareDeltaBytes() == null) {
            send(source, "Driver VRAM delta: unavailable (provider: " + status.hardwareProvider() + ").");
        } else {
            send(source, "Driver VRAM delta: " + signedBytes(status.hardwareDeltaBytes())
                    + " (provider: " + status.hardwareProvider() + ").");
        }
        send(source, "GPU: " + status.gpuVendor() + " / " + status.gpuRenderer());
        if (status.hookErrors() > 0) {
            send(source, "Profiler hook errors suppressed: " + status.hookErrors() + ".");
        }
    }

    private static int showTop(CommandSourceStack source, int limit) {
        if (!GpuProfiler.status().hasSession()) {
            send(source, "Start a session with /wovram start first.");
            return 0;
        }
        List<GpuProfiler.SiteSummary> rows = GpuProfiler.top(limit);
        if (rows.isEmpty()) {
            send(source, "No live GPU allocations have been observed in this session yet.");
            return 1;
        }
        send(source, "Top tracked VRAM allocation sites:");
        for (int i = 0; i < rows.size(); i++) {
            GpuProfiler.SiteSummary row = rows.get(i);
            send(source, (i + 1) + ". " + row.modId() + " — " + GpuProfiler.formatBytes(row.bytes())
                    + " / " + row.objects() + " object(s) — " + row.label() + " @ " + row.location());
        }
        return 1;
    }

    private static int snapshot(CommandSourceStack source, String name) {
        GpuProfiler.SnapshotResult result = GpuProfiler.snapshot(name);
        send(source, result.message());
        return result.captured() ? 1 : 0;
    }

    private static int showDiff(CommandSourceStack source, int limit) {
        if (!GpuProfiler.status().hasSession()) {
            send(source, "Start a session with /wovram start first.");
            return 0;
        }
        GpuProfiler.DiffResult diff = GpuProfiler.diff(limit);
        send(source, "VRAM changes since snapshot '" + diff.snapshotName() + "':");
        if (diff.rows().isEmpty()) {
            send(source, "No tracked allocation changes.");
            return 1;
        }
        for (GpuProfiler.SiteSummary row : diff.rows()) {
            send(source, signedBytes(row.bytes()) + " / " + signedCount(row.objects()) + " object(s) — "
                    + row.modId() + " — " + row.label() + " @ " + row.location());
        }
        return 1;
    }

    private static int export(CommandSourceStack source) {
        Path reportDirectory = FMLPaths.GAMEDIR.get().resolve("logs").resolve("wildernessodysseyapi").resolve("gpu-profiler");
        try {
            Path report = GpuProfiler.export(reportDirectory);
            send(source, "Exported WO VRAM report to " + report + ".");
            return 1;
        } catch (IllegalStateException | IOException exception) {
            source.sendFailure(Component.literal("Could not export WO VRAM report: " + exception.getMessage()));
            return 0;
        }
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static String signedBytes(long bytes) {
        return (bytes >= 0L ? "+" : "") + GpuProfiler.formatBytes(bytes);
    }

    private static String signedCount(int count) {
        return (count >= 0 ? "+" : "") + count;
    }

    private static String formatDuration(long elapsedNanos) {
        double seconds = elapsedNanos / 1_000_000_000.0;
        return String.format("%.1fs", seconds);
    }
}
