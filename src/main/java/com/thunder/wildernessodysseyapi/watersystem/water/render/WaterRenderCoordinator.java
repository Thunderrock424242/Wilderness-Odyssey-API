package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.rendering.RenderFrameContext;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;
import com.thunder.wildernessodysseyapi.rendering.client.WildernessRenderingFramework;
import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterChunkSnapshot;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final WaterSurfaceHandoff HANDOFFS = new WaterSurfaceHandoff();
    private static final ThreadLocal<WaterHandoffReceipt> COMPLETED_COMPILATION = new ThreadLocal<>();
    private static final Long2ObjectOpenHashMap<OceanSeaState.Sample> REGIONAL_SEA_CORNERS =
            new Long2ObjectOpenHashMap<>(256);
    private static boolean externalPackOwnedLastFrame;

    private WaterRenderCoordinator() {
    }

    /** Records that an optional renderer's tagged-water compatibility hook executed. */
    public static void recordExternalRendererBridgeUse() {
        WaterRenderDiagnostics.recordExternalRendererBridgeUse();
    }

    /** Coordinates all custom water rendering at Minecraft's translucent stage. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderFrameContext renderFrame = WildernessRenderingFramework.currentFrame();
        ClientLevel level = minecraft.level;
        if (level == null || !WaterRenderingConfig.replacementWaterRenderingEnabled(level)) {
            clear();
            externalPackOwnedLastFrame = false;
            WaterRenderDiagnostics.setRenderPath(WaterRenderDiagnostics.RenderPath.DISABLED);
            WaterRenderDiagnostics.setSceneCaptureAvailable(false);
            return;
        }

        // Shader packs need the tagged Wilderness fluid geometry. The stable
        // snapshot mesh depends on our vertex shader for its displacement, so
        // drawing it through stock translucent here would create the flat ring
        // seen around the GPU-wave surface and would hide pack-owned fluid tops.
        if (WaterShaders.externalShaderPackOwnsWater()) {
            WaterRenderDiagnostics.setRenderPath(
                    WaterRenderDiagnostics.RenderPath.EXTERNAL_SHADER_PACK);
            WaterRenderDiagnostics.setSceneCaptureAvailable(false);
            if (!externalPackOwnedLastFrame) {
                clear();
                event.getLevelRenderer().allChanged();
                externalPackOwnedLastFrame = true;
            }
            renderDetailSubpasses(minecraft, event);
            WaterRenderDiagnostics.publishFrame(0, 0, 0, 0, 0L, -1L);
            return;
        }
        if (externalPackOwnedLastFrame) {
            externalPackOwnedLastFrame = false;
            ClientWaterSnapshotStore.markAllDirtyMeshes(level);
            event.getLevelRenderer().allChanged();
        }
        WaterRenderDiagnostics.setRenderPath(WaterShaders.shouldUseCoreShader()
                ? WaterRenderDiagnostics.RenderPath.CORE_SHADER
                : WaterRenderDiagnostics.RenderPath.VANILLA_FALLBACK);

        long started = System.nanoTime();
        rebuildDirtyGroups(level, event);
        var camera = event.getCamera().getPosition();
        List<WaterChunkMeshCache.MeshGroup> visible = MESHES.visibleGroups(
                        event.getFrustum(), camera.x, camera.y, camera.z)
                .stream()
                .filter(group -> HANDOFFS.customVisible(group.chunkKey()))
                .toList();
        int totalGroups = MESHES.size();
        int vertices = 0;
        int triangles = 0;
        long ssrNanos = -1L;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean submergedOpticsNeedCapture = WaterShaders.shouldUseUnderwaterShader()
                && ClientWaterImmersion.sample(event.getCamera(), partialTick).isVisuallySubmerged();
        if (!visible.isEmpty() || submergedOpticsNeedCapture) {
            OceanSeaState.Sample seaState = ClientOceanSeaState.sampleAt(
                    level, camera.x, camera.z, partialTick);
            WaterShaders.updateOceanUniforms(
                    level.getGameTime(),
                    partialTick,
                    seaState,
                    WaterAnimationClock.periodicFraction(
                            level.getDayTime(), partialTick, 24_000L)
            );
        }

        if (!visible.isEmpty()) {
            WaterShaders.prepareSnapshotMeshPass();
            boolean timeSsrPass = WaterShaders.shouldUseCoreShader()
                    && WaterRenderingConfig.screenSpaceReflectionSteps() > 0;
            if (timeSsrPass) {
                WaterGpuTimer.begin(renderFrame.backend(), renderFrame.frameIndex());
            }
            renderSurfaceGroups(event, visible, level, partialTick);
            if (timeSsrPass) {
                WaterGpuTimer.end();
                ssrNanos = WaterGpuTimer.poll()
                        .map(RenderBackend.GpuTimingSample::durationNanos)
                        .orElse(-1L);
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

    /**
     * Returns whether a section compiler must omit the baked top at this cell.
     *
     * <p>Pending intent is visible before the section is dirtied, but only a
     * compiler observing the current generation may omit the top. The existing
     * fallback remains visible until an acknowledged upload promotes the custom
     * mesh.</p>
     */
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
        if (!HANDOFFS.suppressionRequested(key)) {
            return false;
        }
        ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.get(
                level, pos.getX() >> 4, pos.getZ() >> 4);
        if (snapshot == null) {
            return false;
        }
        ClientWaterChunkSnapshot.Column column = snapshot.column(pos.getX() & 15, pos.getZ() & 15);
        boolean owned = column.wet()
                && MESHES.ownsSurface(key, pos.getX() & 15, pos.getZ() & 15)
                && column.surfaceBlockY() == pos.getY();
        if (!owned) {
            return false;
        }
        int columnIndex = (pos.getX() & 15) | ((pos.getZ() & 15) << 4);
        return HANDOFFS.shouldSuppressTop(SectionPos.asLong(pos), columnIndex);
    }

    /** Starts one exact renderer-section compilation observation. */
    public static void beginSectionCompilation(long sectionKey) {
        COMPLETED_COMPILATION.remove();
        HANDOFFS.beginCompilation(sectionKey);
    }

    /** Finishes one renderer-section compilation and returns its exact receipt. */
    public static WaterHandoffReceipt finishSectionCompilation(long sectionKey) {
        return HANDOFFS.finishCompilation(sectionKey);
    }

    /** Stages a vanilla compile receipt for the immediately-created compiled section object. */
    public static void stageCompletedCompilation(WaterHandoffReceipt receipt) {
        if (receipt != null && receipt.valid()) {
            COMPLETED_COMPILATION.set(receipt);
        } else {
            COMPLETED_COMPILATION.remove();
        }
    }

    /** Takes and clears the receipt staged by the current vanilla compiler thread. */
    public static WaterHandoffReceipt takeCompletedCompilation() {
        WaterHandoffReceipt receipt = COMPLETED_COMPILATION.get();
        COMPLETED_COMPILATION.remove();
        return receipt == null ? WaterHandoffReceipt.NONE : receipt;
    }

    /** Acknowledges that the exact suppression build reached renderer-owned GPU storage. */
    public static void acknowledgeSectionUpload(WaterHandoffReceipt receipt) {
        HANDOFFS.acknowledgeUpload(receipt);
    }

    /** Releases mesh state on client level teardown. */
    @SubscribeEvent
    @SuppressWarnings("removal") // Releases the temporary OpenGL scene-copy target.
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
            externalPackOwnedLastFrame = false;
            WaterSceneCapture.release();
            WaterGpuTimer.release();
        }
    }

    private static void rebuildDirtyGroups(ClientLevel level, RenderLevelStageEvent event) {
        int budget = WaterRenderingConfig.snapshotMeshRebuildsPerFrame();
        long timeBudgetNanos = WaterRenderingConfig.snapshotMeshRebuildTimeBudgetNanos();
        long started = System.nanoTime();
        for (int rebuilt = 0; rebuilt < budget; rebuilt++) {
            // One rebuild is always allowed so the queue cannot stall. Further
            // CPU construction and GPU uploads defer once this frame's soft
            // budget is spent, preserving the existing published/fallback mesh.
            if (rebuilt > 0 && System.nanoTime() - started >= timeBudgetNanos) {
                return;
            }
            Long key = ClientWaterSnapshotStore.pollDirtyMesh();
            if (key == null) {
                return;
            }
            boolean replacingPublishedMesh = MESHES.hasGroup(key)
                    && HANDOFFS.customVisible(key);
            Set<Long> previouslyTrackedSections = HANDOFFS.trackedSections(key);
            int chunkX = (int) (long) key;
            int chunkZ = (int) ((long) key >>> 32);
            ClientWaterChunkSnapshot snapshot = ClientWaterSnapshotStore.get(level, chunkX, chunkZ);
            if (snapshot == null) {
                MESHES.remove(key);
                HANDOFFS.remove(key);
                continue;
            }
            WaterChunkMeshCache.MeshGroup group = MESHES.rebuild(level, snapshot);
            WaterRenderDiagnostics.recordMeshRebuild();
            if (group.empty()) {
                HANDOFFS.remove(key);
                // A previously owned surface may have become dry or covered;
                // request the baked fallback immediately so no hole remains.
                markSurfaceSectionsDirty(event, union(
                        previouslyTrackedSections,
                        surfaceSectionKeys(snapshot)
                ));
                continue;
            }
            Map<Long, WaterSurfaceHandoff.SectionMask> suppressionSections =
                    suppressionSections(snapshot, group);
            if (replacingPublishedMesh) {
                // An ordinary custom-to-custom update keeps the established
                // custom owner visible while refreshed fallback buffers compile.
                HANDOFFS.keepCustomVisible(key, suppressionSections);
            } else {
                // Publish intent before scheduling section work. Compiler
                // workers therefore cannot bake a pre-intent fallback result.
                HANDOFFS.beginSuppression(key, suppressionSections);
            }
            markSurfaceSectionsDirty(event, union(
                    previouslyTrackedSections,
                    suppressionSections.keySet()
            ));
        }
    }

    private static void renderSurfaceGroups(
            RenderLevelStageEvent event,
            List<WaterChunkMeshCache.MeshGroup> visible,
            ClientLevel level,
            float partialTick
    ) {
        boolean coreShader = WaterShaders.shouldUseCoreShader();
        RenderType renderType = coreShader
                ? WaterRenderTypes.dynamicOcean()
                : RenderType.translucent();
        renderType.setupRenderState();
        ShaderInstance shader = coreShader
                ? WaterShaders.getOceanShader()
                : net.minecraft.client.renderer.GameRenderer.getRendertypeTranslucentShader();
        if (shader == null) {
            renderType.clearRenderState();
            return;
        }
        var camera = event.getCamera().getPosition();
        WaterChunkMeshCache.MeshGroup firstGroup = visible.getFirst();
        Matrix4f modelView = chunkModelView(event, firstGroup, camera.x, camera.y, camera.z);
        shader.setDefaultUniforms(
                VertexFormat.Mode.QUADS,
                modelView,
                event.getProjectionMatrix(),
                Minecraft.getInstance().getWindow()
        );
        shader.apply();
        if (coreShader) {
            // Reuse primitive-key storage each frame to avoid boxed corner keys
            // and per-frame hash-table allocation in large ocean views.
            REGIONAL_SEA_CORNERS.clear();
            WaterShaders.beginRegionalOceanStatePass();
        }
        for (WaterChunkMeshCache.MeshGroup group : visible) {
            // Each VBO is chunk-local. Neighboring groups share exact corner
            // samples, so the vertex shader can vary regional sea energy in
            // world space without camera-global morphing or boundary cracks.
            if (shader.MODEL_VIEW_MATRIX != null) {
                shader.MODEL_VIEW_MATRIX.set(chunkModelView(
                        event, group, camera.x, camera.y, camera.z));
                shader.MODEL_VIEW_MATRIX.upload();
            }
            if (coreShader) {
                int minimumX = group.originX();
                int minimumZ = group.originZ();
                WaterShaders.uploadRegionalOceanState(
                        regionalSeaCorner(
                                level, minimumX, minimumZ, partialTick),
                        regionalSeaCorner(
                                level, minimumX + 16, minimumZ, partialTick),
                        regionalSeaCorner(
                                level, minimumX, minimumZ + 16, partialTick),
                        regionalSeaCorner(
                                level, minimumX + 16, minimumZ + 16, partialTick)
                );
                var chunkOrigin = shader.getUniform("ChunkOrigin");
                if (chunkOrigin != null) {
                    chunkOrigin.set((float) group.originX(), (float) group.originZ());
                    chunkOrigin.upload();
                }
            }
            group.buffer().bind();
            group.buffer().draw();
        }
        VertexBuffer.unbind();
        shader.clear();
        renderType.clearRenderState();
    }

    private static OceanSeaState.Sample regionalSeaCorner(
            ClientLevel level,
            int worldX,
            int worldZ,
            float partialTick
    ) {
        long key = ((long) worldX << 32) | (worldZ & 0xFFFF_FFFFL);
        return REGIONAL_SEA_CORNERS.computeIfAbsent(
                key,
                ignored -> ClientOceanSeaState.sampleAt(
                        level, worldX, worldZ, partialTick)
        );
    }

    private static Matrix4f chunkModelView(
            RenderLevelStageEvent event,
            WaterChunkMeshCache.MeshGroup group,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        return new Matrix4f(event.getModelViewMatrix()).translate(
                (float) (group.originX() - cameraX),
                (float) -cameraY,
                (float) (group.originZ() - cameraZ)
        );
    }

    private static void markSurfaceSectionsDirty(
            RenderLevelStageEvent event,
            Set<Long> sectionKeys
    ) {
        for (long sectionKey : sectionKeys) {
            event.getLevelRenderer().setSectionDirty(
                    SectionPos.x(sectionKey),
                    SectionPos.y(sectionKey),
                    SectionPos.z(sectionKey)
            );
        }
    }

    private static Map<Long, WaterSurfaceHandoff.SectionMask> suppressionSections(
            ClientWaterChunkSnapshot snapshot,
            WaterChunkMeshCache.MeshGroup group
    ) {
        Map<Long, long[]> mutable = new HashMap<>();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                if (!group.ownsSurface(localX, localZ)) {
                    continue;
                }
                ClientWaterChunkSnapshot.Column column = snapshot.column(localX, localZ);
                long sectionKey = SectionPos.asLong(
                        snapshot.chunkX(),
                        SectionPos.blockToSectionCoord(column.surfaceBlockY()),
                        snapshot.chunkZ()
                );
                long[] mask = mutable.computeIfAbsent(sectionKey, ignored -> new long[4]);
                int columnIndex = localX | (localZ << 4);
                mask[columnIndex >>> 6] |= 1L << (columnIndex & 63);
            }
        }
        Map<Long, WaterSurfaceHandoff.SectionMask> immutable = new HashMap<>();
        mutable.forEach((sectionKey, mask) -> immutable.put(
                sectionKey,
                new WaterSurfaceHandoff.SectionMask(mask[0], mask[1], mask[2], mask[3])
        ));
        return Map.copyOf(immutable);
    }

    private static Set<Long> surfaceSectionKeys(ClientWaterChunkSnapshot snapshot) {
        Set<Long> sections = new HashSet<>();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                ClientWaterChunkSnapshot.Column column = snapshot.column(localX, localZ);
                if (column.wet()) {
                    sections.add(SectionPos.asLong(
                            snapshot.chunkX(),
                            SectionPos.blockToSectionCoord(column.surfaceBlockY()),
                            snapshot.chunkZ()
                    ));
                }
            }
        }
        return Set.copyOf(sections);
    }

    private static Set<Long> union(Set<Long> first, Set<Long> second) {
        Set<Long> union = new HashSet<>(first);
        union.addAll(second);
        return union;
    }

    private static void clear() {
        MESHES.clear();
        HANDOFFS.clear();
        COMPLETED_COMPILATION.remove();
        REGIONAL_SEA_CORNERS.clear();
        WaterSurfaceDisplacement.clear();
        FluidRenderer.clear();
        RippleRenderer.clear();
        CoastalRunupRenderer.clear();
        WaterRenderDiagnostics.publishCoastalDebug(0);
    }

    private static void renderDetailSubpasses(
            Minecraft minecraft,
            RenderLevelStageEvent event
    ) {
        FluidRenderer.onRenderLevel(event);
        RippleRenderer.onRenderLevel(event);
        CoastalRunupRenderer.onRenderLevel(event);
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.translucent());
        if (CoastalDebugRenderer.onRenderLevel(event)) {
            minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        }
    }
}
