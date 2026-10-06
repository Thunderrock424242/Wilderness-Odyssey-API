package com.thunder.wildernessodysseyapi.loading.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.loading.LoadingModAttribution;
import com.thunder.wildernessodysseyapi.loading.LoadingProfileMonitor;
import com.thunder.wildernessodysseyapi.loading.LoadingProfileSession;
import com.thunder.wildernessodysseyapi.loading.LoadingProfileSettings;
import com.thunder.wildernessodysseyapi.loading.LoadingScreenPhase;
import com.thunder.wildernessodysseyapi.mixin.LevelLoadingScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Client-only bridge from loading screens to an independent JVM diagnostic
 * worker. The render/tick thread publishes stage/progress metadata only. All
 * thread sampling, aggregation and local report I/O happen on that worker.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class LoadingStallDetector {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("wilderness.loadingstall.enabled", "true"));
    private static final long OBSERVATION_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static LoadingProfileMonitor monitor;
    private static boolean initializationFailed;
    private static long lastObservationNanos;
    private static LoadingScreenPhase observedPhase;
    private static LoadingProfileMonitor.Status displayedStatus;
    private static LoadingScreenPhase displayedPhase;
    private static List<String> displayLines = List.of();

    private LoadingStallDetector() {
    }

    /** Retained as a compatibility bridge for callers of the former overlay detector. */
    public static void recordProgress() {
        observeCurrentScreen();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        observeCurrentScreen();
    }

    /** Minecraft's blocking spawn-loading loop still renders without normal client ticks. */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        observeCurrentScreen();
    }

    /** Arms the worker before screen initialization or a following synchronous load can block. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!ENABLED) {
            return;
        }
        Screen next = event.getNewScreen();
        LoadingScreenPhase phase = classify(next);
        if (phase != null) {
            observe(next, phase, System.nanoTime());
        } else if (next != null && !(Minecraft.getInstance().getOverlay() instanceof LoadingOverlay)) {
            stop("Loading screen closed or cancelled");
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop("Client connection closed");
    }

    @SubscribeEvent
    public static void onShutdown(GameShuttingDownEvent event) {
        if (monitor != null) {
            monitor.close();
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!ENABLED || monitor == null || observedPhase == null || classify(event.getScreen()) == null) {
            return;
        }
        var status = monitor.status();
        if (!status.active() || status.snapshot() == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (status != displayedStatus || observedPhase != displayedPhase) {
            displayedStatus = status;
            displayedPhase = observedPhase;
            displayLines = buildDisplay(status, observedPhase);
        }
        int width = Math.max(0, event.getScreen().width - 20);
        int height = displayLines.size() * 11 + 8;
        int top = event.getScreen().height - height - 8;
        var graphics = event.getGuiGraphics();
        graphics.fill(10, top, 10 + width, top + height, 0xB0000000);
        for (int index = 0; index < displayLines.size(); index++) {
            graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(displayLines.get(index), Math.max(0, width - 8)),
                    14, top + 4 + index * 11, 0xFFE0E0E0);
        }
    }

    private static void observeCurrentScreen() {
        if (!ENABLED) {
            return;
        }
        long now = System.nanoTime();
        if (lastObservationNanos != 0 && now - lastObservationNanos < OBSERVATION_INTERVAL_NANOS) {
            return;
        }
        lastObservationNanos = now;
        Minecraft minecraft = Minecraft.getInstance();
        LoadingScreenPhase phase = minecraft.getOverlay() instanceof LoadingOverlay
                ? LoadingScreenPhase.RESOURCES : classify(minecraft.screen);
        if (phase == null) {
            stop(minecraft.level != null ? "World opened" : "Loading screen closed or cancelled");
        } else {
            observe(minecraft.screen, phase, now);
        }
    }

    private static void observe(Screen screen, LoadingScreenPhase phase, long now) {
        if (initializationFailed) {
            return;
        }
        if (monitor == null) {
            try {
                monitor = createMonitor();
            } catch (RuntimeException | LinkageError exception) {
                initializationFailed = true;
                ModConstants.LOGGER.warn("[Loading profiler] Could not initialize diagnostics; loading continues normally.", exception);
                return;
            }
        }
        int progress = -1;
        if (phase == LoadingScreenPhase.SPAWN && screen instanceof LevelLoadingScreenAccessor accessor) {
            progress = accessor.wildernessOdysseyApi$getProgressListener().getProgress();
        }
        observedPhase = phase;
        monitor.observe(phase.label(), progress, now);
    }

    private static LoadingScreenPhase classify(Screen screen) {
        if (screen instanceof LevelLoadingScreen) {
            return LoadingScreenPhase.SPAWN;
        }
        if (screen instanceof ReceivingLevelScreen) {
            return LoadingScreenPhase.TERRAIN;
        }
        if (screen instanceof ProgressScreen) {
            return LoadingScreenPhase.WORLD_OPERATION;
        }
        if (screen instanceof GenericMessageScreen && screen.getTitle().getContents() instanceof TranslatableContents message) {
            return LoadingScreenPhase.fromMessageKey(message.getKey());
        }
        return null;
    }

    private static LoadingProfileMonitor createMonitor() {
        Map<String, List<String>> modules = new HashMap<>();
        List<String> mods = new ArrayList<>();
        for (var mod : ModList.get().getMods()) {
            String module = mod.getOwningFile().moduleName();
            if (module != null && !module.isBlank()) {
                modules.computeIfAbsent(module, ignored -> new ArrayList<>()).add(mod.getModId());
            }
            mods.add(mod.getModId() + "@" + mod.getVersion());
        }
        Map<String, String> owners = new HashMap<>();
        modules.forEach((module, ids) -> {
            ids.sort(String::compareTo);
            owners.put(module, String.join(", ", ids) + (ids.size() > 1 ? " (shared module)" : ""));
        });
        mods.sort(String::compareTo);
        return new LoadingProfileMonitor(FMLPaths.GAMEDIR.get().resolve("logs").resolve("loading-stalls"),
                new LoadingModAttribution(owners), mods,
                LoadingProfileSettings.reportDelayMillis(System.getProperty("wilderness.loadingstall.minutes", "")));
    }

    private static void stop(String outcome) {
        if (monitor != null && observedPhase != null) {
            monitor.stop(outcome);
        }
        observedPhase = null;
        displayedStatus = null;
        displayedPhase = null;
        displayLines = List.of();
    }

    private static List<String> buildDisplay(LoadingProfileMonitor.Status status, LoadingScreenPhase phase) {
        var snapshot = status.snapshot();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("loading.wildernessodysseyapi.elapsed", LoadingProfileSession.duration(snapshot.elapsedNanos()),
                Component.translatable(phase.translationKey())));
        String since = LoadingProfileSession.duration(snapshot.noProgressNanos());
        lines.add(snapshot.progress() >= 0
                ? Component.translatable("loading.wildernessodysseyapi.progress", snapshot.progress(), since)
                : Component.translatable("loading.wildernessodysseyapi.stage_wait", since));
        var activeSite = snapshot.sites().stream().filter(site -> site.runnableSamples() > 0).findFirst();
        lines.add(activeSite.map(site -> Component.translatable("loading.wildernessodysseyapi.suspect", site.owner()))
                .orElseGet(() -> Component.translatable("loading.wildernessodysseyapi.sampling")));
        if (status.saveFailed()) {
            lines.add(Component.translatable("loading.wildernessodysseyapi.save_failed"));
        } else if (status.report() != null) {
            lines.add(Component.translatable("loading.wildernessodysseyapi.saved"));
        }
        return lines.stream().map(Component::getString).toList();
    }
}
