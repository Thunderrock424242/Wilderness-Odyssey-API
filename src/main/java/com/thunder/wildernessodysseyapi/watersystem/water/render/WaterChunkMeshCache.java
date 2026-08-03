package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns stable, world-aligned GPU meshes derived only from immutable water snapshots.
 *
 * <p>Topology changes when a chunk-local snapshot or a relevant client block
 * changes. Each rebuild verifies the compact snapshot surface against the
 * matching physical water projection; continuous tide and Gerstner displacement
 * remains shader-owned, so oceans still do not rebuild every frame.</p>
 */
public final class WaterChunkMeshCache {

    private static final int BYTES_PER_VERTEX = DefaultVertexFormat.BLOCK.getVertexSize();
    private static final int FULL_BRIGHT = LightTexture.pack(15, 15);

    private final Map<Long, MeshGroup> groups = new ConcurrentHashMap<>();

    /** Rebuilds one dirty chunk and atomically replaces its previous GPU group. */
    public MeshGroup rebuild(ClientLevel level, ClientWaterChunkSnapshot snapshot) {
        long key = chunkKey(snapshot.chunkX(), snapshot.chunkZ());
        MeshGroup replacement = build(level, snapshot);
        MeshGroup previous = replacement.empty() ? groups.remove(key) : groups.put(key, replacement);
        if (previous != null) {
            previous.close();
        }
        return replacement;
    }

    /** Removes one unloaded chunk mesh. */
    public void remove(long chunkKey) {
        MeshGroup previous = groups.remove(chunkKey);
        if (previous != null) {
            previous.close();
        }
    }

    /** Returns visible groups sorted far-to-near with a deterministic key tie-breaker. */
    public List<MeshGroup> visibleGroups(
            net.minecraft.client.renderer.culling.Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        List<MeshGroup> visible = new ArrayList<>();
        for (MeshGroup group : groups.values()) {
            if (frustum.isVisible(group.bounds())) {
                visible.add(group);
            }
        }
        visible.sort(Comparator
                .comparingDouble((MeshGroup group) -> -group.distanceSquared(cameraX, cameraY, cameraZ))
                .thenComparingLong(MeshGroup::chunkKey));
        return visible;
    }

    public int size() {
        return groups.size();
    }

    /** Returns whether a published GPU group can remain visible during an atomic rebuild. */
    public boolean hasGroup(long chunkKey) {
        return groups.containsKey(chunkKey);
    }

    /** Returns whether the uploaded mesh physically verified and emitted one column. */
    public boolean ownsSurface(long chunkKey, int localX, int localZ) {
        MeshGroup group = groups.get(chunkKey);
        return group != null && group.ownsSurface(localX, localZ);
    }

    /** Releases all GPU objects during dimension/resource teardown. */
    public void clear() {
        for (MeshGroup group : groups.values()) {
            group.close();
        }
        groups.clear();
    }

    private MeshGroup build(ClientLevel level, ClientWaterChunkSnapshot snapshot) {
        int chunkMinX = snapshot.chunkX() << 4;
        int chunkMinZ = snapshot.chunkZ() << 4;
        Map<Long, Boolean> liveSurfaceEligibility = new HashMap<>(324);
        long[] ownedSurfaceMask = new long[4];
        int wetColumns = 0;
        int firstSurfaceY = 0;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                ClientWaterChunkSnapshot.Column column = snapshot.column(localX, localZ);
                if (column.wet() && usesCustomSurface(
                        level,
                        chunkMinX + localX,
                        chunkMinZ + localZ,
                        column,
                        liveSurfaceEligibility
                )) {
                    int columnIndex = localX | (localZ << 4);
                    ownedSurfaceMask[columnIndex >>> 6] |= 1L << (columnIndex & 63);
                    if (wetColumns++ == 0) {
                        firstSurfaceY = column.surfaceBlockY();
                    }
                }
            }
        }
        if (wetColumns == 0) {
            return MeshGroup.EMPTY;
        }

        TextureAtlasSprite sprite = FluidSpriteCache.getFluidSprites(
                level,
                new BlockPos(chunkMinX + 8, firstSurfaceY, chunkMinZ + 8),
                WildernessFluidRegistry.WILDERNESS_WATER.get().defaultFluidState()
        )[0];

        int subdivisions = surfaceSubdivisions();
        int quadsPerColumn = subdivisions * subdivisions;
        int vertexCount = wetColumns * quadsPerColumn * 4;
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(256, vertexCount * BYTES_PER_VERTEX))) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    ClientWaterChunkSnapshot.Column column = snapshot.column(localX, localZ);
                    if (!column.wet() || !usesCustomSurface(
                            level,
                            chunkMinX + localX,
                            chunkMinZ + localZ,
                            column,
                            liveSurfaceEligibility
                    )) {
                        continue;
                    }
                    minimumY = Math.min(minimumY, column.floorY());
                    maximumY = Math.max(maximumY, column.surfaceBlockY());
                    emitColumn(level, snapshot, builder, sprite, chunkMinX + localX,
                            chunkMinZ + localZ, column, subdivisions, chunkMinX, chunkMinZ,
                            liveSurfaceEligibility);
                }
            }
            MeshData mesh = builder.buildOrThrow();
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
            AABB bounds = new AABB(
                    chunkMinX,
                    minimumY - 2.0,
                    chunkMinZ,
                    chunkMinX + 16.0,
                    maximumY + 3.0,
                    chunkMinZ + 16.0
            );
            return new MeshGroup(
                    chunkKey(snapshot.chunkX(), snapshot.chunkZ()),
                    snapshot.generatedRevision(),
                    snapshot.sparseRevision(),
                    chunkMinX,
                    chunkMinZ,
                    buffer,
                    bounds,
                    vertexCount,
                    wetColumns * quadsPerColumn * 2,
                    ownedSurfaceMask[0],
                    ownedSurfaceMask[1],
                    ownedSurfaceMask[2],
                    ownedSurfaceMask[3]
            );
        }
    }

    private static void emitColumn(
            ClientLevel level,
            ClientWaterChunkSnapshot snapshot,
            BufferBuilder builder,
            TextureAtlasSprite sprite,
            int worldX,
            int worldZ,
            ClientWaterChunkSnapshot.Column column,
            int subdivisions,
            int chunkMinX,
            int chunkMinZ,
            Map<Long, Boolean> liveSurfaceEligibility
    ) {
        VertexSample northWest = sampleVertex(
                level, worldX, worldZ, column, liveSurfaceEligibility);
        VertexSample southWest = sampleVertex(
                level, worldX, worldZ + 1, column, liveSurfaceEligibility);
        VertexSample southEast = sampleVertex(
                level, worldX + 1, worldZ + 1, column, liveSurfaceEligibility);
        VertexSample northEast = sampleVertex(
                level, worldX + 1, worldZ, column, liveSurfaceEligibility);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float step = 1.0f / subdivisions;
        for (int subZ = 0; subZ < subdivisions; subZ++) {
            float z0 = subZ * step;
            float z1 = (subZ + 1) * step;
            for (int subX = 0; subX < subdivisions; subX++) {
                float x0 = subX * step;
                float x1 = (subX + 1) * step;
                VertexSample subNorthWest = interpolate(
                        northWest, southWest, southEast, northEast, x0, z0);
                VertexSample subSouthWest = interpolate(
                        northWest, southWest, southEast, northEast, x0, z1);
                VertexSample subSouthEast = interpolate(
                        northWest, southWest, southEast, northEast, x1, z1);
                VertexSample subNorthEast = interpolate(
                        northWest, southWest, southEast, northEast, x1, z0);
                float subU0 = mix(u0, u1, x0);
                float subU1 = mix(u0, u1, x1);
                float subV0 = mix(v0, v1, z0);
                float subV1 = mix(v0, v1, z1);
                // Keep GPU positions chunk-local so half-block subdivisions remain
                // precise millions of blocks from spawn. The render coordinator
                // supplies the exact integer chunk origin for projection and phase.
                emit(builder, localCoordinate(worldX, chunkMinX, x0), subNorthWest,
                        localCoordinate(worldZ, chunkMinZ, z0), subU0, subV0);
                emit(builder, localCoordinate(worldX, chunkMinX, x0), subSouthWest,
                        localCoordinate(worldZ, chunkMinZ, z1), subU0, subV1);
                emit(builder, localCoordinate(worldX, chunkMinX, x1), subSouthEast,
                        localCoordinate(worldZ, chunkMinZ, z1), subU1, subV1);
                emit(builder, localCoordinate(worldX, chunkMinX, x1), subNorthEast,
                        localCoordinate(worldZ, chunkMinZ, z0), subU1, subV0);
            }
        }
    }

    // Every surface vertex samples the four touching columns. Adjacent chunks
    // therefore calculate identical base height and body weights at a shared edge.
    private static VertexSample sampleVertex(
            ClientLevel level,
            int worldVertexX,
            int worldVertexZ,
            ClientWaterChunkSnapshot.Column fallback,
            Map<Long, Boolean> liveSurfaceEligibility
    ) {
        float height = 0.0f;
        float ocean = 0.0f;
        float river = 0.0f;
        float lake = 0.0f;
        float velocityX = 0.0f;
        float velocityZ = 0.0f;
        float depth = 0.0f;
        float tintRed = 0.0f;
        float tintGreen = 0.0f;
        float tintBlue = 0.0f;
        int count = 0;
        for (int offsetZ = -1; offsetZ <= 0; offsetZ++) {
            for (int offsetX = -1; offsetX <= 0; offsetX++) {
                int columnX = worldVertexX + offsetX;
                int columnZ = worldVertexZ + offsetZ;
                ClientWaterChunkSnapshot neighbor = ClientWaterSnapshotStore.getAtBlock(level, columnX, columnZ);
                if (neighbor == null) {
                    continue;
                }
                ClientWaterChunkSnapshot.Column column = neighbor.column(columnX & 15, columnZ & 15);
                if (!column.wet() || !usesCustomSurface(
                        level,
                        columnX,
                        columnZ,
                        column,
                        liveSurfaceEligibility
                )) {
                    continue;
                }
                height += column.baseSurfaceY();
                ocean += column.oceanWeight() / 255.0f;
                river += column.riverWeight() / 255.0f;
                lake += column.lakeWeight() / 255.0f;
                velocityX += column.velocityX();
                velocityZ += column.velocityZ();
                depth += column.depth();
                tintRed += ((column.waterTint() >>> 16) & 0xFF) / 255.0f;
                tintGreen += ((column.waterTint() >>> 8) & 0xFF) / 255.0f;
                tintBlue += (column.waterTint() & 0xFF) / 255.0f;
                count++;
            }
        }
        if (count == 0) {
            height = fallback.baseSurfaceY();
            ocean = fallback.oceanWeight() / 255.0f;
            river = fallback.riverWeight() / 255.0f;
            lake = fallback.lakeWeight() / 255.0f;
            velocityX = fallback.velocityX();
            velocityZ = fallback.velocityZ();
            depth = fallback.depth();
            tintRed = ((fallback.waterTint() >>> 16) & 0xFF) / 255.0f;
            tintGreen = ((fallback.waterTint() >>> 8) & 0xFF) / 255.0f;
            tintBlue = (fallback.waterTint() & 0xFF) / 255.0f;
            count = 1;
        }
        float inverse = 1.0f / count;
        ocean *= inverse;
        river *= inverse;
        lake *= inverse;
        float total = Math.max(0.001f, ocean + river + lake);
        ocean /= total;
        river /= total;
        lake /= total;
        float averagedDepth = depth * inverse;
        // The separate server shallow-water solver is not synchronized to this
        // client mesh. This deterministic approximation uses only immutable
        // snapshot topology and depth, so it cannot invent mutable authority
        // state or trigger per-frame world scans.
        float topologyShore = 1.0f - Math.min(1.0f, count * 0.25f);
        float shallowShore = 1.0f - smoothStep(0.35f, 4.5f, averagedDepth);
        float shoreFactor = Math.max(topologyShore, shallowShore);
        int color = opticalColor(ocean, river, lake,
                tintRed * inverse, tintGreen * inverse, tintBlue * inverse,
                averagedDepth, count < 4);
        return new VertexSample(height * inverse, ocean, river, lake,
                Math.max(0.18f, count * 0.25f), color,
                velocityX * inverse, velocityZ * inverse, shoreFactor, averagedDepth);
    }

    /** Returns whether the coordinator mesh, rather than the fluid fallback, owns this column. */
    static boolean usesCustomSurface(ClientWaterChunkSnapshot.Column column) {
        // Buried aquifers and generation-covered columns stay on the translucent
        // fluid fallback; exposed oceans, rivers, lakes, and springs use one
        // stable non-overlapping surface mesh.
        return !column.surfaceCovered()
                && column.bodyType() != GeneratedWaterChunk.BodyType.AQUIFER
                && column.oceanWeight() + column.riverWeight() + column.lakeWeight() > 0;
    }

    /** Returns whether the immutable surface still has an exposed physical water projection. */
    static boolean usesCustomSurface(
            ClientLevel level,
            int worldX,
            int worldZ,
            ClientWaterChunkSnapshot.Column column
    ) {
        if (!usesCustomSurface(column)) {
            return false;
        }
        return hasExposedPhysicalSurface(level, worldX, worldZ, column);
    }

    private static boolean usesCustomSurface(
            ClientLevel level,
            int worldX,
            int worldZ,
            ClientWaterChunkSnapshot.Column column,
            Map<Long, Boolean> liveSurfaceEligibility
    ) {
        if (!usesCustomSurface(column)) {
            return false;
        }
        long surfaceKey = BlockPos.asLong(worldX, column.surfaceBlockY(), worldZ);
        return liveSurfaceEligibility.computeIfAbsent(
                surfaceKey,
                ignored -> hasExposedPhysicalSurface(level, worldX, worldZ, column)
        );
    }

    private static boolean hasExposedPhysicalSurface(
            ClientLevel level,
            int worldX,
            int worldZ,
            ClientWaterChunkSnapshot.Column column
    ) {
        BlockPos surfacePos = new BlockPos(worldX, column.surfaceBlockY(), worldZ);
        FluidState surfaceFluid = level.getFluidState(surfacePos);
        BlockPos abovePos = surfacePos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        return isLiveSurfaceEligible(
                column,
                surfaceFluid.is(FluidTags.WATER),
                aboveState.getFluidState().is(FluidTags.WATER),
                aboveState.shouldHideAdjacentFluidFace(Direction.DOWN, surfaceFluid),
                aboveState.getCollisionShape(level, abovePos).isEmpty()
        );
    }

    // Kept independent from world access so dry excavations and covered tops
    // remain directly regression-testable without constructing a client level.
    static boolean isLiveSurfaceEligible(
            ClientWaterChunkSnapshot.Column column,
            boolean surfaceContainsTaggedWater,
            boolean aboveContainsTaggedWater,
            boolean aboveHidesFluidFace,
            boolean aboveCollisionIsEmpty
    ) {
        return usesCustomSurface(column)
                && surfaceContainsTaggedWater
                && !aboveContainsTaggedWater
                && !aboveHidesFluidFace
                && aboveCollisionIsEmpty;
    }

    private static int opticalColor(float ocean, float river, float lake,
                                    float tintRed, float tintGreen, float tintBlue,
                                    float depth, boolean frontier) {
        float shallow = Math.max(0.0f, Math.min(1.0f, depth / 18.0f));
        float red = 0.035f * river + 0.018f * ocean + 0.045f * lake;
        float green = 0.35f * river + 0.25f * ocean + 0.38f * lake;
        float blue = 0.58f * river + 0.62f * ocean + 0.52f * lake;
        red = red * 0.72f + tintRed * 0.28f;
        green = green * 0.72f + tintGreen * 0.28f;
        blue = blue * 0.72f + tintBlue * 0.28f;
        red *= 1.0f - shallow * 0.45f;
        green *= 1.0f - shallow * 0.38f;
        blue *= 1.0f - shallow * 0.20f;
        int alpha = Math.round((frontier ? 0.16f : Math.max(0.22f, Math.min(1.0f, depth / 24.0f))) * 255.0f);
        return (alpha << 24) | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
    }

    // High quality uses a half-block topology so GPU-displaced silhouettes and
    // specular gradients no longer reveal Minecraft's one-block quad grid.
    // Lower tiers retain one quad per column to bound distant-ocean cost.
    private static int surfaceSubdivisions() {
        return switch (WaterRenderingConfig.waterQuality()) {
            case LOW, MEDIUM -> 1;
            case HIGH, CINEMATIC -> 2;
        };
    }

    private static VertexSample interpolate(
            VertexSample northWest,
            VertexSample southWest,
            VertexSample southEast,
            VertexSample northEast,
            float x,
            float z
    ) {
        return new VertexSample(
                bilinear(northWest.height, southWest.height, southEast.height, northEast.height, x, z),
                bilinear(northWest.oceanWeight, southWest.oceanWeight,
                        southEast.oceanWeight, northEast.oceanWeight, x, z),
                bilinear(northWest.riverWeight, southWest.riverWeight,
                        southEast.riverWeight, northEast.riverWeight, x, z),
                bilinear(northWest.lakeWeight, southWest.lakeWeight,
                        southEast.lakeWeight, northEast.lakeWeight, x, z),
                bilinear(northWest.continuity, southWest.continuity,
                        southEast.continuity, northEast.continuity, x, z),
                bilinearColor(northWest.color, southWest.color, southEast.color, northEast.color, x, z),
                bilinear(northWest.velocityX, southWest.velocityX,
                        southEast.velocityX, northEast.velocityX, x, z),
                bilinear(northWest.velocityZ, southWest.velocityZ,
                        southEast.velocityZ, northEast.velocityZ, x, z),
                bilinear(northWest.shoreFactor, southWest.shoreFactor,
                        southEast.shoreFactor, northEast.shoreFactor, x, z),
                bilinear(northWest.depth, southWest.depth, southEast.depth, northEast.depth, x, z)
        );
    }

    private static float bilinear(float northWest, float southWest, float southEast,
                                  float northEast, float x, float z) {
        return mix(mix(northWest, northEast, x), mix(southWest, southEast, x), z);
    }

    private static int bilinearColor(int northWest, int southWest, int southEast,
                                     int northEast, float x, float z) {
        int alpha = Math.round(bilinear((northWest >>> 24) & 0xFF, (southWest >>> 24) & 0xFF,
                (southEast >>> 24) & 0xFF, (northEast >>> 24) & 0xFF, x, z));
        int red = Math.round(bilinear((northWest >>> 16) & 0xFF, (southWest >>> 16) & 0xFF,
                (southEast >>> 16) & 0xFF, (northEast >>> 16) & 0xFF, x, z));
        int green = Math.round(bilinear((northWest >>> 8) & 0xFF, (southWest >>> 8) & 0xFF,
                (southEast >>> 8) & 0xFF, (northEast >>> 8) & 0xFF, x, z));
        int blue = Math.round(bilinear(northWest & 0xFF, southWest & 0xFF,
                southEast & 0xFF, northEast & 0xFF, x, z));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    // Subtract integer world coordinates before introducing the fractional
    // subdivision. Converting the absolute coordinate to float first loses the
    // half-block detail that HIGH and CINEMATIC meshes rely on far from spawn.
    static float localCoordinate(int worldCoordinate, int chunkMinimum, float offset) {
        return (worldCoordinate - chunkMinimum) + offset;
    }

    private static void emit(
            BufferBuilder builder,
            float worldX,
            VertexSample sample,
            float worldZ,
            float u,
            float v
    ) {
        int encodedColor = WaterSurfaceVertexData.encodeColor(
                sample.color,
                sample.velocityX,
                sample.velocityZ,
                sample.shoreFactor,
                sample.depth
        );
        builder.addVertex(worldX, sample.height, worldZ)
                .setColor(encodedColor)
                .setUv(u, v)
                .setLight(FULL_BRIGHT)
                // Normal magnitude carries loaded-surface continuity; the
                // shader renormalizes the body blend before optical use.
                .setNormal(sample.oceanWeight * sample.continuity,
                        sample.riverWeight * sample.continuity,
                        sample.lakeWeight * sample.continuity);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Math.max(0.0f, Math.min(1.0f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    private record VertexSample(
            float height,
            float oceanWeight,
            float riverWeight,
            float lakeWeight,
            float continuity,
            int color,
            float velocityX,
            float velocityZ,
            float shoreFactor,
            float depth
    ) {
    }

    /** One immutable uploaded chunk group and its culling/diagnostic metadata. */
    public record MeshGroup(
            long chunkKey,
            long generatedRevision,
            long sparseRevision,
            int originX,
            int originZ,
            VertexBuffer buffer,
            AABB bounds,
            int vertices,
            int triangles,
            long surfaceMask0,
            long surfaceMask1,
            long surfaceMask2,
            long surfaceMask3
    ) implements AutoCloseable {
        private static final MeshGroup EMPTY = new MeshGroup(0L, 0L, 0L, 0, 0, null,
                new AABB(0, 0, 0, 0, 0, 0), 0, 0, 0L, 0L, 0L, 0L);

        public boolean empty() {
            return buffer == null || vertices == 0;
        }

        /** Returns whether this immutable upload contains the requested local column. */
        public boolean ownsSurface(int localX, int localZ) {
            int columnIndex = (localX & 15) | ((localZ & 15) << 4);
            long mask = switch (columnIndex >>> 6) {
                case 0 -> surfaceMask0;
                case 1 -> surfaceMask1;
                case 2 -> surfaceMask2;
                default -> surfaceMask3;
            };
            return (mask & (1L << (columnIndex & 63))) != 0L;
        }

        public double distanceSquared(double x, double y, double z) {
            double centerX = (bounds.minX + bounds.maxX) * 0.5;
            double centerY = (bounds.minY + bounds.maxY) * 0.5;
            double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
            double dx = centerX - x;
            double dy = centerY - y;
            double dz = centerZ - z;
            return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public void close() {
            if (buffer != null) {
                buffer.close();
            }
        }
    }
}
