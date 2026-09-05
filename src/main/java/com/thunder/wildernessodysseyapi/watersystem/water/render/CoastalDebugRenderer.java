package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.debugoverlay.client.WildernessDebugManager;
import com.thunder.wildernessodysseyapi.debugoverlay.config.DebugOverlayConfig;
import com.thunder.wildernessodysseyapi.debugoverlay.provider.RenderingDebugDataProvider;
import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSegment;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveModel;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveBreakEvent;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Draws bounded in-world coastline tuning geometry while the Rendering F3 page is selected.
 *
 * <p>The renderer consumes the same immutable segment cache as visible surf and
 * is invoked by {@link WaterRenderCoordinator}; it does not register a second
 * level-render event or scan terrain. Colors are documented on the debug page.</p>
 */
public final class CoastalDebugRenderer {

    private static final int BOX_BUDGET = 720;
    private static final int DETAILED_SEGMENT_LIMIT = 4;
    private static final float VISUAL_TIDE_SCALE = 0.18f;

    private CoastalDebugRenderer() {
    }

    /** Appends line geometry and returns whether the coordinator must flush the line batch. */
    public static boolean onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || level == null
                || !shouldRender(minecraft)) {
            WaterRenderDiagnostics.publishCoastalDebug(0);
            return false;
        }
        List<CoastalSegment> segments = ClientCoastalSegmentStore.segments(level);
        if (segments.isEmpty()) {
            WaterRenderDiagnostics.publishCoastalDebug(0);
            return false;
        }

        var camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        VertexConsumer lines = minecraft.renderBuffers().bufferSource()
                .getBuffer(RenderType.lines());
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float tide = TideSystem.getTideOffset(level) * VISUAL_TIDE_SCALE;
        int boxes = 0;

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (int segmentIndex = 0;
             segmentIndex < segments.size() && boxes < BOX_BUDGET;
             segmentIndex++) {
            CoastalSegment segment = segments.get(segmentIndex);
            OceanSeaState.Sample sea = WaterRenderingConfig.coastalWeatherInfluenceEnabled()
                    ? ClientOceanSeaState.sampleAt(
                    level, segment.centerX(), segment.centerZ(), partialTick)
                    : OceanSeaState.CALM;
            float onshoreWind = sea.windDirectionX() * segment.landwardNormalX()
                    + sea.windDirectionZ() * segment.landwardNormalZ();
            CoastalWaveModel.Sample wave = CoastalWaveModel.sample(
                    segment.id(), level.getGameTime(), partialTick, segment.profile(), sea,
                    segment.averageBeachSlope(), segment.underwaterSlope(),
                    segment.averageWaterDepth(), onshoreWind);
            wave = CoastalWaveModel.withTide(wave, TideSystem.getTideOffset(level),
                    TideSystem.getTideRate(level), segment.averageBeachSlope());
            float energyRed = 0.25f + wave.energy() * 0.75f;
            float energyGreen = 0.90f - wave.energy() * 0.58f;

            // Segment center and landward wave direction encode live energy by color.
            boxes += box(poseStack, lines,
                    segment.centerX() + 0.30, segment.surfaceY() + tide + 0.08,
                    segment.centerZ() + 0.30,
                    segment.centerX() + 0.70, segment.surfaceY() + tide + 0.48,
                    segment.centerZ() + 0.70,
                    energyRed, energyGreen, 0.12f, 0.95f);
            boxes += vectorBoxes(
                    poseStack, lines, segment, tide,
                    energyRed, energyGreen, 0.12f, BOX_BUDGET - boxes);

            boolean detailed = segmentIndex < DETAILED_SEGMENT_LIMIT;
            for (CoastalSegment.ShorelinePoint point : segment.shoreline()) {
                if (boxes >= BOX_BUDGET) {
                    break;
                }
                float shorelineY = point.waterSurfaceY() + tide;
                // Cyan: discovered coastline samples.
                boxes += box(poseStack, lines,
                        point.waterX() + 0.12, shorelineY - 0.08, point.waterZ() + 0.12,
                        point.waterX() + 0.88, shorelineY + 0.12, point.waterZ() + 0.88,
                        0.10f, 0.92f, 1.0f, 0.90f);

                if (!detailed || boxes >= BOX_BUDGET) {
                    continue;
                }
                CoastalSegment.NearshoreCell breaker = closestNearshoreCell(
                        point, wave.breakerDistanceBlocks());
                // Orange: breaker zone. Blue vertical column: sampled water depth.
                boxes += box(poseStack, lines,
                        breaker.blockX() + 0.06, breaker.waterSurfaceY() + tide - 0.08,
                        breaker.blockZ() + 0.06,
                        breaker.blockX() + 0.94,
                        breaker.waterSurfaceY() + tide + Math.max(0.14, wave.breakerLift()),
                        breaker.blockZ() + 0.94,
                        1.0f, 0.48f, 0.08f, 0.95f);
                boxes += box(poseStack, lines,
                        breaker.blockX() + 0.43,
                        breaker.waterSurfaceY() - breaker.depthBlocks(),
                        breaker.blockZ() + 0.43,
                        breaker.blockX() + 0.57,
                        breaker.waterSurfaceY() + tide,
                        breaker.blockZ() + 0.57,
                        0.14f, 0.36f, 1.0f, 0.78f);

                CoastalSegment.RunUpCell limit = closestRunUpCell(
                        point, wave.maximumRunUpDistanceBlocks());
                if (limit != null) {
                    // Magenta: terrain-aware run-up limit.
                    boxes += terrainCell(poseStack, lines, limit,
                            1.0f, 0.16f, 0.86f, 0.92f);
                }
                for (CoastalSegment.RunUpCell cell : point.runUpCells()) {
                    if (boxes >= BOX_BUDGET
                            || cell.distanceFromWaterBlocks()
                            > wave.maximumRunUpDistanceBlocks() + 0.01f) {
                        continue;
                    }
                    boolean covered = cell.distanceFromWaterBlocks()
                            <= wave.runUpDistanceBlocks() + 0.01f;
                    // Green center markers always preserve the cached terrain/slope trace.
                    double slopeY = cell.topBlockY() + 1.02;
                    boxes += box(poseStack, lines,
                            cell.blockX() + 0.43, slopeY, cell.blockZ() + 0.43,
                            cell.blockX() + 0.57, slopeY + 0.16, cell.blockZ() + 0.57,
                            0.25f, 0.92f, 0.34f, 0.72f);
                    if (boxes >= BOX_BUDGET) {
                        break;
                    }
                    if (covered && wave.foam() > 0.05f) {
                        // White: live water/foam footprint.
                        boxes += terrainCell(poseStack, lines, cell,
                                0.96f, 0.99f, 1.0f, 0.82f);
                    } else if (wave.wetness() > 0.05f) {
                        // Dark blue: recently washed wetness footprint.
                        boxes += terrainCell(poseStack, lines, cell,
                                0.12f, 0.34f, 0.62f, 0.76f);
                    }
                }
            }
        }
        CoastalWaveBreakEvent lastBreak = CoastalBreakEffects.activeBreak(level, 40L).orElse(null);
        if (lastBreak != null && boxes < BOX_BUDGET) {
            // Red: last actual wave-break event selected for local effects.
            boxes += box(poseStack, lines,
                    lastBreak.x() - 0.28, lastBreak.y() - 0.28, lastBreak.z() - 0.28,
                    lastBreak.x() + 0.28, lastBreak.y() + 0.28, lastBreak.z() + 0.28,
                    1.0f, 0.08f, 0.08f, 1.0f);
        }
        poseStack.popPose();
        WaterRenderDiagnostics.publishCoastalDebug(boxes);
        return boxes > 0;
    }

    private static boolean shouldRender(Minecraft minecraft) {
        return DebugOverlayConfig.ENABLE_CUSTOM_DEBUG_HUD.get()
                && minecraft.getDebugOverlay().showDebugScreen()
                && RenderingDebugDataProvider.PAGE_ID.equals(
                WildernessDebugManager.get().selectedPageId());
    }

    private static int vectorBoxes(
            PoseStack poseStack,
            VertexConsumer lines,
            CoastalSegment segment,
            float tide,
            float red,
            float green,
            float blue,
            int remaining
    ) {
        int count = 0;
        for (int step = 1; step <= 4 && count < remaining; step++) {
            double x = segment.centerX() + 0.5
                    + segment.landwardNormalX() * step * 0.72;
            double z = segment.centerZ() + 0.5
                    + segment.landwardNormalZ() * step * 0.72;
            double size = step == 4 ? 0.16 : 0.09;
            count += box(poseStack, lines,
                    x - size, segment.surfaceY() + tide + 0.22 - size, z - size,
                    x + size, segment.surfaceY() + tide + 0.22 + size, z + size,
                    red, green, blue, 0.90f);
        }
        return count;
    }

    private static int terrainCell(
            PoseStack poseStack,
            VertexConsumer lines,
            CoastalSegment.RunUpCell cell,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        double y = cell.topBlockY() + 1.025;
        return box(poseStack, lines,
                cell.blockX() + 0.08, y, cell.blockZ() + 0.08,
                cell.blockX() + 0.92, y + 0.08, cell.blockZ() + 0.92,
                red, green, blue, alpha);
    }

    private static CoastalSegment.NearshoreCell closestNearshoreCell(
            CoastalSegment.ShorelinePoint point,
            float targetDistance
    ) {
        if (point.nearshoreCells().isEmpty()) {
            return new CoastalSegment.NearshoreCell(
                    point.waterX(), point.waterSurfaceY(), point.waterZ(), 0.0f, 0.0f);
        }
        CoastalSegment.NearshoreCell closest = point.nearshoreCells().getFirst();
        float difference = Math.abs(closest.distanceFromShoreBlocks() - targetDistance);
        for (int index = 1; index < point.nearshoreCells().size(); index++) {
            CoastalSegment.NearshoreCell candidate = point.nearshoreCells().get(index);
            float candidateDifference = Math.abs(
                    candidate.distanceFromShoreBlocks() - targetDistance);
            if (candidateDifference < difference) {
                closest = candidate;
                difference = candidateDifference;
            }
        }
        return closest;
    }

    private static CoastalSegment.RunUpCell closestRunUpCell(
            CoastalSegment.ShorelinePoint point,
            float targetDistance
    ) {
        CoastalSegment.RunUpCell closest = null;
        float difference = Float.POSITIVE_INFINITY;
        for (CoastalSegment.RunUpCell candidate : point.runUpCells()) {
            float candidateDifference = Math.abs(
                    candidate.distanceFromWaterBlocks() - targetDistance);
            if (candidateDifference < difference) {
                closest = candidate;
                difference = candidateDifference;
            }
        }
        return closest;
    }

    private static int box(
            PoseStack poseStack,
            VertexConsumer lines,
            double minimumX,
            double minimumY,
            double minimumZ,
            double maximumX,
            double maximumY,
            double maximumZ,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        LevelRenderer.renderLineBox(
                poseStack,
                lines,
                new AABB(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ),
                red,
                green,
                blue,
                alpha
        );
        return 1;
    }
}
