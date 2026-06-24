package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveAnimator;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Resolves camera immersion against canonical fill and the animated surface.
 *
 * <p>Vanilla tests only the flat fluid height stored in a block. This resolver
 * retains that fluid as the compatibility source, then applies the same
 * Gerstner/tide surface used by the replacement renderer so fog and overlays
 * cross the visible wave rather than an invisible horizontal plane.</p>
 */
public final class ClientWaterImmersion {

    private static final int MAX_SURFACE_SCAN = 64;
    private static final int MAX_DEPTH_SAMPLE = 32;

    private static ClientLevel cachedLevel;
    private static long cachedGameTime = Long.MIN_VALUE;
    private static float cachedPartialTick = Float.NaN;
    private static Vec3 cachedCameraPosition = new Vec3(Double.NaN, Double.NaN, Double.NaN);
    private static ImmersionState cachedState = ImmersionState.DRY;

    private ClientWaterImmersion() {
    }

    /** Returns the camera's current bounded water-immersion state. */
    public static ImmersionState sample(Camera camera, float partialTick) {
        if (!(camera.getEntity().level() instanceof ClientLevel level)) {
            return ImmersionState.DRY;
        }

        Vec3 cameraPosition = camera.getPosition();
        if (isCached(level, cameraPosition, partialTick)) {
            return cachedState;
        }

        cachedLevel = level;
        cachedGameTime = level.getGameTime();
        cachedPartialTick = partialTick;
        cachedCameraPosition = cameraPosition;
        cachedState = resolve(level, cameraPosition);
        return cachedState;
    }

    /** Clears per-frame state when the client unloads a dimension. */
    public static void clear(ClientLevel level) {
        if (cachedLevel == level) {
            cachedLevel = null;
            cachedGameTime = Long.MIN_VALUE;
            cachedPartialTick = Float.NaN;
            cachedCameraPosition = new Vec3(Double.NaN, Double.NaN, Double.NaN);
            cachedState = ImmersionState.DRY;
        }
    }

    private static ImmersionState resolve(ClientLevel level, Vec3 cameraPosition) {
        int x = (int) Math.floor(cameraPosition.x);
        int z = (int) Math.floor(cameraPosition.z);
        int cameraY = (int) Math.floor(cameraPosition.y);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, cameraY, z);
        if (!level.hasChunkAt(cursor)) {
            return ImmersionState.DRY;
        }

        // The two-block downward allowance captures a wave crest that rises
        // above vanilla's otherwise flat top fluid block.
        int waterY = Integer.MIN_VALUE;
        for (int offset = 0; offset <= 2; offset++) {
            cursor.set(x, cameraY - offset, z);
            if (hasWater(level, cursor)) {
                waterY = cursor.getY();
                break;
            }
        }
        if (waterY == Integer.MIN_VALUE) {
            return resolveMobileWater(level, cameraPosition);
        }

        int topY = waterY;
        int scanned = 0;
        while (topY + 1 < level.getMaxBuildHeight() && scanned++ < MAX_SURFACE_SCAN) {
            cursor.set(x, topY + 1, z);
            if (!hasWater(level, cursor)) {
                break;
            }
            topY++;
        }

        BlockPos surfacePos = new BlockPos(x, topY, z);
        var canonicalCell = CanonicalWater.getTracked(level, surfacePos);
        float fillHeight = canonicalCell != null
                ? canonicalCell.fillFraction()
                : level.getFluidState(surfacePos).getOwnHeight();
        float baseSurfaceY = topY + fillHeight;
        float columnDepth = sampleColumnDepth(level, surfacePos);
        WaterBodyClassifier.WaterType waterType = WaterBodyClassifier.classify(level, surfacePos);

        float animatedHeight = 0.0f;
        if (WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()
                && WaterRenderingConfig.ENABLE_DYNAMIC_OCEAN_SURFACE.get()) {
            float waveBlend = smoothStep(0.35f, 4.0f, columnDepth);
            animatedHeight = GerstnerWaveAnimator.getSurfaceSampleAt(
                    (float) cameraPosition.x,
                    (float) cameraPosition.z,
                    waterType
            ).height() * waveBlend;
        }

        float surfaceY = baseSurfaceY + animatedHeight;
        float depthBelowSurface = surfaceY - (float) cameraPosition.y;
        float disturbance = canonicalCell == null ? 0.0f : clamp((float) Math.sqrt(
                canonicalCell.velocityX() * canonicalCell.velocityX()
                        + canonicalCell.velocityY() * canonicalCell.velocityY()
                        + canonicalCell.velocityZ() * canonicalCell.velocityZ()
        ) / 1.5f, 0.0f, 1.0f);
        float daylight = LightTexture.getBrightness(
                level.dimensionType(),
                level.getMaxLocalRawBrightness(surfacePos)
        );
        float seaState = waterType == WaterBodyClassifier.WaterType.OCEAN
                ? ClientOceanSeaState.current(level).strength()
                : 0.0f;

        int tint = IClientFluidTypeExtensions.of(Fluids.WATER).getTintColor(
                Fluids.WATER.defaultFluidState(),
                level,
                surfacePos
        );
        UnderwaterOpticsModel.OpticalProperties optics = UnderwaterOpticsModel.evaluate(
                depthBelowSurface,
                columnDepth,
                disturbance,
                daylight,
                ((tint >> 16) & 0xFF) / 255.0f,
                ((tint >> 8) & 0xFF) / 255.0f,
                (tint & 0xFF) / 255.0f,
                seaState,
                WaterRenderingConfig.UNDERWATER_VISIBILITY_BLOCKS.get().floatValue(),
                WaterRenderingConfig.UNDERWATER_TURBIDITY_STRENGTH.get().floatValue()
        );
        return new ImmersionState(true, surfaceY, depthBelowSurface, seaState, optics);
    }

    private static ImmersionState resolveMobileWater(ClientLevel level, Vec3 cameraPosition) {
        SPHSimulationManager.MobileWaterSample mobile = SPHSimulationManager.get().sampleAt(
                level,
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z
        );
        if (!mobile.wet()) {
            return ImmersionState.DRY;
        }

        BlockPos cameraPos = BlockPos.containing(cameraPosition);
        int tint = IClientFluidTypeExtensions.of(Fluids.WATER).getTintColor(
                Fluids.WATER.defaultFluidState(),
                level,
                cameraPos
        );
        float disturbance = clamp((float) Math.sqrt(
                mobile.velocityX() * mobile.velocityX()
                        + mobile.velocityY() * mobile.velocityY()
                        + mobile.velocityZ() * mobile.velocityZ()
        ) / 1.5f, 0.0f, 1.0f);
        float daylight = LightTexture.getBrightness(
                level.dimensionType(),
                level.getMaxLocalRawBrightness(cameraPos)
        );
        UnderwaterOpticsModel.OpticalProperties optics = UnderwaterOpticsModel.evaluate(
                0.35f,
                1.0f,
                disturbance,
                daylight,
                ((tint >> 16) & 0xFF) / 255.0f,
                ((tint >> 8) & 0xFF) / 255.0f,
                (tint & 0xFF) / 255.0f,
                0.0f,
                WaterRenderingConfig.UNDERWATER_VISIBILITY_BLOCKS.get().floatValue(),
                WaterRenderingConfig.UNDERWATER_TURBIDITY_STRENGTH.get().floatValue()
        );
        return new ImmersionState(
                true,
                (float) cameraPosition.y + 0.35f,
                0.35f,
                0.0f,
                optics
        );
    }

    private static boolean hasWater(ClientLevel level, BlockPos pos) {
        var canonicalCell = CanonicalWater.getTracked(level, pos);
        if (canonicalCell != null) {
            return canonicalCell.volumeUnits() > 0;
        }
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    private static float sampleColumnDepth(ClientLevel level, BlockPos surfacePos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int offset = 1; offset <= MAX_DEPTH_SAMPLE; offset++) {
            cursor.set(surfacePos.getX(), surfacePos.getY() - offset, surfacePos.getZ());
            if (level.isOutsideBuildHeight(cursor)
                    || !level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                return offset;
            }
        }
        return MAX_DEPTH_SAMPLE;
    }

    private static boolean isCached(ClientLevel level, Vec3 cameraPosition, float partialTick) {
        return cachedLevel == level
                && cachedGameTime == level.getGameTime()
                && Math.abs(cachedPartialTick - partialTick) < 1.0e-4f
                && cachedCameraPosition.distanceToSqr(cameraPosition) < 1.0e-8;
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Immutable client sample shared by fog and overlay hooks in one frame. */
    public record ImmersionState(
            boolean waterColumnPresent,
            float surfaceY,
            float depthBelowSurface,
            float seaState,
            UnderwaterOpticsModel.OpticalProperties optics
    ) {
        /** Shared dry value for unloaded or water-free camera positions. */
        public static final ImmersionState DRY = new ImmersionState(
                false,
                Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                0.0f,
                new UnderwaterOpticsModel.OpticalProperties(0.0f, 0.0f, 0.0f,
                        1.0f, 128.0f, 0.0f, 0.0f, 0.0f)
        );

        /** Returns whether any visible underwater transition should be active. */
        public boolean isVisuallySubmerged() {
            return waterColumnPresent && optics.immersionBlend() > 0.001f;
        }
    }
}
