package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.thunder.wildernessodysseyapi.weather.api.CloudType;

import java.util.List;
import java.util.Objects;

/**
 * Converts a cloud genus into blended low, middle, high, and tower decks.
 *
 * <p>The profile is client presentation derived from synchronized continuous
 * fields. Multiple bands can be visible together—for example a cumulonimbus
 * tower and high anvil—without adding client-owned weather state.</p>
 */
public record CloudLayerProfile(
        CloudType dominantType,
        BandProfile low,
        BandProfile middle,
        BandProfile high,
        BandProfile convective
) {
    public static final CloudLayerProfile CLEAR = new CloudLayerProfile(
            CloudType.CLEAR,
            BandProfile.EMPTY_LOW,
            BandProfile.EMPTY_MIDDLE,
            BandProfile.EMPTY_HIGH,
            BandProfile.EMPTY_CONVECTIVE
    );

    public CloudLayerProfile {
        dominantType = Objects.requireNonNullElse(dominantType, CloudType.CLEAR);
        low = Objects.requireNonNullElse(low, BandProfile.EMPTY_LOW);
        middle = Objects.requireNonNullElse(middle, BandProfile.EMPTY_MIDDLE);
        high = Objects.requireNonNullElse(high, BandProfile.EMPTY_HIGH);
        convective = Objects.requireNonNullElse(convective, BandProfile.EMPTY_CONVECTIVE);
    }

    /** Builds a continuous multi-deck presentation from one cloud field. */
    public static CloudLayerProfile evaluate(CloudFieldSample field) {
        CloudFieldSample sample = field == null ? CloudFieldSample.CLEAR : field;
        CloudType type = sample.cloudType();
        if (type == CloudType.CLEAR || sample.support() <= 0.0) {
            return CLEAR;
        }

        double support = sample.support();
        double precipitationMass = sample.precipitationIntensity()
                >= CloudCoverageModel.PRECIPITATION_COVERAGE_THRESHOLD
                ? 0.72 + sample.precipitationIntensity() * 0.28
                : 0.0;
        double mass = unit(Math.max(
                smoothstep(0.035, 0.72, sample.cloudWater()),
                precipitationMass
        )) * support;
        double shear = unit(sample.windShear() / 1.25);
        double lowBase = lowBaseOffset(sample);
        double lowDepth = clamp(4.0 + sample.cloudDepth() * 15.0, 4.0, 20.0);
        double middleBase = 38.0 + sample.temperature() * 0.08;
        double middleDepth = clamp(7.0 + sample.cloudDepth() * 16.0, 7.0, 24.0);
        double highBase = 76.0 + Math.max(0.0, sample.temperature() - 5.0) * 0.10;
        double highDepth = clamp(5.0 + shear * 8.0 + sample.cloudDepth() * 5.0, 5.0, 18.0);
        double towerDepth = clamp(
                18.0
                        + sample.cloudDepth() * 58.0
                        + sample.stormEnergy() * 28.0
                        + Math.max(0.0, sample.verticalMotion()) * 18.0,
                18.0,
                112.0
        );

        BandProfile low = BandProfile.empty(CloudBand.LOW);
        BandProfile middle = BandProfile.empty(CloudBand.MIDDLE);
        BandProfile high = BandProfile.empty(CloudBand.HIGH);
        BandProfile tower = BandProfile.empty(CloudBand.CONVECTIVE);
        switch (type) {
            case CIRRUS -> high = band(CloudBand.HIGH, mass * 0.52, highBase, highDepth * 0.65,
                    CloudType.Shape.WISPY);
            case CIRROSTRATUS -> high = band(CloudBand.HIGH, mass * 0.72, highBase - 4.0, highDepth,
                    CloudType.Shape.WISPY);
            case CIRROCUMULUS -> high = band(CloudBand.HIGH, mass * 0.68, highBase, highDepth * 0.82,
                    CloudType.Shape.CELLULAR);
            case ALTOSTRATUS -> middle = band(CloudBand.MIDDLE, mass * 0.82, middleBase, middleDepth,
                    CloudType.Shape.LAYERED);
            case ALTOCUMULUS -> middle = band(CloudBand.MIDDLE, mass * 0.78, middleBase, middleDepth,
                    CloudType.Shape.CELLULAR);
            case STRATUS -> low = band(CloudBand.LOW, mass * 0.92, lowBase, lowDepth * 0.72,
                    CloudType.Shape.LAYERED);
            case STRATOCUMULUS -> low = band(CloudBand.LOW, mass * 0.88, lowBase, lowDepth,
                    CloudType.Shape.CELLULAR);
            case CUMULUS -> {
                low = band(CloudBand.LOW, mass * 0.48, lowBase, lowDepth, CloudType.Shape.CELLULAR);
                tower = band(
                        CloudBand.CONVECTIVE,
                        mass * (0.56 + sample.instability() * 0.24),
                        lowBase,
                        towerDepth * 0.58,
                        CloudType.Shape.CONVECTIVE
                );
            }
            case NIMBOSTRATUS -> {
                low = band(CloudBand.LOW, mass, lowBase - 3.0, lowDepth * 1.15, CloudType.Shape.LAYERED);
                middle = band(CloudBand.MIDDLE, mass * 0.78, middleBase - 8.0, middleDepth * 1.25,
                        CloudType.Shape.LAYERED);
            }
            case CUMULONIMBUS -> {
                tower = band(CloudBand.CONVECTIVE, mass, lowBase - 4.0, towerDepth,
                        CloudType.Shape.CONVECTIVE);
                high = band(
                        CloudBand.HIGH,
                        mass * (0.46 + shear * 0.36),
                        Math.max(highBase, lowBase + towerDepth * 0.72),
                        highDepth * (1.0 + shear * 0.45),
                        CloudType.Shape.LAYERED
                );
            }
            case CLEAR -> {
                return CLEAR;
            }
        }
        return new CloudLayerProfile(type, low, middle, high, tower);
    }

    /** Returns all four altitude bands in stable low-to-high order. */
    public List<BandProfile> bands() {
        return List.of(low, middle, high, convective);
    }

    /** Returns the strongest visible deck used by compatibility renderers. */
    public BandProfile dominantBand() {
        BandProfile result = low;
        for (BandProfile candidate : List.of(middle, high, convective)) {
            if (candidate.density() > result.density()) {
                result = candidate;
            }
        }
        return result;
    }

    /** Returns a normalized union-like visibility signal across every deck. */
    public double visibleCoverage() {
        double clear = 1.0;
        for (BandProfile band : bands()) {
            clear *= 1.0 - band.density();
        }
        return unit(1.0 - clear);
    }

    private static BandProfile band(
            CloudBand band,
            double density,
            double baseOffset,
            double depth,
            CloudType.Shape shape
    ) {
        return new BandProfile(band, density, baseOffset, depth, shape);
    }

    private static double lowBaseOffset(CloudFieldSample sample) {
        double warmLift = Math.max(0.0, sample.temperature() - 18.0) * 0.18;
        double moistureLowering = sample.humidity() * 16.0;
        double ascentLowering = Math.max(0.0, sample.verticalMotion()) * 8.0;
        return clamp(10.0 + warmLift - moistureLowering - ascentLowering, -12.0, 18.0);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double amount = unit((value - edge0) / (edge1 - edge0));
        return amount * amount * (3.0 - 2.0 * amount);
    }

    private static double unit(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        double finite = Double.isFinite(value) ? value : minimum;
        return Math.max(minimum, Math.min(maximum, finite));
    }

    /** Altitude band encoded into the volumetric vertex stream. */
    public enum CloudBand {
        LOW,
        MIDDLE,
        HIGH,
        CONVECTIVE
    }

    /** Immutable geometry and density for one visual cloud deck. */
    public record BandProfile(
            CloudBand band,
            double density,
            double baseOffsetBlocks,
            double depthBlocks,
            CloudType.Shape shape
    ) {
        private static final BandProfile EMPTY_LOW = empty(CloudBand.LOW);
        private static final BandProfile EMPTY_MIDDLE = empty(CloudBand.MIDDLE);
        private static final BandProfile EMPTY_HIGH = empty(CloudBand.HIGH);
        private static final BandProfile EMPTY_CONVECTIVE = empty(CloudBand.CONVECTIVE);

        public BandProfile {
            band = Objects.requireNonNullElse(band, CloudBand.LOW);
            density = unit(density);
            baseOffsetBlocks = clamp(baseOffsetBlocks, -16.0, 160.0);
            depthBlocks = clamp(depthBlocks, 0.0, 128.0);
            shape = Objects.requireNonNullElse(shape, CloudType.Shape.LAYERED);
            if (density <= 0.0) {
                depthBlocks = 0.0;
            }
        }

        /** Returns whether this deck should emit any geometry. */
        public boolean visible() {
            return density > 0.015 && depthBlocks > 0.0;
        }

        private static BandProfile empty(CloudBand band) {
            return new BandProfile(band, 0.0, 0.0, 0.0, CloudType.Shape.LAYERED);
        }
    }
}
