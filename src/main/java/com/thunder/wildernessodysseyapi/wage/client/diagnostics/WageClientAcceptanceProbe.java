package com.thunder.wildernessodysseyapi.wage.client.diagnostics;

import com.thunder.wildernessodysseyapi.cinematic.CinematicManager;
import com.thunder.wildernessodysseyapi.cinematic.CinematicStopReason;
import com.thunder.wildernessodysseyapi.client.notice.AlphaNoticeScreen;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.wage.client.WildernessRenderingFramework;
import com.thunder.wildernessodysseyapi.wage.config.RendererConfig;
import com.thunder.wildernessodysseyapi.wage.performance.AdaptiveGpuController;
import com.thunder.wildernessodysseyapi.wage.performance.FrameTimeWindow;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Explicit local acceptance only: uses a pre-copied disposable world, supported
 * Minecraft load/reload/save APIs and the normal cloud/policy owners. Never runs
 * without the Gradle opt-in and the dedicated game directory. Normal gameplay
 * has no sample collection, config mutation, screenshots or automatic shutdown.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class WageClientAcceptanceProbe {
    private static final boolean ENABLED = Boolean.getBoolean("wildernessodysseyapi.wageAcceptanceTest");
    private static final String WORLD = "wage-acceptance";
    private static final FrameTimeWindow CADENCE = new FrameTimeWindow(4096);
    private static boolean started;
    private static boolean configured;
    private static boolean stopping;
    private static int phase;
    private static long startedNanos;
    private static long phaseStarted;
    private static long previousFrame;
    private static long admissions;
    private static int initialWidth;
    private static int initialHeight;
    private static CompletableFuture<Void> reload;

    private WageClientAcceptanceProbe() { }

    @SubscribeEvent
    public static void afterScreen(ScreenEvent.Render.Post event) {
        if (!ENABLED || started) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null || (!(event.getScreen() instanceof TitleScreen)
                && !(event.getScreen() instanceof AlphaNoticeScreen))) return;
        if (!minecraft.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString()
                .equals("run-wage-acceptance")) {
            throw new IllegalStateException("WAGE acceptance requires its isolated game directory");
        }
        if (!Files.isRegularFile(minecraft.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat"))) {
            throw new IllegalStateException("Copy the disposable smoke world to saves/" + WORLD + " before acceptance");
        }
        started = true;
        startedNanos = System.nanoTime();
        org.lwjgl.glfw.GLFW.glfwHideWindow(minecraft.getWindow().getWindow());
        minecraft.execute(() -> open(minecraft));
    }

    private static void open(Minecraft minecraft) {
        LevelStorageSource.LevelStorageAccess storage = null;
        PackRepository packs = null;
        WorldStem stem = null;
        try {
            storage = minecraft.getLevelSource().validateAndCreateAccess(WORLD);
            packs = ServerPacksSource.createPackRepository(storage);
            stem = minecraft.createWorldOpenFlows().loadWorldStem(storage.getDataTag(), false, packs);
        } catch (Exception failure) {
            if (stem != null) stem.close();
            if (storage != null) storage.safeClose();
            throw new IllegalStateException("WAGE disposable world failed to load", failure);
        }
        // The ordinary integrated-server lifecycle owns storage and stem from this call onward.
        minecraft.doWorldLoad(storage, packs, stem, false);
    }

    @SubscribeEvent
    public static void afterFrame(RenderFrameEvent.Post event) {
        if (!ENABLED || !started) return;
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        if (now - startedNanos > 240_000_000_000L) {
            throw new IllegalStateException("WAGE acceptance exceeded its four-minute deadline");
        }
        if (stopping) return;
        if (minecraft.level == null || minecraft.player == null || minecraft.getOverlay() != null) return;
        if (!configured) {
            configured = true;
            minecraft.options.pauseOnLostFocus = false;
            minecraft.options.hideGui = true;
            minecraft.options.cloudStatus().set(CloudStatus.FANCY);
            minecraft.options.enableVsync().set(false);
            minecraft.getWindow().setWindowed(1024, 576);
            RendererConfig.GPU_ENGINE_DIAGNOSTICS.set(true);
            RendererConfig.GPU_ENGINE_ENABLED.set(false);
            RendererConfig.GPU_ENGINE_MODE.set(AdaptiveGpuController.Mode.PERFORMANCE);
            RendererConfig.TARGET_FRAME_TIME.set(5.0);
            RendererConfig.reload();
            UUID playerId = minecraft.player.getUUID();
            var server = minecraft.getSingleplayerServer();
            if (server == null) throw new IllegalStateException("Acceptance must use a local integrated server");
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("Acceptance player disappeared");
                CinematicManager.stop(player, CinematicStopReason.SKIPPED);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(server.overworld(), 20, 140, 20, 0, -25);
                server.overworld().setDayTime(6000);
                var source = player.createCommandSourceStack().withPermission(4);
                server.getCommands().performPrefixedCommand(source, "wilderness weather force cloud cumulonimbus");
            });
            advance(now, "baseline warm-up");
            return;
        }
        if (minecraft.screen != null) return;
        if (previousFrame != 0) CADENCE.record(now - previousFrame);
        previousFrame = now;
        admissions = Math.max(admissions, WildernessRenderingFramework.gpuEngineSnapshot().accepted());
        long elapsed = now - phaseStarted;
        if (phase == 0 && elapsed >= 20_000_000_000L) {
            initialWidth = minecraft.getWindow().getWidth();
            initialHeight = minecraft.getWindow().getHeight();
            report(minecraft, "baseline");
            RendererConfig.GPU_ENGINE_ENABLED.set(true);
            RendererConfig.reload();
            phase = 1;
            advance(now, "adaptive cloud policy");
        } else if (phase == 1 && elapsed >= 30_000_000_000L) {
            report(minecraft, "enabled");
            requireFreshAdmissions("enabled");
            phase = 2;
            reload = minecraft.reloadResourcePacks();
            advance(now, "resource reload");
        } else if (phase == 2 && reload.isDone()) {
            reload.join();
            if (WildernessRenderingFramework.gpuEngineSnapshot().active()) {
                throw new IllegalStateException("Reload retained active GPU policy before fresh warm-up");
            }
            phase = 3;
            advance(now, "post-reload warm-up");
        } else if (phase == 3 && elapsed >= 15_000_000_000L) {
            requireFreshAdmissions("resource reload");
            report(minecraft, "reloaded");
            minecraft.getWindow().setWindowed(960, 540);
            phase = 4;
            advance(now, "resize warm-up");
        } else if (phase == 4 && elapsed >= 15_000_000_000L) {
            requireFreshAdmissions("resize");
            if (minecraft.getWindow().getWidth() == initialWidth && minecraft.getWindow().getHeight() == initialHeight) {
                throw new IllegalStateException("Acceptance resize did not change the framebuffer dimensions");
            }
            report(minecraft, "resized");
            stopping = true;
            minecraft.execute(() -> {
                minecraft.level.disconnect();
                minecraft.disconnect(new TitleScreen());
                if (WildernessRenderingFramework.gpuEngineSnapshot().accepted() != 0
                        || WildernessRenderingFramework.gpuEngineSnapshot().active()) {
                    throw new IllegalStateException("World unload retained GPU policy observations");
                }
                ModConstants.LOGGER.info("[WAGE-ACCEPT] PASS optimizedAdmissions={} reload=true resize=true unload=true", admissions);
                minecraft.stop();
            });
        }
    }

    private static void advance(long now, String label) {
        phaseStarted = now;
        previousFrame = 0;
        CADENCE.clear();
        ModConstants.LOGGER.info("[WAGE-ACCEPT] phase={}", label);
    }

    private static void requireFreshAdmissions(String stage) {
        if (WildernessRenderingFramework.gpuEngineSnapshot().accepted() == 0) {
            throw new IllegalStateException("Acceptance did not observe fresh optimized cloud admission after " + stage);
        }
    }

    private static void report(Minecraft minecraft, String label) {
        var cadence = CADENCE.snapshot();
        ModConstants.LOGGER.info("[WAGE-ACCEPT] sample={} frames={} cadenceAverageNs={} medianNs={} p95Ns={} p99Ns={} policy={} framebuffer={}x{}",
                label, cadence.samples(), cadence.averageNanos(), cadence.medianNanos(), cadence.p95Nanos(),
                cadence.p99Nanos(), WildernessRenderingFramework.gpuEngineSnapshot(),
                minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        for (String line : WildernessRenderingFramework.debugLines()) {
            ModConstants.LOGGER.info("[WAGE-ACCEPT] {}", line);
        }
        // One-shot vanilla screenshots belong only to this explicitly requested acceptance run.
        Screenshot.grab(minecraft.gameDirectory, "wage-" + label + ".png", minecraft.getMainRenderTarget(),
                message -> ModConstants.LOGGER.info("[WAGE-ACCEPT] screenshot={}", message.getString()));
    }
}
