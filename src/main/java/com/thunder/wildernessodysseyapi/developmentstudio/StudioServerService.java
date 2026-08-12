package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.developmentstudio.bookmark.StudioBookmark;
import com.thunder.wildernessodysseyapi.developmentstudio.campus.StudioLocationDefinition;
import com.thunder.wildernessodysseyapi.developmentstudio.campus.StudioLocationRegistry;
import com.thunder.wildernessodysseyapi.developmentstudio.config.StudioConfig;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspection;
import com.thunder.wildernessodysseyapi.developmentstudio.network.OpenStudioPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioBookmarkActionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Server-owned implementation of screen opening, bookmarks, and safe teleports. */
public final class StudioServerService {
    private StudioServerService() {
    }

    /** Authorizes and opens the World module without accepting client state. */
    public static boolean open(ServerPlayer player) {
        return open(player, "world", null);
    }

    /** Authorizes and opens an inspector result produced from a server-owned target. */
    public static boolean openInspector(ServerPlayer player, StudioInspection inspection) {
        return open(player, "inspector", inspection);
    }

    /** Handles a bounded bookmark operation after rechecking authorization. */
    public static void handleBookmarkAction(ServerPlayer player, StudioBookmarkActionPayload payload) {
        if (!authorize(player)) {
            return;
        }

        StudioWorldData data = StudioWorldData.getOrCreate(player.getServer());
        switch (payload.action()) {
            case CREATE -> createBookmark(player, data, payload);
            case UPDATE -> updateBookmark(player, data, payload);
            case DELETE -> deleteBookmark(player, data, payload.bookmarkId());
            case TELEPORT -> teleportToBookmark(player, data, payload.bookmarkId());
        }
        open(player, "locations", null);
    }

    /** Teleports only to a registered, available campus location. */
    public static void teleportToCampusLocation(ServerPlayer player, ResourceLocation locationId) {
        if (!authorize(player)) {
            return;
        }

        Optional<StudioLocationDefinition> definition = StudioLocationRegistry.get(locationId)
                .filter(StudioLocationDefinition::available);
        if (definition.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.wildernessodysseyapi.studio.location_missing"), false);
            return;
        }

        StudioWorldData data = StudioWorldData.getOrCreate(player.getServer());
        Optional<BlockPos> campusOrigin = data.campusOrigin();
        if (campusOrigin.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.wildernessodysseyapi.studio.campus_missing"), false);
            return;
        }

        ServerLevel overworld = player.getServer().overworld();
        BlockPos target = campusOrigin.get().offset(definition.get().offset());
        player.teleportTo(overworld,
                target.getX() + 0.5D,
                target.getY(),
                target.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot());
    }

    private static boolean open(ServerPlayer player, String module, StudioInspection inspection) {
        if (!authorize(player)) {
            return false;
        }

        MinecraftServer server = player.getServer();
        StudioWorldData data = StudioWorldData.getOrCreate(server);
        PacketDistributor.sendToPlayer(player, new OpenStudioPayload(
                module,
                data.isDevelopmentStudioWorld(),
                server.getWorldData().worldGenOptions().seed(),
                data.campusOrigin().orElse(null),
                data.bookmarks(),
                inspection
        ));
        return true;
    }

    private static boolean authorize(ServerPlayer player) {
        StudioAccessPolicy.Result result = StudioAccessPolicy.evaluate(player);
        if (result == StudioAccessPolicy.Result.ALLOWED) {
            return true;
        }
        StudioAccessPolicy.explainDenial(player, result);
        return false;
    }

    private static void createBookmark(ServerPlayer player,
                                       StudioWorldData data,
                                       StudioBookmarkActionPayload payload) {
        if (data.bookmarks().size() >= StudioConfig.MAX_BOOKMARKS.get()) {
            player.displayClientMessage(Component.translatable(
                    "message.wildernessodysseyapi.studio.bookmark_limit"), false);
            return;
        }

        BlockPos position = player.blockPosition();
        ResourceLocation biome = player.serverLevel().getBiome(position)
                .unwrapKey()
                .map(ResourceKey::location)
                .orElse(ResourceLocation.withDefaultNamespace("plains"));
        data.addBookmark(new StudioBookmark(
                UUID.randomUUID(),
                payload.name(),
                player.serverLevel().dimension().location(),
                position,
                player.getYRot(),
                player.getXRot(),
                biome,
                payload.notes(),
                payload.tags(),
                System.currentTimeMillis()
        ));
    }

    private static void updateBookmark(ServerPlayer player,
                                       StudioWorldData data,
                                       StudioBookmarkActionPayload payload) {
        Optional<StudioBookmark> existing = data.bookmark(payload.bookmarkId());
        if (existing.isEmpty()) {
            bookmarkMissing(player);
            return;
        }
        data.updateBookmark(existing.get().withDetails(payload.name(), payload.notes(), payload.tags()));
    }

    private static void deleteBookmark(ServerPlayer player, StudioWorldData data, UUID id) {
        if (!data.removeBookmark(id)) {
            bookmarkMissing(player);
        }
    }

    private static void teleportToBookmark(ServerPlayer player, StudioWorldData data, UUID id) {
        Optional<StudioBookmark> bookmark = data.bookmark(id);
        if (bookmark.isEmpty()) {
            bookmarkMissing(player);
            return;
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, bookmark.get().dimension());
        ServerLevel targetLevel = player.getServer().getLevel(dimension);
        if (targetLevel == null) {
            player.displayClientMessage(Component.translatable(
                    "message.wildernessodysseyapi.studio.dimension_missing"), false);
            return;
        }

        BlockPos position = bookmark.get().position();
        if (position.getY() < targetLevel.getMinBuildHeight()
                || position.getY() >= targetLevel.getMaxBuildHeight()
                || !targetLevel.getWorldBorder().isWithinBounds(position)) {
            player.displayClientMessage(Component.translatable(
                    "message.wildernessodysseyapi.studio.location_missing"), false);
            return;
        }
        player.teleportTo(targetLevel,
                position.getX() + 0.5D,
                position.getY(),
                position.getZ() + 0.5D,
                bookmark.get().yaw(),
                bookmark.get().pitch());
    }

    private static void bookmarkMissing(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.wildernessodysseyapi.studio.bookmark_missing"), false);
    }
}
