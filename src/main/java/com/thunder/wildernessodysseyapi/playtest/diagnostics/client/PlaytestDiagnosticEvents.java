package com.thunder.wildernessodysseyapi.playtest.diagnostics.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.playtest.diagnostics.DiagnosticCollector;
import com.thunder.wildernessodysseyapi.playtest.diagnostics.DiagnosticHttp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Client-only lifecycle; game state is snapshotted, never read on a network worker. */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class PlaytestDiagnosticEvents {
    private static final ClientSparkCapture SPARK = new ClientSparkCapture();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2), runnable -> { var thread = new Thread(runnable, "WO-Playtest-IO"); thread.setDaemon(true); return thread; });
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "WO-Playtest-Lease"); thread.setDaemon(true); return thread;
    });
    private static volatile DiagnosticCollector collector;
    private static volatile DiagnosticCollector.Snapshot snapshot = DiagnosticCollector.Snapshot.menu();
    private static volatile String setupStatus = "Loading playtest settings";
    private static volatile boolean initialized;
    private static boolean shuttingDown;
    private static long lastSnapshot;
    private static boolean notified;
    private static CaptureAppender appender;
    private static final Component RECORDING_LABEL = Component.translatable("screen.wildernessodysseyapi.playtest.indicator");

    private PlaytestDiagnosticEvents() { }

    private static void initialize() {
        if (initialized) return;
        initialized = true;
        submit(() -> {
            try {
                collector = new DiagnosticCollector(FMLPaths.CONFIGDIR.get().resolve(ModConstants.MOD_ID).resolve("playtest-diagnostics"), new DiagnosticHttp(), SPARK);
                setupStatus = "Ready for a playtest invitation";
            } catch (java.io.IOException | RuntimeException error) { setupStatus = "Playtest storage unavailable"; }
        });
        appender = new CaptureAppender();
        appender.start();
        ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addAppender(appender);
        WATCHDOG.scheduleWithFixedDelay(() -> {
            var current = collector;
            if (current != null) current.expire();
        }, 1, 1, TimeUnit.SECONDS);
        WATCHDOG.scheduleWithFixedDelay(() -> {
            var current = collector;
            if (current != null && BUSY.compareAndSet(false, true)) {
                if (!submit(() -> { try { current.cycle(snapshot); } finally { BUSY.set(false); } })) BUSY.set(false);
            }
        }, 2, 10, TimeUnit.SECONDS);
    }

    static String status() { var current = collector; return current == null ? setupStatus : current.status(); }
    static String origin() { var current = collector; return current == null ? "" : current.origin(); }

    static void enroll(String origin, String invitation) {
        var current = collector;
        if (current == null || !BUSY.compareAndSet(false, true)) { setupStatus = "Please wait for the current request"; return; }
        setupStatus = "Checking invitation";
        if (!submit(() -> {
            try { current.enroll(origin, invitation); setupStatus = "Invitation accepted"; }
            catch (java.io.IOException | InterruptedException | RuntimeException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                setupStatus = "Invitation could not be accepted. Check the code and service address.";
            } finally { BUSY.set(false); }
        })) BUSY.set(false);
    }

    static String enrollmentMessage() { return setupStatus; }

    static void stop() {
        var current = collector;
        if (current == null) return;
        current.localStop();
        // Cleanup is retried by the normal worker cycle if the bounded queue is occupied.
        submit(() -> { try { current.eraseStopped(); } catch (java.io.IOException error) { setupStatus = "Stopped; cleanup will retry"; } });
    }

    @SubscribeEvent public static void onScreen(ScreenEvent.Init.Post event) {
        initialize();
        if (event.getScreen() instanceof TitleScreen || event.getScreen() instanceof PauseScreen) {
            var screen = event.getScreen();
            event.addListener(Button.builder(Component.translatable("screen.wildernessodysseyapi.playtest.open"),
                    button -> Minecraft.getInstance().setScreen(new PlaytestEnrollmentScreen(screen)))
                    .bounds(5, 5, 100, 20).build());
        }
    }

    @SubscribeEvent public static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("woplaytest").executes(context -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> minecraft.setScreen(new PlaytestEnrollmentScreen(minecraft.screen)));
            return 1;
        }));
    }

    @SubscribeEvent public static void onTick(ClientTickEvent.Post event) {
        if (shuttingDown) return;
        initialize();
        long now = System.nanoTime();
        if (now - lastSnapshot < TimeUnit.SECONDS.toNanos(1)) return;
        lastSnapshot = now;
        Minecraft minecraft = Minecraft.getInstance();
        if (collector == null || !collector.enrolled()) { snapshot = DiagnosticCollector.Snapshot.menu(); return; }
        if (minecraft.player != null && minecraft.level != null) {
            SPARK.discover();
            snapshot = new DiagnosticCollector.Snapshot(true, minecraft.hasSingleplayerServer() ? "singleplayer" : "multiplayer",
                    minecraft.player.getGameProfile().getName(), minecraft.player.getUUID().toString(), ModConstants.currentVersion());
            boolean recording = collector != null && collector.recording();
            if (recording && !notified) minecraft.player.displayClientMessage(Component.translatable("screen.wildernessodysseyapi.playtest.recording"), false);
            notified = recording;
        } else { snapshot = DiagnosticCollector.Snapshot.menu(); notified = false; }
    }

    @SubscribeEvent public static void onGui(RenderGuiEvent.Post event) {
        var current = collector;
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null || !current.recording() || minecraft.player == null || minecraft.options.hideGui) return;
        int right = minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(RECORDING_LABEL) - 6;
        event.getGuiGraphics().drawString(minecraft.font, RECORDING_LABEL, right, 6, 0xFFFFCC55);
    }

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        snapshot = DiagnosticCollector.Snapshot.menu();
        var current = collector;
        if (current != null) submit(current::disconnected);
    }

    @SubscribeEvent public static void onShutdown(GameShuttingDownEvent event) {
        shuttingDown = true;
        snapshot = DiagnosticCollector.Snapshot.menu();
        WATCHDOG.shutdownNow();
        var current = collector;
        if (current != null) submit(current::disconnected);
        IO.shutdown();
        try { if (!IO.awaitTermination(2, TimeUnit.SECONDS)) IO.shutdownNow(); }
        catch (InterruptedException error) { IO.shutdownNow(); Thread.currentThread().interrupt(); }
        SPARK.cancel();
        if (current != null) current.closeTransport();
        if (appender != null) {
            ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).removeAppender(appender);
            appender.stop();
        }
    }

    private static boolean submit(Runnable work) {
        try { IO.execute(work); return true; }
        catch (java.util.concurrent.RejectedExecutionException full) { return false; }
    }

    private static final class CaptureAppender extends AbstractAppender {
        CaptureAppender() { super("WOPlaytestDiagnostics", null, null, true, Property.EMPTY_ARRAY); }
        @Override public void append(LogEvent event) {
            var current = collector;
            if (current == null || !current.recording()) return;
            current.append(event.getLevel().name(), event.getLoggerName(), event.getMessage().getFormattedMessage());
            Throwable error = event.getThrown();
            if (error != null) {
                current.append("ERROR", event.getLoggerName(), error.toString());
                int count = 0;
                for (StackTraceElement frame : error.getStackTrace()) {
                    if (++count > 32) break;
                    current.append("ERROR", event.getLoggerName(), "    at " + frame);
                }
            }
        }
    }
}
