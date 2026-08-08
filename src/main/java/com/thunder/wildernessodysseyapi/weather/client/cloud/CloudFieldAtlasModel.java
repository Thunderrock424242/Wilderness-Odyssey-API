package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.thunder.wildernessodysseyapi.weather.api.CloudType;

/**
 * Pure layout and pixel-packing rules for the continuous GPU cloud field.
 *
 * <p>The first atlas plane stores four altitude bands for a detailed near
 * field followed by four bands for a coarser distant field. A matching second
 * plane stores morphology weights. All origins are snapped in world space, so
 * camera movement never changes the weather sampled by one texel.</p>
 */
public final class CloudFieldAtlasModel {

    public static final int BAND_COUNT = CloudLayerProfile.CloudBand.values().length;
    public static final float MINIMUM_BASE_OFFSET = -16.0F;
    public static final float MAXIMUM_BASE_OFFSET = 160.0F;
    public static final float MAXIMUM_DEPTH = 128.0F;
    private static final float[] BAND_MINIMUM_OFFSETS = {-16.0F, 16.0F, 56.0F, -16.0F};
    private static final float[] BAND_MAXIMUM_OFFSETS = {48.0F, 96.0F, 160.0F, 144.0F};

    private CloudFieldAtlasModel() {
    }

    /** Returns the padded lower carrier bound for one low-to-high cloud band. */
    public static float bandMinimumOffset(int band) {
        return BAND_MINIMUM_OFFSETS[clamp(band, 0, BAND_COUNT - 1)];
    }

    /** Returns the padded upper carrier bound for one low-to-high cloud band. */
    public static float bandMaximumOffset(int band) {
        return BAND_MAXIMUM_OFFSETS[clamp(band, 0, BAND_COUNT - 1)];
    }

    /** Selects a coordinated field-resolution and lighting budget from the primary step count. */
    public static QualityPreset qualityForSteps(int raymarchSteps) {
        if (raymarchSteps <= 16) {
            return QualityPreset.PERFORMANCE;
        }
        if (raymarchSteps <= 32) {
            return QualityPreset.BALANCED;
        }
        return QualityPreset.CINEMATIC;
    }

    /** Builds a world-snapped atlas layout around the supplied camera position. */
    public static Layout layout(
            double cameraX,
            double cameraZ,
            int nearRadiusBlocks,
            int distantRadiusBlocks,
            int distantSpacingBlocks,
            int raymarchSteps,
            boolean distantEnabled
    ) {
        QualityPreset quality = qualityForSteps(raymarchSteps);
        int nearSpacing = quality.fieldSpacingBlocks();
        int distantSpacing = Math.max(Math.max(1, distantSpacingBlocks), nearSpacing * 4);
        int centerQuantum = nearSpacing * 4;
        int centerX = snap(cameraX, centerQuantum);
        int centerZ = snap(cameraZ, centerQuantum);
        int nearRadius = alignRadius(nearRadiusBlocks, nearSpacing);
        int distantRadius = distantEnabled
                ? alignRadius(Math.max(nearRadius, distantRadiusBlocks), distantSpacing)
                : nearRadius;
        int distantCenterX = snap(cameraX, distantSpacing);
        int distantCenterZ = snap(cameraZ, distantSpacing);
        int nearDimension = nearRadius * 2 / nearSpacing + 1;
        int distantDimension = distantEnabled ? distantRadius * 2 / distantSpacing + 1 : 0;
        int atlasWidth = Math.max(nearDimension, distantDimension);
        int distantRowOffset = nearDimension * BAND_COUNT;
        int morphologyRowOffset = distantRowOffset + distantDimension * BAND_COUNT;
        int atlasHeight = morphologyRowOffset * 2;
        return new Layout(
                quality,
                centerX,
                centerZ,
                centerX - nearRadius,
                centerZ - nearRadius,
                nearRadius,
                nearSpacing,
                nearDimension,
                distantCenterX - distantRadius,
                distantCenterZ - distantRadius,
                distantRadius,
                distantSpacing,
                distantDimension,
                atlasWidth,
                atlasHeight,
                distantRowOffset,
                morphologyRowOffset
        );
    }

    /** Returns whether the camera has crossed the padded recentering threshold. */
    public static boolean shouldRecenter(Layout layout, double cameraX, double cameraZ) {
        if (layout == null) {
            return true;
        }
        double threshold = layout.nearSpacingBlocks() * 4.0;
        return Math.abs(cameraX - layout.centerBlockX()) >= threshold
                || Math.abs(cameraZ - layout.centerBlockZ()) >= threshold;
    }

    /** Returns the atlas row for one near or distant cloud band and local Z sample. */
    public static int atlasRow(Layout layout, boolean distant, int band, int localZ) {
        int boundedBand = clamp(band, 0, BAND_COUNT - 1);
        if (distant) {
            return layout.distantRowOffset() + boundedBand * layout.distantDimension() + localZ;
        }
        return boundedBand * layout.nearDimension() + localZ;
    }

    /** Returns the second-plane row containing smoothly blendable morphology weights. */
    public static int morphologyAtlasRow(Layout layout, boolean distant, int band, int localZ) {
        return layout.morphologyRowOffset() + atlasRow(layout, distant, band, localZ);
    }

    /** Packs shader-facing coverage, base, depth, and storm values into NativeImage ABGR. */
    public static int packPixel(double coverage, double baseOffset, double depth, double storm) {
        int red = byteValue(coverage);
        int green = byteValue((baseOffset - MINIMUM_BASE_OFFSET)
                / (MAXIMUM_BASE_OFFSET - MINIMUM_BASE_OFFSET));
        int blue = byteValue(depth / MAXIMUM_DEPTH);
        int alpha = byteValue(storm);
        return alpha << 24 | blue << 16 | green << 8 | red;
    }

    /** Packs one-hot wispy, layered, cellular, and convective morphology weights. */
    public static int packMorphologyPixel(CloudType.Shape shape) {
        CloudType.Shape safeShape = shape == null ? CloudType.Shape.CLEAR : shape;
        return switch (safeShape) {
            case CLEAR -> 0;
            case WISPY -> 0x000000FF;
            case LAYERED -> 0x0000FF00;
            case CELLULAR -> 0x00FF0000;
            case CONVECTIVE -> 0xFF000000;
        };
    }

    /** Decodes one packed pixel for regression tests and CPU diagnostics. */
    public static DecodedPixel decodePixel(int packed) {
        double coverage = (packed & 0xFF) / 255.0;
        double base = ((packed >>> 8) & 0xFF) / 255.0
                * (MAXIMUM_BASE_OFFSET - MINIMUM_BASE_OFFSET) + MINIMUM_BASE_OFFSET;
        double depth = ((packed >>> 16) & 0xFF) / 255.0 * MAXIMUM_DEPTH;
        double storm = ((packed >>> 24) & 0xFF) / 255.0;
        return new DecodedPixel(coverage, base, depth, storm);
    }

    private static int alignRadius(int radius, int spacing) {
        int safeRadius = Math.max(spacing, radius);
        return (safeRadius + spacing - 1) / spacing * spacing;
    }

    private static int snap(double value, int quantum) {
        return (int) Math.floor(value / quantum) * quantum;
    }

    private static int byteValue(double value) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, finite)) * 255.0);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Coordinated quality families exposed in F3 and selected from raymarch steps. */
    public enum QualityPreset {
        PERFORMANCE(12, 2),
        BALANCED(6, 4),
        CINEMATIC(4, 6);

        private final int fieldSpacingBlocks;
        private final int lightingSteps;

        QualityPreset(int fieldSpacingBlocks, int lightingSteps) {
            this.fieldSpacingBlocks = fieldSpacingBlocks;
            this.lightingSteps = lightingSteps;
        }

        /** World-space distance between detailed cloud-field samples. */
        public int fieldSpacingBlocks() {
            return fieldSpacingBlocks;
        }

        /** Maximum secondary samples toward the sun for this quality family. */
        public int lightingSteps() {
            return lightingSteps;
        }

        /** Lowercase name used by compact player-facing diagnostics. */
        public String displayName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Immutable atlas dimensions and world-coordinate mappings. */
    public record Layout(
            QualityPreset quality,
            int centerBlockX,
            int centerBlockZ,
            int nearOriginBlockX,
            int nearOriginBlockZ,
            int nearRadiusBlocks,
            int nearSpacingBlocks,
            int nearDimension,
            int distantOriginBlockX,
            int distantOriginBlockZ,
            int distantRadiusBlocks,
            int distantSpacingBlocks,
            int distantDimension,
            int atlasWidth,
            int atlasHeight,
            int distantRowOffset,
            int morphologyRowOffset
    ) {
        /** Returns whether this layout includes the coarse distant bands. */
        public boolean hasDistantField() {
            return distantDimension > 0;
        }
    }

    /** Approximate unpacked values represented by one normalized RGBA texel. */
    public record DecodedPixel(double coverage, double baseOffset, double depth, double storm) {
    }
}
