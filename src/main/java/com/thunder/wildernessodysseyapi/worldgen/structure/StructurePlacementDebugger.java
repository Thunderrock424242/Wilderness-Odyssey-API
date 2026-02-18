package com.thunder.wildernessodysseyapi.worldgen.structure;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Small debug utility that records structure placement attempts and exposes them to chat commands.
 */
public final class StructurePlacementDebugger {
    private static final int MAX_ENTRIES = 50;
    private static final Deque<PlacementAttempt> ATTEMPTS = new ArrayDeque<>();

    private StructurePlacementDebugger() {
    }

    /**
     * Starts tracking a placement attempt for the given structure.
     */
    public static PlacementAttempt startAttempt(ServerLevel level, ResourceLocation id, Vec3i size, BlockPos origin) {
        PlacementAttempt attempt = new PlacementAttempt(
                id,
                level.dimension().location(),
                origin,
                size,
                level.getGameTime()
        );
        ATTEMPTS.addFirst(attempt);
        trim();
        log("Starting placement", attempt, false);
        return attempt;
    }

    /** Marks the attempt as successful and logs the detail. */
    public static void markSuccess(PlacementAttempt attempt, String detail) {
        if (attempt == null) {
            return;
        }
        attempt.finish(true, detail);
        log("Placed", attempt, false);
    }

    /** Marks the attempt as failed and logs the detail. */
    public static void markFailure(PlacementAttempt attempt, String detail) {
        if (attempt == null) {
            return;
        }
        attempt.finish(false, detail);
        log("Failed to place", attempt, true);
    }

    /** Returns up to {@code limit} most recent placement attempts (newest first). */
    public static List<PlacementAttempt> recent(int limit) {
        List<PlacementAttempt> copy = new ArrayList<>(ATTEMPTS);
        Collections.sort(copy); // latest attempts first
        return copy.subList(0, Math.min(limit, copy.size()));
    }

    private static void trim() {
        while (ATTEMPTS.size() > MAX_ENTRIES) {
            ATTEMPTS.removeLast();
        }
    }

    private static void log(String prefix, PlacementAttempt attempt, boolean warn) {
        if (!StructureConfig.DEBUG_LOG_PLACEMENTS.get()) {
            return;
        }

        String formatted = "%s %s at %s size %s (%s)".formatted(
                prefix,
                attempt.id(),
                formatPos(attempt.origin(), attempt.dimension()),
                attempt.size(),
                attempt.statusDetail()
        );

        if (warn) {
            ModConstants.LOGGER.warn(formatted);
        } else {
            ModConstants.LOGGER.info(formatted);
        }
    }

    private static String formatPos(BlockPos pos, ResourceLocation dimension) {
        return "[%s] %s,%s,%s".formatted(dimension.toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Mutable attempt descriptor that can be sorted by creation time (newest first).
     */
    public static final class PlacementAttempt implements Comparable<PlacementAttempt> {
        private final ResourceLocation id;
        private final ResourceLocation dimension;
        private final BlockPos origin;
        private final Vec3i size;
        private final long createdGameTime;
        private boolean finished;
        private boolean success;
        private String detail = "pending";
        private final Instant createdAt = Instant.now();

        private PlacementAttempt(ResourceLocation id, ResourceLocation dimension, BlockPos origin, Vec3i size, long gameTime) {
            this.id = id;
            this.dimension = dimension;
            this.origin = origin;
            this.size = size;
            this.createdGameTime = gameTime;
        }

        public ResourceLocation id() {
            return id;
        }

        public ResourceLocation dimension() {
            return dimension;
        }

        public BlockPos origin() {
            return origin;
        }

        public Vec3i size() {
            return size;
        }

        public long createdGameTime() {
            return createdGameTime;
        }

        public boolean success() {
            return success;
        }

        public String statusDetail() {
            return detail;
        }

        private void finish(boolean success, String detail) {
            this.finished = true;
            this.success = success;
            this.detail = detail;
        }

        @Override
        public int compareTo(PlacementAttempt other) {
            return other.createdAt.compareTo(this.createdAt);
        }

        @Override
        public String toString() {
            return "%s (%s)".formatted(id, success ? "ok" : finished ? "fail" : "pending");
        }
    }
}
