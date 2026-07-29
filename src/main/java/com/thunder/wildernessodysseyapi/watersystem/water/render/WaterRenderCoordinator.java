package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sole coordinator for Wilderness translucent surface geometry.
 *
 * <p>The coordinator consumes immutable snapshots, rebuilds stable chunk meshes
 * incrementally, culls and sorts uploaded groups, owns fallback handoff, and
 * invokes ripple/SPH detail emitters before one shared translucent flush.
 * Shoreline material is encoded in the main mesh instead of drawing a second
 * overlapping surface.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class WaterRenderCoordinator {

    private static final WaterChunkMeshCache MESHES = new WaterChunkMeshCache();
    private static final Map<Long, Ownership> OWNERSHIP = new ConcurrentHashMap<>();
    private static boolean externalPackOwnedLastFrame;

    private WaterRenderCoordinator() {
    }

    /** Coordinates all custom water rendering at Minecraft's translucent stage. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !WaterRenderingConfig.replacementWaterRenderingEnabled(level)) {
            clear();
            externalPackOwnedLastFrame = false;
            return;
        }

        // Shader packs need the tagged Wilderness fluid geometry. The stable
        // snapshot mesh depends on our vertex shader for its displacement, so
        // drawing it through stock translucent here would create the flat ring
        // seen around the GPU-wave surface and would hide pack-owned fluid tops.
        if (WaterShaders.externalShaderPackOwnsWater()) {
            if (!externalPackOwnedLastFrame) {
                clear();
                event.getLevelRenderer().allChanged();
                externalPackOwnedLastFrame = true;
            }
            renderDetailSubpasses(minecraft, event);
            WaterRenderDiagnostics.publishFrame(0, 0, 0, 0, 0L, 0L);
            return;
        }
        if (externalPackOwnedLastFrame) {
            externalPackOwnedLastFrame = false;
            ClientWaterSnapshotStore.markAllDirtyMeshes(level);
            event.getLevelRenderer().allChanged();
        }

        long started = System.nanoTime();
        rebuildDirtyGroups(level, event);
        var camera = event.getCamera().getPosition();
        List<WaterChunkMeshCache.MeshGroup> visible = MESHES.visibleGroups(
                event.getFrustum(), camera.x, camera.y, camera.z);
        int totalGroups = MESHES.size();
        int vertices = 0;
        int triangles = 0;
        long ssrNanos = 0L;

        if (!visible.isEmpty()) {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float timeSeconds = (level.getGameTime() + partialTick) / 20.0f;
            OceanSeaState.Sample seaState = ClientOceanSeaState.current(level);
            WaterShaders.updateOceanUniforms(
                    timeSeconds,
                    seaState,
                    ((level.getDayTime() + partialTick) % 24_000L) / 24_000.0f
            );
            WaterShaders.prepareSnapshotMeshPass();
            boolean timeSsrPass = WaterShaders.shouldUseCoreShader()
                    && WaterRenderingConfig.screenSpaceReflectionSteps() > 0;
            if (timeSsrPass) {
                WaterGpuTimer.begin();
            }
            renderSurfaceGroups(event, visible);
            if (timeSsrPass) {
                WaterGpuTimer.end();
                ssrNanos = WaterGpuTimer.latestNanos();
            }
            for (WaterChunkMeshCache.MeshGroup group : visible) {
                vertices += group.vertices();
                triangles += group.triangles();
            }
        }

        // Local SPH and ripple effects emit into the same stock translucent
        // buffer and are flushed once after both coordinated detail subpasses.
        renderDetailSubpasses(minecraft, event);

        WaterRenderDiagnostics.setSnapshotBytes(ClientWaterSnapshotStore.estimatedBytes(level));
        WaterRenderDiagnostics.setGeneratedMetadataBytes(
                ClientWaterSnapshotStore.generatedEstimatedBytes(level));
        WaterRenderDiagnostics.publishFrame(
                visible.size(),
                Math.max(0, totalGroups - visible.size()),
                vertices,
                triangles,
                System.nanoTime() - started,
                ssrNanos
        );
    }

    /** Returns true only after a replacement mesh is uploaded and published. */
    public static boolean ownsBakedTop(BlockPos pos) {
        if (WaterShaders.externalShaderPackOwnsWater()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return false;
        }
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (OWNERSHIP.get(key) != Ownership.CUSTOM_OWNED) {
            return false;
        }
        ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.get(
                level, pos.getX() >> 4, pos.getZ() >> 4);
        if (snapshot == null) {
            return false;
        }
        ClientWaterChunkSnapshot.Column column = snapshot.column(pos.getX() & 15, pos.getZ() & 15);
        return column.wet()
                && WaterChunkMeshCache.usesCustomSurface(column)
                && column.surfaceBlockY() == pos.getY();
    }

    /** Releases mesh state on client level teardown. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
            externalPackOwnedLastFrame = false;
            WaterSceneCapture.release();
            WaterGpuTimer.release();
        }
    }

    private static void rebuildDirtyGroups(ClientLevel level, RenderLevelStageEvent event) {
        int baseBudget = switch (WaterRenderingConfig.waterQuality()) {
            case LOW -> 4;
            case MEDIUM -> 8;
            case HIGH -> 16;
            case CINEMATIC -> 24;
        };
        // Chunk streaming can publish generated and sparse snapshots together.
        // A bounded burst drains that unique-key backlog before it becomes a
        // visible flat fallback ring, while normal incremental updates retain
        // the smaller per-quality rebuild budget.
        int pending = ClientWaterSnapshotStore.pendingDirtyMeshCount();
        int budget = pending > baseBudget * 3
                ? Math.min(48, baseBudget * 2)
                : baseBudget;
        for (int rebuilt = 0; rebuilt < budget; rebuilt++) {
            Long key = ClientWaterSnapshotStore.pollDirtyMesh();
            if (key == null) {
                return;
            }
            boolean replacingPublishedMesh = MESHES.hasGroup(key)
                    && OWNERSHIP.get(key) == Ownership.CUSTOM_OWNED;
            if (!replacingPublishedMesh) {
                OWNERSHIP.put(key, Ownership.FALLBACK);
            }
            int chunkX = (int) (long) key;
            int chunkZ = (int) ((long) key >>> 32);
            ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.get(level, chunkX, chunkZ);
            if (snapshot == null) {
                MESHES.remove(key);
                OWNERSHIP.remove(key);
                continue;
            }
            WaterChunkMeshCache.MeshGroup group = MESHES.rebuild(level, snapshot);
            WaterRenderDiagnostics.recordMeshRebuild();
            if (group.empty()) {
                OWNERSHIP.remove(key);
                // A previously owned surface may have become dry or covered;
                // request the baked fallback immediately so no hole remains.
                markSurfaceSectionsDirty(event, snapshot);
                continue;
            }
            markSurfaceSectionsDirty(event, snapshot);
            // Public chunk compilation hooks cannot acknowledge exactly when
            // every fallback top has disappeared. Publishing custom ownership
            // after upload guarantees no missing surface; the requested section
            // rebuild limits the handoff to a bounded overlap window.
            OWNERSHIP.put(key, Ownership.CUSTOM_OWNED);
        }
    }

    private static void renderSurfaceGroups(
            RenderLevelStageEvent event,
            List<WaterChunkMeshCache.MeshGroup> visible
    ) {
        RenderType renderType = WaterShaders.shouldUseCoreShader()
                ? WaterRenderTypes.dynamicOcean()
                : RenderType.translucent();
        renderType.setupRenderState();
        ShaderInstance shader = WaterShaders.shouldUseCoreShader()
                ? WaterShaders.getOceanShader()
                : net.minecraft.client.renderer.GameRenderer.getRendertypeTranslucentShader();
        if (shader == null) {
            renderType.clearRenderState();
            return;
        }
        var camera = event.getCamera().getPosition();
        Matrix4f modelView = new Matrix4f(event.getModelViewMatrix())
                .translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
        shader.setDefaultUniforms(
                VertexFormat.Mode.QUADS,
                modelView,
                event.getProjectionMatrix(),
                Minecraft.getInstance().getWindow()
        );
        shader.apply();
        for (WaterChunkMeshCache.MeshGroup group : visible) {
            group.buffer().bind();
            group.buffer().draw();
        }
        VertexBuffer.unbind();
        shader.clear();
        renderType.clearRenderState();
    }

    private static void markSurfaceSectionsDirty(
            RenderLevelStageEvent event,
            ClientWaterChunkSnapshot snapshot
    ) {
        boolean[] sections = new boolean[64];
        int minimumSection = -32;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                ClientWaterChunkSnapshot.Column column = snapshot.column(localX, localZ);
                if (!column.wet()) {
                    continue;
                }
                int sectionY = SectionPos.blockToSectionCoord(column.surfaceBlockY());
                int index = sectionY - minimumSection;
                if (index >= 0 && index < sections.length && !sections[index]) {
                    sections[index] = true;
                    event.getLevelRenderer().setSectionDirty(snapshot.chunkX(), sectionY, snapshot.chunkZ());
                }
            }
        }
    }

    private static void clear() {
        MESHES.clear();
        OWNERSHIP.clear();
        WaterSurfaceDisplacement.clear();
        RippleRenderer.clear();
    }

    private static void renderDetailSubpasses(
            Minecraft minecraft,
            RenderLevelStageEvent event
    ) {
        FluidRenderer.onRenderLevel(event);
        RippleRenderer.onRenderLevel(event);
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.translucent());
    }

    private enum Ownership {
        FALLBACK,
        CUSTOM_OWNED
    }
}
