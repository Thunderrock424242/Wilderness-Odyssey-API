package com.thunder.wildernessodysseyapi.lorebook.map;

import com.thunder.wildernessodysseyapi.lorebook.network.SyncCodexMapPayload;
import com.thunder.wildernessodysseyapi.meteor.worldgen.MeteorSavedData;
import com.thunder.wildernessodysseyapi.worldgen.spawn.CryoSpawnData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the map data sent to the Field Codex and published to BlueMap.
 *
 * <p>Only persisted server-owned discoveries are exported here. The client is
 * responsible for drawing the player's live position, while this provider keeps
 * durable world POIs such as starter cryo tubes and meteor impacts consistent
 * across map surfaces.</p>
 */
public final class CodexMapPoiProvider {
    private static final int CRYO_COLOR = 0xFF64D6FF;
    private static final int METEOR_COLOR = 0xFFFF8B3D;
    private static final int SPAWN_COLOR = 0xFFEAD95B;

    private CodexMapPoiProvider() {
    }

    /** Builds the full map sync payload for one player opening the Codex. */
    public static SyncCodexMapPayload buildPayload(ServerPlayer player) {
        List<CodexMapPoi> pois = CodexMapServerConfig.SYNC_WORLD_POIS.get()
                ? collectPois(player.server)
                : List.of();
        return new SyncCodexMapPayload(CodexMapServerConfig.settings(), pois);
    }

    /** Collects all currently known Wilderness Odyssey map markers. */
    public static List<CodexMapPoi> collectPois(MinecraftServer server) {
        List<CodexMapPoi> pois = new ArrayList<>();
        int maxPois = Math.max(0, CodexMapServerConfig.MAX_SYNCED_POIS.get());
        if (maxPois == 0) {
            return List.of();
        }

        for (ServerLevel level : server.getAllLevels()) {
            collectCryoPois(level, pois, maxPois);
            collectMeteorPois(level, pois, maxPois);
            if (pois.size() >= maxPois) {
                break;
            }
        }

        return pois.stream()
                .sorted(Comparator.comparing(CodexMapPoi::type).thenComparing(CodexMapPoi::label))
                .limit(maxPois)
                .toList();
    }

    private static void collectCryoPois(ServerLevel level, List<CodexMapPoi> pois, int maxPois) {
        if (!level.dimension().equals(Level.OVERWORLD) || pois.size() >= maxPois) {
            return;
        }

        ResourceLocation dimension = level.dimension().location();
        BlockPos spawn = level.getSharedSpawnPos();
        pois.add(CodexMapPoi.at(
                "spawn_" + safeId(dimension),
                "World Spawn",
                "spawn",
                dimension,
                spawn,
                SPAWN_COLOR
        ));

        for (BlockPos pos : CryoSpawnData.get(level).getPositions()) {
            if (pois.size() >= maxPois) {
                return;
            }
            pois.add(CodexMapPoi.at(
                    "cryo_" + safeId(dimension) + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ(),
                    "Cryo Tube",
                    "cryo",
                    dimension,
                    pos,
                    CRYO_COLOR
            ));
        }
    }

    private static void collectMeteorPois(ServerLevel level, List<CodexMapPoi> pois, int maxPois) {
        if (pois.size() >= maxPois) {
            return;
        }

        ResourceLocation dimension = level.dimension().location();
        int index = 1;
        for (MeteorSavedData.MeteorRecord meteor : MeteorSavedData.get(level).getMeteors()) {
            if (pois.size() >= maxPois) {
                return;
            }
            BlockPos center = meteor.center();
            pois.add(CodexMapPoi.at(
                    "meteor_" + safeId(dimension) + "_" + center.getX() + "_" + center.getZ(),
                    "Meteor Impact " + index,
                    "meteor",
                    dimension,
                    center,
                    METEOR_COLOR
            ));
            index++;
        }
    }

    private static String safeId(ResourceLocation location) {
        return location.toString().replace(':', '_').replace('/', '_');
    }
}
