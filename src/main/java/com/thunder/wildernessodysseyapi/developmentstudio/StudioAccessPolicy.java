package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.developmentstudio.config.StudioConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative gate shared by every Studio command, item, and payload.
 *
 * <p>A Studio world (or explicit test-world config) establishes scope. The
 * integrated-server owner or permission level 2 establishes authority.</p>
 */
public final class StudioAccessPolicy {
    private StudioAccessPolicy() {
    }

    /** Evaluates both world scope and player authority for one request. */
    public static Result evaluate(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        boolean studioWorld = StudioWorldAccess.isDevelopmentStudioWorld(server);
        if (!studioWorld && !StudioConfig.ALLOW_IN_NORMAL_WORLDS.get()) {
            return Result.DENIED_WORLD;
        }

        boolean singleplayerOwner = server.isSingleplayerOwner(player.getGameProfile());
        boolean operator = player.createCommandSourceStack().hasPermission(2);
        return singleplayerOwner || operator ? Result.ALLOWED : Result.DENIED_PERMISSION;
    }

    /** Sends localized feedback for a rejected request. */
    public static void explainDenial(ServerPlayer player, Result result) {
        if (result == Result.DENIED_WORLD) {
            player.displayClientMessage(Component.translatable(
                    "message.wildernessodysseyapi.studio.denied_world"), false);
        } else if (result == Result.DENIED_PERMISSION) {
            player.displayClientMessage(Component.translatable(
                    "message.wildernessodysseyapi.studio.denied_permission"), false);
        }
    }

    public enum Result {
        ALLOWED,
        DENIED_WORLD,
        DENIED_PERMISSION
    }
}
