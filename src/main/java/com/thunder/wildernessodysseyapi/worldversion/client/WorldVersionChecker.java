package com.thunder.wildernessodysseyapi.worldversion.client;

import com.thunder.wildernessodysseyapi.worldupgrade.WorldUpgradeManager;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Compatibility bridge for the historical {@code /updateworldversion} command.
 *
 * <p>The old JSON label system could claim success without migrating any chunk. The command now
 * starts the authoritative SavedData-backed queue and directs operators to its status command.</p>
 *
 * @deprecated use {@code /worldupgrade} and {@link WorldUpgradeManager} directly
 */
@Deprecated(forRemoval = false)
@EventBusSubscriber
public final class WorldVersionChecker {

    private WorldVersionChecker() {
        // Compatibility bridge
    }

    /** Registers the legacy name as an honest redirect to the authoritative migration queue. */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("updateworldversion")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    WorldUpgradeManager.start(context.getSource().getServer());
                    WorldUpgradeManager.WorldUpgradeStatus status = WorldUpgradeManager.status(
                            context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Legacy command redirected: the world upgrade queue is running with "
                                    + status.queuedChunks() + " currently queued chunk(s). Use "
                                    + "/worldupgrade status, then /worldupgrade complete only after "
                                    + "the queue is empty and failures are zero."), true);
                    return 1;
                }));
    }

    /** Parses a legacy semantic version for source compatibility with older integrations. */
    public static int[] parseVersion(String version) {
        if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+")) {
            return new int[]{0, 0, 0};
        }
        String[] parts = version.split("\\.");
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException exception) {
            return new int[]{0, 0, 0};
        }
    }

    /** Compares two three-segment legacy semantic versions. */
    public static boolean isVersionGreater(int[] candidate, int[] baseline) {
        if (candidate == null || baseline == null || candidate.length < 3 || baseline.length < 3) {
            return false;
        }
        for (int index = 0; index < 3; index++) {
            if (candidate[index] != baseline[index]) {
                return candidate[index] > baseline[index];
            }
        }
        return false;
    }

    /** Describes which legacy semantic-version segment changed. */
    public static String getUpdateType(String oldVersion, String newVersion) {
        int[] oldSegments = parseVersion(oldVersion);
        int[] newSegments = parseVersion(newVersion);
        if (newSegments[0] > oldSegments[0]) {
            return "Major";
        }
        if (newSegments[1] > oldSegments[1]) {
            return "Minor";
        }
        return "Patch";
    }
}
