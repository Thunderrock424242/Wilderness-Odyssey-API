package com.thunder.wildernessodysseyapi.ModPackPatches;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.LOGGER;

/**
 * A FreezeChecker mod that tries to reduce world corruption and false positives by:
 *  - Using repeated checks (multiple intervals of freeze detection).
 *  - Attempting a graceful shutdown instead of a hard crash.
 */
@EventBusSubscriber
public class FreezeChecker {

    // Track the last time (in ms) that a server tick completed.
    private static volatile long lastTickTime = System.currentTimeMillis();

    // How many consecutive checks we want before deciding it's truly frozen
    private static final int MAX_FREEZE_CHECKS = 3;

    // How long (in ms) between each watchdog check
    private static final long CHECK_INTERVAL_MS = 5000L;

    // How many ms must pass without a tick before we consider the server "frozen"
    private static final long FREEZE_TIMEOUT_MS = 60_000L; // 60 seconds

    public FreezeChecker() {
        // Register our tick event to the Forge bus
        NeoForge.EVENT_BUS.register(this);

        // Start the watchdog thread
        Thread watchdogThread = new Thread(new Watchdog(), "FreezeChecker-Watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.start();

        LOGGER.info("FreezeChecker mod initialized with repeated checks and graceful shutdown.");
    }

    /**
     * Update lastTickTime each time the server finishes a tick.
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            lastTickTime = System.currentTimeMillis();
        }
    }

    /**
     * Watchdog Runnable that checks periodically whether we've exceeded the freeze threshold
     * multiple consecutive times. If so, it attempts to generate a crash report and gracefully shut down.
     */
    private static class Watchdog implements Runnable {
        private int freezeCount = 0;

        @Override
        public void run() {
            while (true) {
                long now = System.currentTimeMillis();
                long delta = now - lastTickTime;

                if (delta > FREEZE_TIMEOUT_MS) {
                    freezeCount++;
                    LOGGER.warn("FreezeChecker: Potential freeze detected ({} ms behind). Attempt {}/{}",
                            delta, freezeCount, MAX_FREEZE_CHECKS);

                    if (freezeCount >= MAX_FREEZE_CHECKS) {
                        // We assume it's truly stuck now
                        LOGGER.fatal("FreezeChecker: Server has not ticked for {} ms. Initiating graceful shutdown.", delta);
                        attemptGracefulShutdown(delta);
                        // After calling attemptGracefulShutdown, we break from the loop so we don't keep spamming.
                        break;
                    }
                } else {
                    // The server ticked again, reset the freeze counter
                    freezeCount = 0;
                }

                try {
                    Thread.sleep(CHECK_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                }
            }
        }

        /**
         * Tries to produce a CrashReport, save it, then instruct the server to shut down normally.
         */
        private void attemptGracefulShutdown(long freezeDurationMs) {
            try {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    // If we don't have a server instance, just log and exit
                    LOGGER.fatal("No server instance found. Exiting immediately.");
                    System.exit(1);
                    return;
                }

                // 1) Generate a crash report
                CrashReport crashReport = CrashReport.makeCrashReport(
                        new Exception("FreezeChecker: Server freeze detection"),
                        "Server freeze detected by FreezeChecker"
                );
                CrashReportCategory category = crashReport.makeCategory("FreezeChecker Info");
                category.addDetail("Freeze Duration (ms)", () -> String.valueOf(freezeDurationMs));
                category.addDetail("Threshold (ms)", () -> String.valueOf(FREEZE_TIMEOUT_MS));
                category.addDetail("Consecutive Checks Reached", () -> String.valueOf(MAX_FREEZE_CHECKS));

                // 2) Save the crash report with a timestamp
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
                File crashDir = new File(String.valueOf(server.getServerDirectory()), "crash-reports");
                if (!crashDir.exists() && !crashDir.mkdirs()) {
                    LOGGER.error("Could not create crash-reports directory!");
                }
                File crashFile = new File(crashDir, "freeze-detected-" + timestamp + ".txt");
                CrashReport.saveCrashReport(crashReport, crashFile);

                LOGGER.fatal("FreezeChecker: Crash report saved to {}", crashFile.getAbsolutePath());

                // 3) Attempt normal server shutdown
                // This is akin to running the /stop command,
                // giving the server a chance to save data and shut down gracefully.
                LOGGER.fatal("FreezeChecker: Attempting server shutdown...");
                server.initiateShutdown(false);

                // Optional: If you absolutely want to ensure exit (in case the server is truly stuck):
                //   - Wait a bit, then call System.exit(1).
                //   - But there's a chance the server's main thread is truly blocked and won't respond anyway.

            } catch (Exception e) {
                // If something goes wrong here, fall back to a hard exit:
                LOGGER.fatal("FreezeChecker: Failed to gracefully shut down! Forcing exit.", e);
                System.exit(1);
            }
        }
    }
}