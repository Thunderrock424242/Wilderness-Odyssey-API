package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.environment.glacial.client.GlacialWaterTintManager;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuDiagnostics;
import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSegment;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSeasonModel;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveModel;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalFoamModel;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWaterSnapshotStore;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

import java.util.List;

/**
 * Emits the cached coastal breaker, run-up, retreat, foam, and wetness pass.
 *
 * <p>This is invoked only by {@link WaterRenderCoordinator}. It appends to the
 * coordinator's shared stock translucent batch and never registers another
 * render event, flushes a buffer, owns ocean chunks, or changes block state.</p>
 */
public final class CoastalRunupRenderer {

    private static final FluidState WATER_STATE = Fluids.WATER.defaultFluidState();
    private static final float VISUAL_TIDE_SCALE = 0.18f;
    private static final float SURFACE_EPSILON = 0.018f;
    private static final float TEXTURE_SCALE = 0.40f;

    private CoastalRunupRenderer() {
    }

    /** Appends quality-capped coastal detail geometry at the coordinated stage. */
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        try (GpuDiagnostics.Scope ignored = GpuDiagnostics.scope("water.coastal")) {
            renderScoped(event);
        }
    }

    /** Clears the published coastal frame counters on client teardown. */
    public static void clear() {
        WaterRenderDiagnostics.publishCoastalFrame(0, 0, 0, 0);
    }

    private static void renderScoped(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !WaterRenderingConfig.coastalWavesEnabled(level)) {
            clear();
            return;
        }
        List<CoastalSegment> segments = ClientCoastalSegmentStore.segments(level);
        int quadBudget = WaterRenderingConfig.coastalQuadBudget();
        if (segments.isEmpty() || quadBudget <= 0) {
            WaterRenderDiagnostics.publishCoastalFrame(
                    segments.size(), ClientCoastalSegmentStore.lastCandidateCount(), 0, 0);
            return;
        }

        var camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        TextureAtlasSprite waterSprite = FluidSpriteCache.getFluidSprites(
                level, BlockPos.containing(camera), WATER_STATE)[0];
        VertexConsumer buffer = minecraft.renderBuffers().bufferSource()
                .getBuffer(RenderType.translucent());
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float tideOffset = TideSystem.getTideOffset(level) * VISUAL_TIDE_SCALE;
        int runUpDetailDistance = WaterRenderingConfig.coastalRunUpDetailDistanceBlocks();
        double runUpDetailDistanceSquared = runUpDetailDistance * (double) runUpDetailDistance;
        int renderedSegments = 0;
        int quads = 0;

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (CoastalSegment segment : segments) {
            if (quads >= quadBudget) {
                break;
            }
            OceanSeaState.Sample seaState = WaterRenderingConfig.coastalWeatherInfluenceEnabled()
                    ? ClientOceanSeaState.sampleAt(
                    level, segment.centerX(), segment.centerZ(), partialTick)
                    : OceanSeaState.CALM;
            float onshoreWind = seaState.windDirectionX() * segment.landwardNormalX()
                    + seaState.windDirectionZ() * segment.landwardNormalZ();
            CoastalWaveModel.Sample wave = CoastalWaveModel.sample(
                    segment.id(),
                    level.getGameTime(),
                    partialTick,
                    segment.profile(),
                    seaState,
                    segment.averageBeachSlope(),
                    segment.underwaterSlope(),
                    segment.averageWaterDepth(),
                    onshoreWind
            );
            CoastalSeasonModel.Sample season = ClientCoastalClimate.sample(level, segment);
            wave = CoastalWaveModel.withTide(wave, TideSystem.getTideOffset(level),
                    TideSystem.getTideRate(level), segment.averageBeachSlope());
            double deltaX = segment.centerX() + 0.5 - camera.x;
            double deltaZ = segment.centerZ() + 0.5 - camera.z;
            boolean detailedRunUp = deltaX * deltaX + deltaZ * deltaZ
                    <= runUpDetailDistanceSquared;
            int before = quads;
            for (CoastalSegment.ShorelinePoint point : segment.shoreline()) {
                if (quads >= quadBudget) {
                    break;
                }
                quads += drawPoint(
                        level, poseStack.last(), buffer, waterSprite,
                        segment, point, wave, season, tideOffset,
                        detailedRunUp, partialTick, quadBudget - quads);
            }
            if (quads > before) {
                renderedSegments++;
            }
        }
        poseStack.popPose();

        WaterRenderDiagnostics.publishCoastalFrame(
                segments.size(),
                ClientCoastalSegmentStore.lastCandidateCount(),
                renderedSegments,
                quads
        );
    }

    private static int drawPoint(
            ClientLevel level,
            PoseStack.Pose pose,
            VertexConsumer buffer,
            TextureAtlasSprite waterSprite,
            CoastalSegment segment,
            CoastalSegment.ShorelinePoint point,
            CoastalWaveModel.Sample wave,
            CoastalSeasonModel.Sample season,
            float tideOffset,
            boolean detailedRunUp,
            float partialTick,
            int remainingBudget
    ) {
        int quads = 0;
        boolean foamEnabled = WaterRenderingConfig.coastalFoamEnabled(level);
        if (remainingBudget >= CoastalBreakerGeometry.QUADS_PER_CREST
                && (wave.stage() == CoastalWaveModel.Stage.INCOMING
                || wave.stage() == CoastalWaveModel.Stage.SHOALING
                || wave.stage() == CoastalWaveModel.Stage.BREAKING)
                && !point.nearshoreCells().isEmpty()) {
            CoastalBreakerGeometry.Shape shape = CoastalBreakerGeometry.sample(point, wave);
            float centerX = point.waterX() + 0.5f
                    - segment.landwardNormalX() * shape.distanceFromShore();
            float centerZ = point.waterZ() + 0.5f
                    - segment.landwardNormalZ() * shape.distanceFromShore();
            int light = waterLight(level, centerX, shape.surfaceY(), centerZ);
            int tint = waterTint(level, (int) Math.floor(centerX),
                    (int) Math.floor(shape.surfaceY()), (int) Math.floor(centerZ));
            float foam = foamEnabled ? wave.foam()
                    * WaterRenderingConfig.coastalFoamStrength()
                    * season.foamMultiplier() : 0.0f;
            int waterColor = breakerColor(tint, foam * 0.30f, season);
            int lipColor = foam > 0.04f ? foamColor(foam) : waterColor;
            drawBreakerBand(
                    buffer, pose, waterSprite, light, waterColor, lipColor,
                    centerX, centerZ, shape, segment, tideOffset
            );
            quads += CoastalBreakerGeometry.QUADS_PER_CREST;
        }

        // Reuse the wave lifecycle for a bounded whitewater trail. No new
        // particle simulation, render pass or synchronized foam field is needed.
        if (foamEnabled && detailedRunUp) {
            int patches = 0;
            for (CoastalSegment.NearshoreCell cell : point.nearshoreCells()) {
                if (quads >= remainingBudget || patches >= 3) break;
                float strength = CoastalFoamModel.trail(wave, cell.distanceFromShoreBlocks())
                        * WaterRenderingConfig.coastalFoamStrength() * season.foamMultiplier();
                if (strength <= 0.02f) continue;
                if (!level.hasChunkAt(new BlockPos(cell.blockX(), (int) cell.waterSurfaceY(), cell.blockZ()))) continue;
                var snapshot = ClientWaterSnapshotStore.getAtBlock(level, cell.blockX(), cell.blockZ());
                if (snapshot == null) continue;
                var column = snapshot.column(cell.blockX() & 15, cell.blockZ() & 15);
                if (!column.wet() || column.surfaceCovered()) continue;
                float phase = wave.normalizedPhase();
                float driftX = Math.max(-0.15f, Math.min(0.15f, column.velocityX() * phase * 0.2f));
                float driftZ = Math.max(-0.15f, Math.min(0.15f, column.velocityZ() * phase * 0.2f));
                float x = cell.blockX() + 0.5f + driftX;
                float z = cell.blockZ() + 0.5f + driftZ;
                float y = ClientWaterImmersion.visibleSurfaceHeight(level, column, x, z, partialTick) + 0.045f;
                BlockPos foamPosition = BlockPos.containing(x, y, z);
                if (!level.getBlockState(foamPosition).getCollisionShape(level, foamPosition).isEmpty()) continue;
                float pattern = (float) (0.5 + 0.5 * Math.sin(cell.blockX() * 1.73 + cell.blockZ() * 2.31));
                float radius = 0.14f + (0.10f + pattern * 0.10f) * phase;
                int color = foamColor(strength * (0.50f + pattern * 0.40f));
                int light = waterLight(level, x, y, z);
                // Skewed patches and different sizes break up a tiled white sheet.
                addVertex(buffer, pose, waterSprite, light, color, 0, 1, 0, x - radius, y, z - radius * 0.55f);
                addVertex(buffer, pose, waterSprite, light, color, 0, 1, 0, x - radius * 0.65f, y, z + radius);
                addVertex(buffer, pose, waterSprite, light, color, 0, 1, 0, x + radius, y, z + radius * 0.60f);
                addVertex(buffer, pose, waterSprite, light, color, 0, 1, 0, x + radius * 0.70f, y, z - radius);
                quads++;
                patches++;
            }
        }

        boolean drawRunUp = detailedRunUp && WaterRenderingConfig.coastalRunUpEnabled(level);
        boolean drawWetness = detailedRunUp
                && WaterRenderingConfig.coastalWetnessEnabled(level)
                && wave.stage() != CoastalWaveModel.Stage.RUN_UP
                && wave.wetness() > 0.015f;
        if ((!drawRunUp && !drawWetness) || quads >= remainingBudget) {
            return quads;
        }

        for (CoastalSegment.RunUpCell cell : point.runUpCells()) {
            if (quads >= remainingBudget) {
                break;
            }
            boolean washed = cell.distanceFromWaterBlocks()
                    <= wave.maximumRunUpDistanceBlocks() + 0.001f;
            boolean covered = cell.distanceFromWaterBlocks()
                    <= wave.runUpDistanceBlocks() + 0.001f;
            if (!washed) {
                continue;
            }

            int light = waterLight(level, cell.blockX(), cell.topBlockY() + 1.0f, cell.blockZ());
            if (drawWetness && !covered && quads < remainingBudget) {
                drawTopQuad(
                        buffer,
                        pose,
                        waterSprite,
                        light,
                        wetnessColor(wave.wetness() * WaterRenderingConfig.coastalWetnessStrength()),
                        cell.blockX(),
                        cell.topBlockY() + 1.0f + SURFACE_EPSILON * 0.45f,
                        cell.blockZ()
                );
                quads++;
            }
            if (drawRunUp && covered && quads < remainingBudget) {
                int tint = waterTint(level, cell.blockX(), cell.topBlockY() + 1, cell.blockZ());
                float foam = foamEnabled
                        ? wave.foam()
                        * WaterRenderingConfig.coastalFoamStrength()
                        * season.foamMultiplier()
                        * CoastalFoamModel.wash(cell.distanceFromWaterBlocks(), wave.runUpDistanceBlocks(),
                                cell.blockX(), cell.blockZ(), wave.normalizedPhase())
                        : 0.0f;
                drawTopQuad(
                        buffer,
                        pose,
                        waterSprite,
                        light,
                        runUpColor(tint, foam, season),
                        cell.blockX(),
                        cell.topBlockY() + 1.0f + SURFACE_EPSILON,
                        cell.blockZ()
                );
                quads++;
            }
        }
        return quads;
    }

    private static void drawBreakerBand(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            TextureAtlasSprite sprite,
            int light,
            int waterColor,
            int lipColor,
            float centerX,
            float centerZ,
            CoastalBreakerGeometry.Shape shape,
            CoastalSegment segment,
            float tideOffset
    ) {
        float baseY = shape.surfaceY() + tideOffset + 0.025f;
        // A water-colored back, foamy lip, and land-facing slope form a volume.
        // The former single seaward-facing quad was culled from the beach.
        drawSlopeBand(buffer, pose, sprite, light, waterColor, segment, centerX, baseY, centerZ,
                shape.backOffset(), 0.0f, 0.0f, shape.crestHeight());
        drawSlopeBand(buffer, pose, sprite, light, lipColor, segment, centerX, baseY, centerZ,
                0.0f, shape.crestHeight(), shape.lipOffset(), shape.lipHeight());
        drawSlopeBand(buffer, pose, sprite, light, waterColor, segment, centerX, baseY, centerZ,
                shape.lipOffset(), shape.lipHeight(), shape.frontOffset(), 0.0f);
    }

    private static void drawSlopeBand(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            TextureAtlasSprite sprite,
            int light,
            int color,
            CoastalSegment segment,
            float centerX,
            float baseY,
            float centerZ,
            float fromOffset,
            float fromHeight,
            float toOffset,
            float toHeight
    ) {
        float landwardX = segment.landwardNormalX();
        float landwardZ = segment.landwardNormalZ();
        float tangentX = -landwardZ * 0.5f;
        float tangentZ = landwardX * 0.5f;
        float fromX = centerX + landwardX * fromOffset;
        float fromZ = centerZ + landwardZ * fromOffset;
        float toX = centerX + landwardX * toOffset;
        float toZ = centerZ + landwardZ * toOffset;
        float rise = toHeight - fromHeight;
        float run = toOffset - fromOffset;
        float length = Math.max(0.001f, (float) Math.hypot(rise, run));
        float normalX = -landwardX * rise / length;
        float normalY = run / length;
        float normalZ = -landwardZ * rise / length;
        addVertex(buffer, pose, sprite, light, color, normalX, normalY, normalZ,
                fromX - tangentX, baseY + fromHeight, fromZ - tangentZ);
        addVertex(buffer, pose, sprite, light, color, normalX, normalY, normalZ,
                fromX + tangentX, baseY + fromHeight, fromZ + tangentZ);
        addVertex(buffer, pose, sprite, light, color, normalX, normalY, normalZ,
                toX + tangentX, baseY + toHeight, toZ + tangentZ);
        addVertex(buffer, pose, sprite, light, color, normalX, normalY, normalZ,
                toX - tangentX, baseY + toHeight, toZ - tangentZ);
    }

    private static void drawTopQuad(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            TextureAtlasSprite sprite,
            int light,
            int color,
            float x,
            float y,
            float z
    ) {
        addVertex(buffer, pose, sprite, light, color, 0.0f, 1.0f, 0.0f, x, y, z);
        addVertex(buffer, pose, sprite, light, color, 0.0f, 1.0f, 0.0f, x, y, z + 1.0f);
        addVertex(buffer, pose, sprite, light, color, 0.0f, 1.0f, 0.0f, x + 1.0f, y, z + 1.0f);
        addVertex(buffer, pose, sprite, light, color, 0.0f, 1.0f, 0.0f, x + 1.0f, y, z);
    }

    private static void addVertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            TextureAtlasSprite sprite,
            int light,
            int color,
            float normalX,
            float normalY,
            float normalZ,
            float x,
            float y,
            float z
    ) {
        float u = sprite.getU(tile(x * TEXTURE_SCALE));
        float v = sprite.getV(tile(z * TEXTURE_SCALE));
        buffer.addVertex(pose, x, y, z)
                .setColor(
                        (color >> 16) & 0xFF,
                        (color >> 8) & 0xFF,
                        color & 0xFF,
                        (color >>> 24) & 0xFF
                )
                .setUv(u, v)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static int waterTint(ClientLevel level, int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        int base = IClientFluidTypeExtensions.of(Fluids.WATER)
                .getTintColor(WATER_STATE, level, position);
        return GlacialWaterTintManager.surfaceTint(level, position, base);
    }

    private static int runUpColor(
            int tint,
            float foam,
            CoastalSeasonModel.Sample season
    ) {
        int seasonalTint = seasonalTint(tint, season);
        float whiten = Math.max(0.0f, Math.min(0.92f, foam * 1.10f));
        int red = blend((seasonalTint >> 16) & 0xFF, 238, whiten);
        int green = blend((seasonalTint >> 8) & 0xFF, 247, whiten);
        int blue = blend(seasonalTint & 0xFF, 250, whiten);
        int alpha = channel(0.20f + Math.min(1.0f, foam) * 0.48f);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int seasonalTint(int tint, CoastalSeasonModel.Sample season) {
        int red = brighten((tint >> 16) & 0xFF, season.brightness());
        int green = brighten((tint >> 8) & 0xFF, season.brightness());
        int blue = brighten(tint & 0xFF, season.brightness());
        float tropical = season.tropicalClarity() * 0.34f;
        red = blend(red, 69, tropical);
        green = blend(green, 217, tropical);
        blue = blend(blue, 208, tropical);
        float cold = season.coldBlue() * 0.28f;
        red = blend(red, 76, cold);
        green = blend(green, 135, cold);
        blue = blend(blue, 217, cold);
        return red << 16 | green << 8 | blue;
    }

    private static int breakerColor(int tint, float foam, CoastalSeasonModel.Sample season) {
        int rgb = runUpColor(tint, foam, season) & 0xFFFFFF;
        return channel(0.56f + Math.min(1.0f, foam) * 0.16f) << 24 | rgb;
    }

    private static int foamColor(float strength) {
        // Zero residual strength must become transparent rather than retaining
        // a fixed alpha floor and disappearing abruptly at the draw threshold.
        int alpha = channel(Math.max(0.0f, Math.min(1.0f, strength)) * 0.94f);
        return alpha << 24 | 0xEAF7FA;
    }

    private static int wetnessColor(float strength) {
        int alpha = channel(Math.min(0.24f, Math.max(0.0f, strength) * 0.16f));
        return alpha << 24 | 0x101820;
    }

    private static int waterLight(ClientLevel level, float x, float y, float z) {
        BlockPos position = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(position)) {
            return LightTexture.FULL_BRIGHT;
        }
        int packed = LevelRenderer.getLightColor(level, position);
        return LightTexture.pack(
                Math.max(7, LightTexture.block(packed)),
                Math.max(7, LightTexture.sky(packed))
        );
    }

    private static int blend(int from, int to, float factor) {
        return Math.max(0, Math.min(255, Math.round(from + (to - from) * factor)));
    }

    private static int brighten(int channel, float multiplier) {
        return Math.max(0, Math.min(255, Math.round(channel * multiplier)));
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }

    private static float tile(float value) {
        return value - (float) Math.floor(value);
    }
}
