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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns stable, world-aligned GPU meshes derived only from immutable water snapshots.
 *
 * <p>Topology changes only when one chunk-local snapshot changes. Continuous
 * tide and Gerstner displacement is applied by the shader, so oceans do not
 * rebuild every frame.</p>
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

    /** Releases all GPU objects during dimension/resource teardown. */
    public void clear() {
        for (MeshGroup group : groups.values()) {
            group.close();
        }
        groups.clear();
    }

    private MeshGroup build(ClientLevel level, ClientWaterChunkSnapshot snapshot) {
        int wetColumns = 0;
        int firstSurfaceY = 0;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                ClientWaterChunkSnapshot.Column column = snapshot.column(localX, localZ);
                if (column.wet() && usesCustomSurface(column)) {
                    if (wetColumns++ == 0) {
                        firstSurfaceY = column.surfaceBlockY();
                    }
                }
            }
        }
        if (wetColumns == 0) {
            return MeshGroup.EMPTY;
        }

        int chunkMinX = snapshot.chunkX() << 4;
        int chunkMinZ = snapshot.chunkZ() << 4;
        TextureAtlasSprite sprite = FluidSpriteCache.getFluidSprites(
                level,
                new BlockPos(chunkMinX + 8, firstSurfaceY, chunkMinZ + 8),
                WildernessFluidRegistry.WILDERNESS_WATER.get().defaultFluidState()
        )[0];

        int vertexCount = wetColumns * 4;
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(256, vertexCount * BYTES_PER_VERTEX))) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    ClientWaterChunkSnapshot.Column column = snapshot.column(localX, localZ);
                    if (!column.wet() || !usesCustomSurface(column)) {
                        continue;
                    }
                    minimumY = Math.min(minimumY, column.floorY());
                    maximumY = Math.max(maximumY, column.surfaceBlockY());
                    emitColumn(level, snapshot, builder, sprite, chunkMinX + localX, chunkMinZ + localZ, column);
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
                    buffer,
                    bounds,
                    vertexCount,
                    wetColumns * 2
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
            ClientWaterChunkSnapshot.Column column
    ) {
        VertexSample northWest = sampleVertex(level, worldX, worldZ, column);
        VertexSample southWest = sampleVertex(level, worldX, worldZ + 1, column);
        VertexSample southEast = sampleVertex(level, worldX + 1, worldZ + 1, column);
        VertexSample northEast = sampleVertex(level, worldX + 1, worldZ, column);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        emit(builder, worldX, northWest, worldZ, u0, v0);
        emit(builder, worldX, southWest, worldZ + 1, u0, v1);
        emit(builder, worldX + 1, southEast, worldZ + 1, u1, v1);
        emit(builder, worldX + 1, northEast, worldZ, u1, v0);
    }

    // Every surface vertex samples the four touching columns. Adjacent chunks
    // therefore calculate identical base height and body weights at a shared edge.
    private static VertexSample sampleVertex(
            ClientLevel level,
            int worldVertexX,
            int worldVertexZ,
            ClientWaterChunkSnapshot.Column fallback
    ) {
        float height = 0.0f;
        float ocean = 0.0f;
        float river = 0.0f;
        float lake = 0.0f;
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
                if (!column.wet() || !usesCustomSurface(column)) {
                    continue;
                }
                height += column.baseSurfaceY();
                ocean += column.oceanWeight() / 255.0f;
                river += column.riverWeight() / 255.0f;
                lake += column.lakeWeight() / 255.0f;
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
        int color = opticalColor(ocean, river, lake,
                tintRed * inverse, tintGreen * inverse, tintBlue * inverse,
                depth * inverse, count < 4);
        return new VertexSample(height * inverse, ocean, river, lake, color);
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

    private static void emit(
            BufferBuilder builder,
            int worldX,
            VertexSample sample,
            int worldZ,
            float u,
            float v
    ) {
        builder.addVertex(worldX, sample.height, worldZ)
                .setColor(sample.color)
                .setUv(u, v)
                .setLight(FULL_BRIGHT)
                .setNormal(sample.oceanWeight, sample.riverWeight, sample.lakeWeight);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    private record VertexSample(
            float height,
            float oceanWeight,
            float riverWeight,
            float lakeWeight,
            int color
    ) {
    }

    /** One immutable uploaded chunk group and its culling/diagnostic metadata. */
    public record MeshGroup(
            long chunkKey,
            long generatedRevision,
            long sparseRevision,
            VertexBuffer buffer,
            AABB bounds,
            int vertices,
            int triangles
    ) implements AutoCloseable {
        private static final MeshGroup EMPTY = new MeshGroup(0L, 0L, 0L, null,
                new AABB(0, 0, 0, 0, 0, 0), 0, 0);

        public boolean empty() {
            return buffer == null || vertices == 0;
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
