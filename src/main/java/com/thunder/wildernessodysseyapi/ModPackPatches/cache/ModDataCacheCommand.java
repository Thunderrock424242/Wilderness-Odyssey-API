package com.thunder.wildernessodysseyapi.ModPackPatches.cache;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Server command utilities for inspecting and clearing the mod data cache.
 */
public final class ModDataCacheCommand {
    private ModDataCacheCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("modcache")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ModDataCache.invalidateAll();
                            ctx.getSource().sendSuccess(() -> Component.literal("Cleared Wilderness Odyssey mod data cache."), true);
                            return 1;
                        }))
                .then(Commands.literal("stats")
                        .executes(ctx -> {
                            ModDataCache.CacheStats stats = ModDataCache.getStats();
                            long sizeMb = stats.totalSizeBytes() / 1024L / 1024L;
                            long limitMb = stats.sizeLimitBytes() <= 0 ? -1 : stats.sizeLimitBytes() / 1024L / 1024L;
                            String limitText = limitMb < 0 ? "no limit" : limitMb + "MB";
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Cache " + (stats.enabled() ? "enabled" : "disabled") + ": "
                                            + stats.entryCount() + " entries, " + sizeMb + "MB used (limit " + limitText + ")"),
                                    false);
                            return 1;
                        }))
        );
    }
}
