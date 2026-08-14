package com.thunder.wildernessodysseyapi.developmentstudio.structure;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioServerService;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioWorldData;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioStructureActionPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioRegionBlockSnapshot;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioResetPolicy;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegion;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-owned previews, exact lab placement, and persisted bounded resets. */
public final class StudioStructureService {
    private static final long PREVIEW_DURATION_TICKS = 20L * 120L;
    private static final int MAX_PREVIEW_AXIS = 512;
    private static final long MAX_PREVIEW_VOLUME = 16_777_216L;
    private static final Map<UUID, StudioStructurePreview> PREVIEWS = new ConcurrentHashMap<>();

    private StudioStructureService() {
    }

    /** Reauthorizes and executes one allowlisted high-level structure action. */
    public static void handle(ServerPlayer player, StudioStructureActionPayload payload) {
        if (!StudioServerService.authorize(player)) {
            return;
        }
        Optional<StudioStructureDefinition> definition = StudioStructureRegistry.get(payload.structureId());
        if (definition.isEmpty()) {
            player.displayClientMessage(Component.literal("That Studio structure is no longer registered."), false);
            StudioServerService.openModule(player, "structures", null);
            return;
        }
        if (payload.action() != StudioStructureActionPayload.Action.RESET_LAB
                && payload.action() != StudioStructureActionPayload.Action.RELOAD_TEMPLATE
                && !templateSizeAllowed(player.serverLevel(), definition.get())) {
            player.displayClientMessage(Component.literal(
                    "That template exceeds the Studio preview size limit."), false);
            StudioServerService.openModule(player, "structures", null);
            return;
        }

        switch (payload.action()) {
            case PREVIEW_LAB -> previewLab(player, definition.get(), payload);
            case PREVIEW_HERE -> previewHere(player, definition.get(), payload);
            case PLACE_LAB -> placeLab(player, definition.get(), payload);
            case RESET_LAB -> resetLab(player);
            case RELOAD_TEMPLATE -> {
                definition.get().placer().reload(player.serverLevel());
                PREVIEWS.remove(player.getUUID());
                player.displayClientMessage(Component.literal("Studio template cache reloaded."), false);
            }
        }
        StudioServerService.openModule(player, "structures", null);
    }

    /** Returns only the caller's unexpired server-computed preview. */
    public static StudioStructurePreview currentPreview(ServerPlayer player) {
        StudioStructurePreview preview = PREVIEWS.get(player.getUUID());
        if (preview == null) {
            return null;
        }
        ServerLevel level = level(player, preview.dimension());
        if (level == null || level.getGameTime() > preview.expiresAtGameTime()) {
            PREVIEWS.remove(player.getUUID());
            return null;
        }
        return preview;
    }

    /** Releases ephemeral per-player preview state on logout. */
    public static void clearPreview(UUID playerId) {
        if (playerId != null) {
            PREVIEWS.remove(playerId);
        }
    }

    private static void previewLab(ServerPlayer player,
                                   StudioStructureDefinition definition,
                                   StudioStructureActionPayload payload) {
        StudioWorldData data = StudioWorldData.getOrCreate(player.getServer());
        StudioTestRegion region = structureRegion(data).orElse(null);
        if (region == null) {
            missingLab(player);
            return;
        }
        ServerLevel level = level(player, region.dimension());
        if (level == null) {
            missingLab(player);
            return;
        }
        BlockPos origin = centeredOrigin(level, region, definition, payload);
        setPreview(player, level, definition, origin, payload);
    }

    private static void previewHere(ServerPlayer player,
                                    StudioStructureDefinition definition,
                                    StudioStructureActionPayload payload) {
        ServerLevel level = player.serverLevel();
        Vec3 target = player.getEyePosition().add(player.getLookAngle().scale(8.0D));
        BlockPos origin = BlockPos.containing(target);
        if (origin.getY() < level.getMinBuildHeight() || origin.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(origin)) {
            player.displayClientMessage(Component.literal("The preview target is outside world bounds."), false);
            return;
        }
        setPreview(player, level, definition, origin, payload);
    }

    private static void placeLab(ServerPlayer player,
                                 StudioStructureDefinition definition,
                                 StudioStructureActionPayload payload) {
        if (!definition.labPlaceable()) {
            player.displayClientMessage(Component.literal(
                    "This template is preview-only; only the Studio Lab Fixture may replace the lab pad."), false);
            return;
        }

        StudioWorldData data = StudioWorldData.getOrCreate(player.getServer());
        StudioTestRegion region = structureRegion(data).orElse(null);
        if (region == null || region.resetPolicy() != StudioResetPolicy.BLOCK_SNAPSHOT) {
            missingLab(player);
            return;
        }
        ServerLevel level = level(player, region.dimension());
        if (level == null) {
            missingLab(player);
            return;
        }

        StudioRegionBlockSnapshot baseline = data.regionSnapshot(region.id()).orElse(null);
        if (baseline == null) {
            try {
                baseline = StudioRegionBlockSnapshot.capture(level, region);
                data.putRegionSnapshotIfAbsent(baseline);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                player.displayClientMessage(Component.literal(
                        "Structure Lab baseline capture refused: " + exception.getMessage()), false);
                return;
            }
        }
        if (!baseline.restore(level, region)) {
            player.displayClientMessage(Component.literal("The persisted Structure Lab baseline no longer matches."), false);
            return;
        }

        BlockPos origin = centeredOrigin(level, region, definition, payload);
        BoundingBox allowed = box(region);
        if (origin == null || definition.placer().placeExact(
                level, origin, payload.rotation(), payload.mirror(), allowed
        ) == null) {
            baseline.restore(level, region);
            player.displayClientMessage(Component.literal("Structure placement was refused by the lab bounds."), false);
            return;
        }
        setPreview(player, level, definition, origin, payload);
        player.displayClientMessage(Component.literal("Placed the fixture inside the bounded Structure Lab."), false);
    }

    private static void resetLab(ServerPlayer player) {
        StudioWorldData data = StudioWorldData.getOrCreate(player.getServer());
        StudioTestRegion region = structureRegion(data).orElse(null);
        if (region == null) {
            missingLab(player);
            return;
        }
        ServerLevel level = level(player, region.dimension());
        StudioRegionBlockSnapshot baseline = data.regionSnapshot(region.id()).orElse(null);
        if (level == null || baseline == null) {
            player.displayClientMessage(Component.literal("No Structure Lab baseline has been captured yet."), false);
            return;
        }
        if (baseline.restore(level, region)) {
            PREVIEWS.remove(player.getUUID());
            player.displayClientMessage(Component.literal("Structure Lab restored to its persisted baseline."), false);
        } else {
            player.displayClientMessage(Component.literal("Structure Lab reset was refused because its bounds changed."), false);
        }
    }

    private static BlockPos centeredOrigin(ServerLevel level,
                                           StudioTestRegion region,
                                           StudioStructureDefinition definition,
                                           StudioStructureActionPayload payload) {
        BoundingBox relative = definition.placer().previewBoundingBox(
                level, BlockPos.ZERO, payload.rotation(), payload.mirror()
        );
        if (relative == null) {
            return null;
        }
        int width = relative.maxX() - relative.minX() + 1;
        int height = relative.maxY() - relative.minY() + 1;
        int depth = relative.maxZ() - relative.minZ() + 1;
        int regionWidth = region.max().getX() - region.min().getX() + 1;
        int regionHeight = region.max().getY() - region.min().getY() + 1;
        int regionDepth = region.max().getZ() - region.min().getZ() + 1;
        if (width > regionWidth || height > regionHeight || depth > regionDepth) {
            return null;
        }
        int targetMinX = region.min().getX() + (regionWidth - width) / 2;
        int targetMinZ = region.min().getZ() + (regionDepth - depth) / 2;
        return new BlockPos(
                targetMinX - relative.minX(),
                region.min().getY() - relative.minY(),
                targetMinZ - relative.minZ()
        );
    }

    private static void setPreview(ServerPlayer player,
                                   ServerLevel level,
                                   StudioStructureDefinition definition,
                                   BlockPos origin,
                                   StudioStructureActionPayload payload) {
        if (origin == null) {
            player.displayClientMessage(Component.literal("This template does not fit the requested preview area."), false);
            return;
        }
        BoundingBox bounds = definition.placer().previewBoundingBox(
                level, origin, payload.rotation(), payload.mirror()
        );
        if (bounds == null) {
            player.displayClientMessage(Component.literal("The structure template could not be loaded."), false);
            return;
        }
        int width = bounds.maxX() - bounds.minX() + 1;
        int height = bounds.maxY() - bounds.minY() + 1;
        int depth = bounds.maxZ() - bounds.minZ() + 1;
        long volume = (long) width * height * depth;
        BlockPos min = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        BlockPos max = new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        if (width <= 0 || height <= 0 || depth <= 0
                || width > MAX_PREVIEW_AXIS || height > MAX_PREVIEW_AXIS || depth > MAX_PREVIEW_AXIS
                || volume > MAX_PREVIEW_VOLUME
                || bounds.minY() < level.getMinBuildHeight()
                || bounds.maxY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(min)
                || !level.getWorldBorder().isWithinBounds(max)) {
            player.displayClientMessage(Component.literal(
                    "The transformed preview exceeds Studio or world bounds."), false);
            return;
        }
        PREVIEWS.put(player.getUUID(), new StudioStructurePreview(
                definition.id(),
                level.dimension().location(),
                origin,
                min,
                max,
                payload.rotation(),
                payload.mirror(),
                level.getGameTime() + PREVIEW_DURATION_TICKS
        ));
    }

    private static Optional<StudioTestRegion> structureRegion(StudioWorldData data) {
        return data.testRegion(StudioTestRegionRegistry.STRUCTURE_LAB);
    }

    private static ServerLevel level(ServerPlayer player, net.minecraft.resources.ResourceLocation dimension) {
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension);
        return player.getServer().getLevel(key);
    }

    private static BoundingBox box(StudioTestRegion region) {
        return new BoundingBox(
                region.min().getX(), region.min().getY(), region.min().getZ(),
                region.max().getX(), region.max().getY(), region.max().getZ()
        );
    }

    private static void missingLab(ServerPlayer player) {
        player.displayClientMessage(Component.literal("The registered Structure Lab is unavailable."), false);
    }

    private static boolean templateSizeAllowed(ServerLevel level, StudioStructureDefinition definition) {
        net.minecraft.core.Vec3i size = definition.placer().peekSize(level);
        long volume = (long) size.getX() * size.getY() * size.getZ();
        return size.getX() > 0 && size.getY() > 0 && size.getZ() > 0
                && size.getX() <= MAX_PREVIEW_AXIS
                && size.getY() <= MAX_PREVIEW_AXIS
                && size.getZ() <= MAX_PREVIEW_AXIS
                && volume <= MAX_PREVIEW_VOLUME;
    }
}
