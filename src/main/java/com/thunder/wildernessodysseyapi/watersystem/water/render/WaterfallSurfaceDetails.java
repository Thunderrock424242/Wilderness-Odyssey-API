package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterRenderingConfig.WaterQuality;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WaterfallCurrent;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WaterfallHydraulics;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.LoadedWaterReadAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverChannelColumn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Whitewater material and impacts over native waterfall curtains. Only the
 * coordinator draws it; no fluid faces are suppressed and no volume is owned.
 * Discovery inspects 128 immutable metadata columns per tick. Four of 32 cached
 * candidates are refreshed per tick; rendering only consumes their cached quads.
 */
final class WaterfallSurfaceDetails {
    private static final int[] X = {0, 1, 0, -1};
    private static final int[] Z = {-1, 0, 1, 0};
    private static final Candidate[] CANDIDATES = new Candidate[32];
    private static ClientLevel activeLevel;
    private static int probeCursor;
    private static int refreshCursor;

    private WaterfallSurfaceDetails() { }

    static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (activeLevel != level) { clear(); activeLevel = level; }
        if (level == null || minecraft.player == null || !enabled()) { clear(); return; }
        int centerX = minecraft.player.getBlockX() >> 4;
        int centerZ = minecraft.player.getBlockZ() >> 4;
        for (int attempt = 0; attempt < 128; attempt++) {
            int index = probeCursor;
            probeCursor = (probeCursor + 1) % (25 * 256);
            int chunkIndex = index / 256;
            int chunkX = centerX + chunkIndex % 5 - 2;
            int chunkZ = centerZ + chunkIndex / 5 - 2;
            var snapshot = ClientWaterSnapshotStore.get(level, chunkX, chunkZ);
            if (snapshot == null) continue;
            int x = index & 15;
            int z = (index & 255) >> 4;
            var reach = snapshot.rivers().reachAt(x, z);
            if (reach == null || reach.kind() != RiverChannelColumn.Kind.WATERFALL) continue;
            int worldX = chunkX * 16 + x;
            int worldZ = chunkZ * 16 + z;
            if (!near(minecraft, worldX, worldZ)) continue;
            int slot = -1;
            for (int i = 0; i < CANDIDATES.length; i++) {
                Candidate candidate = CANDIDATES[i];
                if (candidate != null && candidate.x == worldX && candidate.z == worldZ) { slot = i; break; }
                if (candidate == null || candidate.expires < level.getGameTime()) slot = i;
            }
            if (slot >= 0) CANDIDATES[slot] = renewCandidate(CANDIDATES[slot], worldX, worldZ, reach.waterY(),
                    level.getGameTime() + 80);
        }
        for (int count = 0; count < 4; count++) {
            int slot = refreshCursor;
            refreshCursor = (refreshCursor + 1) % CANDIDATES.length;
            Candidate candidate = CANDIDATES[slot];
            if (candidate == null) continue;
            if (candidate.expires < level.getGameTime() || !near(minecraft, candidate.x, candidate.z)) {
                CANDIDATES[slot] = null;
                continue;
            }
            refresh(level, candidate);
        }
    }

    private static void refresh(ClientLevel level, Candidate candidate) {
        candidate.faces.clear();
        BlockPos base = new BlockPos(candidate.x, candidate.waterY, candidate.z);
        var fall = WaterfallCurrent.verified(level, base);
        if (fall == null) return;
        candidate.snapshot = ClientWaterSnapshotStore.getAtBlock(level, candidate.x, candidate.z);
        if (candidate.snapshot == null) return;
        var reach = fall.reach();
        float speed = (float) Math.max(Math.hypot(fall.inflowX(), fall.inflowZ()), -fall.fallingY());
        var reader = LoadedWaterReadAccess.blockReader(level);
        WaterfallSheetGeometry.emit(reach.waterY(), reach.topY(), speed, 64,
                y -> candidate.snapshot.contains(candidate.x & 15, y, candidate.z & 15),
                (side, y) -> {
                    var outside = new BlockPos(candidate.x + X[side], y, candidate.z + Z[side]);
                    var chunk = LoadedWaterReadAccess.loadedChunk(level, outside);
                    if (chunk == null) return false;
                    var state = chunk.getBlockState(outside);
                    return !state.getFluidState().is(FluidTags.WATER)
                            && state.getCollisionShape(reader, outside).isEmpty();
                },
                (side, y, foam) -> candidate.faces.add(new Face(side, y, foam,
                        LevelRenderer.getLightColor(level, new BlockPos(candidate.x, y, candidate.z)))));
        if (level.getGameTime() - candidate.lastImpact < 12) return;
        candidate.lastImpact = level.getGameTime();
        var impact = WaterfallHydraulics.sample(reach.topY() + 1, reach.waterY() + 1,
                0, reach.waterY() + 1, 0, fall.inflowX(), fall.inflowZ(), fall.fallingY(), level.getGameTime());
        double x = candidate.x + .5;
        double z = candidate.z + .5;
        if (WaterRenderingConfig.riverFoamEnabled()) {
            WaterSurfaceDisplacement.spawnWaterfallImpact(level, x, z, impact.foam());
            // Floating foam needs a real receiving surface; a falling column
            // deliberately fails the environmental pool's advection admission.
            for (int side = 0; side < 4; side++) {
                int rx = candidate.x + X[side];
                int rz = candidate.z + Z[side];
                var receiver = ClientWaterSnapshotStore.getAtBlock(level, rx, rz);
                if (receiver == null) continue;
                var column = receiver.column(rx & 15, rz & 15);
                if (!column.wet() || column.falling() || column.surfaceCovered()
                        || Math.abs(column.baseSurfaceY() - reach.waterY() - 1) > .8) continue;
                WaterEnvironmentalEffectPool.offer(WaterEnvironmentalEffectPool.Kind.FOAM,
                        rx + .5, column.baseSurfaceY(), rz + .5, fall.inflowX() * .03, fall.inflowZ() * .03,
                        impact.foam(), level.getGameTime());
                break;
            }
        }
        if (WaterRenderingConfig.riverSprayEnabled()) {
            WaterEnvironmentalEffectPool.offer(WaterEnvironmentalEffectPool.Kind.MIST,
                    x, reach.waterY() + 1, z, 0, 0, impact.foam(), level.getGameTime());
        }
    }

    static void render(RenderLevelStageEvent event) {
        if (!enabled() || WaterShaders.externalShaderPackOwnsWater()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level != activeLevel) return;
        int budget = quadBudget(WaterRenderingConfig.waterQuality());
        var camera = event.getCamera().getPosition();
        VertexConsumer buffer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.translucent());
        TextureAtlasSprite sprite = FluidSpriteCache.getFluidSprites(minecraft.level,
                BlockPos.containing(camera), Fluids.WATER.defaultFluidState())[1];
        float phase = WaterAnimationClock.stablePhase(minecraft.level.getGameTime(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false), 3.1);
        int emitted = 0;
        for (Candidate candidate : CANDIDATES) {
            if (candidate == null || candidate.snapshot != ClientWaterSnapshotStore.getAtBlock(
                    minecraft.level, candidate.x, candidate.z)) continue;
            for (Face face : candidate.faces) {
                if (emitted++ >= budget) return;
                int side = face.side();
                float normalX = X[side];
                float normalZ = Z[side];
                float cx = (float) (candidate.x + .5 - camera.x) + normalX * .502f;
                float cz = (float) (candidate.z + .5 - camera.z) + normalZ * .502f;
                float y = (float) (face.y() - camera.y);
                float width = .30f + .08f * (float) Math.sin(phase - face.y() * 1.8 + side);
                int alpha = Math.round((28 + 64 * face.foam())
                        * (.65f + .35f * (float) Math.sin(phase - face.y() * 2.4 + side * 1.7)));
                WaterfallSheetGeometry.quad(X[side], Z[side], cx, y, cz, width,
                        (vx, vy, vz, u, v) -> vertex(buffer, event, sprite, face, vx, vy, vz, u, v, alpha));
            }
        }
    }

    private static void vertex(VertexConsumer buffer, RenderLevelStageEvent event, TextureAtlasSprite sprite,
                               Face face, float x, float y, float z, float u, float v, int alpha) {
        var pose = event.getPoseStack().last();
        buffer.addVertex(pose, x, y, z).setColor(205, 232, 239, alpha)
                .setUv(sprite.getU(u), sprite.getV(v)).setLight(face.light())
                .setNormal(pose, X[face.side()], 0, Z[face.side()]);
    }

    static int quadBudget(WaterQuality quality) {
        return switch (quality) { case LOW -> 0; case MEDIUM -> 48; case HIGH -> 128; case CINEMATIC -> 192; };
    }

    private static boolean enabled() {
        return WaterRenderingConfig.ENABLE_WATERFALL_SHEETS.get()
                && WaterRenderingConfig.replacementWaterRenderingEnabled(Minecraft.getInstance().level)
                && quadBudget(WaterRenderingConfig.waterQuality()) > 0;
    }

    private static boolean near(Minecraft minecraft, int x, int z) {
        double dx = x + .5 - minecraft.player.getX();
        double dz = z + .5 - minecraft.player.getZ();
        int distance = WaterRenderingConfig.riverEffectDistanceBlocks();
        return dx * dx + dz * dz <= distance * distance;
    }

    static void clear() {
        java.util.Arrays.fill(CANDIDATES, null);
        activeLevel = null;
        probeCursor = 0;
        refreshCursor = 0;
    }

    record Face(int side, int y, float foam, int light) { }

    static Candidate renewCandidate(Candidate previous, int x, int z, int waterY, long expires) {
        if (previous != null && previous.x == x && previous.z == z && previous.waterY == waterY) {
            previous.expires = expires;
            return previous;
        }
        return new Candidate(x, z, waterY, expires);
    }

    static final class Candidate {
        final int x;
        final int z;
        final int waterY;
        long expires;
        final List<Face> faces = new ArrayList<>(32);
        ClientWaterChunkSnapshot snapshot;
        long lastImpact = Long.MIN_VALUE / 2;

        Candidate(int x, int z, int waterY, long expires) {
            this.x = x; this.z = z; this.waterY = waterY; this.expires = expires;
        }
    }
}
