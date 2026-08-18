package com.thunder.wildernessodysseyapi.weather.wind;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindSample;
import com.thunder.wildernessodysseyapi.weather.api.WindSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Pure query model that derives continuous regional wind from localized weather.
 *
 * <p>The model owns no ticking state. Ambient direction, natural variation,
 * and gust timing are deterministic functions of atmospheric-cell coordinates,
 * dimension identity, and world time. Bilinear interpolation uses the same
 * cell-center convention as weather snapshots, avoiding chunk-border steps.</p>
 */
public final class WindFieldModel {
    private static final double TICKS_PER_MINECRAFT_MINUTE = 1_200.0D;
    private static final long DIRECTION_EPOCH_TICKS = 24_000L;
    private static final double TAU = Math.PI * 2.0D;
    private static final long DIRECTION_SALT = 0x44D2E17A0B37C5A1L;
    private static final long AMBIENT_SPEED_SALT = 0x6932C54A81DF0B77L;
    private static final long GUST_SALT = 0x1F8750D9A2C46E3BL;
    private static final long TURBULENCE_SALT = 0x72A419BC3058E6DFL;

    private WindFieldModel() {
    }

    /**
     * Samples one immutable wind result without loading chunks or mutating state.
     *
     * @param weather spatially interpolated localized weather at the query point
     * @param settings captured server-authored wind settings
     * @param cellSize atmospheric cell width in blocks
     * @param blockX world X coordinate
     * @param blockZ world Z coordinate
     * @param gameTime synchronized level game time in ticks
     * @param dimensionSalt stable dimension identity salt
     */
    public static WindSample sample(
            WeatherSample weather,
            WindSettings settings,
            int cellSize,
            double blockX,
            double blockZ,
            long gameTime,
            long dimensionSalt
    ) {
        WeatherSample atmosphere = Objects.requireNonNullElse(weather, WeatherSample.CLEAR);
        WindSettings controls = Objects.requireNonNullElse(settings, WindSettings.DEFAULT);
        int safeCellSize = Math.max(1, cellSize);
        double safeX = coordinate(blockX);
        double safeZ = coordinate(blockZ);
        AtmosphereCellKey region = AtmosphereCellKey.fromBlock(
                floorCoordinate(safeX),
                floorCoordinate(safeZ),
                safeCellSize
        );
        if (!controls.enabled() || controls.maxWindSpeed() <= 0.0F) {
            return WindSample.calm(region);
        }

        // Slowly evolving value noise supplies natural clear-weather motion
        // while the simulated pressure wind remains the storm authority.
        Vector2 ambientDirection = ambientDirection(
                safeX,
                safeZ,
                safeCellSize,
                gameTime,
                dimensionSalt
        );
        double ambientVariation = lerp(
                0.72D,
                1.12D,
                spatialUnitNoise(
                        safeX,
                        safeZ,
                        safeCellSize,
                        dimensionSalt ^ AMBIENT_SPEED_SALT
                )
        );
        double baseSpeed = Math.min(
                controls.maxWindSpeed(),
                controls.baseWindStrength() * ambientVariation
        );

        // Cloud growth and pressure deficit make approaching storms noticeable
        // before precipitation, while mature storm energy dominates later.
        double severity = weatherSeverity(atmosphere);
        double transport = unit(atmosphere.wind().magnitude());
        double stormScale = lerp(1.0D, controls.stormWindMultiplier(), severity);
        double weatherPotential = controls.maxWindSpeed()
                * (transport * 0.34D + severity * 0.22D)
                * stormScale;
        double sustainedCap = Math.max(
                baseSpeed,
                controls.maxWindSpeed() - controls.gustStrength() * 0.75D
        );
        double speed = Math.min(sustainedCap, baseSpeed + weatherPotential);
        double weatherContribution = Math.max(0.0D, speed - baseSpeed);

        Vector2 atmosphericDirection = atmosphere.wind().magnitude() <= 1.0E-6D
                ? ambientDirection
                : new Vector2(atmosphere.wind().x(), atmosphere.wind().z()).normalized(ambientDirection);
        Vector2 horizontalDirection = ambientDirection.scale(baseSpeed)
                .add(atmosphericDirection.scale(weatherContribution))
                .normalized(ambientDirection);

        GustState gustState = gustState(
                safeX,
                safeZ,
                safeCellSize,
                gameTime,
                controls.gustFrequency(),
                dimensionSalt,
                region
        );
        double gustPotential = controls.gustStrength()
                * gustState.factor()
                * lerp(0.65D, 1.0D, severity)
                * stormScale;
        double gust = Math.min(
                Math.max(0.0D, controls.maxWindSpeed() - speed),
                Math.max(0.0D, gustPotential)
        );

        // Vertical motion remains subtle in ordinary weather and becomes
        // visible only with convection or coherent storm turbulence.
        double turbulence = spatialUnitNoise(
                safeX,
                safeZ,
                safeCellSize,
                dimensionSalt ^ TURBULENCE_SALT
        ) * 2.0D - 1.0D;
        double vertical = clamp(
                atmosphere.verticalMotion() * (0.04D + severity * 0.24D)
                        + turbulence * severity * gustState.factor() * 0.08D,
                -0.35D,
                0.35D
        );
        Vec3 direction = new Vec3(horizontalDirection.x(), vertical, horizontalDirection.z()).normalize();

        return new WindSample(
                direction,
                (float) speed,
                (float) gust,
                (float) weatherContribution,
                (float) gustState.factor(),
                (float) gustState.phase(),
                gustState.cycle(),
                region
        );
    }

    /** Returns a stable salt shared by both logical sides for one dimension id. */
    public static long dimensionSalt(ResourceLocation dimension) {
        return dimensionSalt(Objects.requireNonNull(dimension, "dimension").toString());
    }

    /** String overload keeps pure model tests independent of registry bootstrap. */
    public static long dimensionSalt(String dimension) {
        String value = Objects.requireNonNull(dimension, "dimension");
        long hash = 0xCBF29CE484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001B3L;
        }
        return mix(hash);
    }

    private static Vector2 ambientDirection(
            double blockX,
            double blockZ,
            int cellSize,
            long gameTime,
            long dimensionSalt
    ) {
        double epochCoordinate = Math.max(0L, gameTime) / (double) DIRECTION_EPOCH_TICKS;
        long epoch = (long) Math.floor(epochCoordinate);
        double timeAmount = smooth(epochCoordinate - epoch);
        return spatialVector(
                blockX,
                blockZ,
                cellSize,
                dimensionSalt ^ DIRECTION_SALT,
                epoch,
                timeAmount
        ).normalized(new Vector2(1.0D, 0.0D));
    }

    private static Vector2 spatialVector(
            double blockX,
            double blockZ,
            int cellSize,
            long salt,
            long epoch,
            double timeAmount
    ) {
        GridPoint point = gridPoint(blockX, blockZ, cellSize);
        Vector2 northWest = temporalDirection(point.minimumX(), point.minimumZ(), salt, epoch, timeAmount);
        Vector2 northEast = temporalDirection(point.minimumX() + 1, point.minimumZ(), salt, epoch, timeAmount);
        Vector2 southWest = temporalDirection(point.minimumX(), point.minimumZ() + 1, salt, epoch, timeAmount);
        Vector2 southEast = temporalDirection(point.minimumX() + 1, point.minimumZ() + 1, salt, epoch, timeAmount);
        Vector2 north = Vector2.lerp(northWest, northEast, point.xAmount());
        Vector2 south = Vector2.lerp(southWest, southEast, point.xAmount());
        return Vector2.lerp(north, south, point.zAmount());
    }

    private static Vector2 temporalDirection(
            int cellX,
            int cellZ,
            long salt,
            long epoch,
            double amount
    ) {
        Vector2 current = hashedDirection(cellX, cellZ, epoch, salt);
        Vector2 next = hashedDirection(cellX, cellZ, epoch + 1L, salt);
        return Vector2.lerp(current, next, amount).normalized(current);
    }

    private static Vector2 hashedDirection(int cellX, int cellZ, long epoch, long salt) {
        double angle = unitHash(cellX, cellZ, epoch, salt) * TAU;
        return new Vector2(Math.cos(angle), Math.sin(angle));
    }

    private static GustState gustState(
            double blockX,
            double blockZ,
            int cellSize,
            long gameTime,
            double frequency,
            long dimensionSalt,
            AtmosphereCellKey region
    ) {
        if (frequency <= 0.0D) {
            return GustState.NONE;
        }
        double cycleCoordinate = Math.max(0L, gameTime) * frequency / TICKS_PER_MINECRAFT_MINUTE;
        GridPoint point = gridPoint(blockX, blockZ, cellSize);
        double northWest = gustEnvelope(point.minimumX(), point.minimumZ(), cycleCoordinate, dimensionSalt);
        double northEast = gustEnvelope(point.minimumX() + 1, point.minimumZ(), cycleCoordinate, dimensionSalt);
        double southWest = gustEnvelope(point.minimumX(), point.minimumZ() + 1, cycleCoordinate, dimensionSalt);
        double southEast = gustEnvelope(point.minimumX() + 1, point.minimumZ() + 1, cycleCoordinate, dimensionSalt);
        double north = lerp(northWest, northEast, point.xAmount());
        double south = lerp(southWest, southEast, point.xAmount());
        double factor = unit(lerp(north, south, point.zAmount()));

        double regionalCoordinate = cycleCoordinate
                + unitHash(region.x(), region.z(), 0L, dimensionSalt ^ GUST_SALT);
        long cycle = Math.max(0L, (long) Math.floor(regionalCoordinate));
        return new GustState(factor, fraction(regionalCoordinate), cycle);
    }

    private static double gustEnvelope(int cellX, int cellZ, double cycleCoordinate, long dimensionSalt) {
        double phase = fraction(cycleCoordinate
                + unitHash(cellX, cellZ, 0L, dimensionSalt ^ GUST_SALT));
        double pulse = Math.max(0.0D, Math.sin(phase * TAU));
        return pulse * pulse * pulse * pulse;
    }

    private static double spatialUnitNoise(
            double blockX,
            double blockZ,
            int cellSize,
            long salt
    ) {
        GridPoint point = gridPoint(blockX, blockZ, cellSize);
        double northWest = unitHash(point.minimumX(), point.minimumZ(), 0L, salt);
        double northEast = unitHash(point.minimumX() + 1, point.minimumZ(), 0L, salt);
        double southWest = unitHash(point.minimumX(), point.minimumZ() + 1, 0L, salt);
        double southEast = unitHash(point.minimumX() + 1, point.minimumZ() + 1, 0L, salt);
        return lerp(
                lerp(northWest, northEast, point.xAmount()),
                lerp(southWest, southEast, point.xAmount()),
                point.zAmount()
        );
    }

    private static GridPoint gridPoint(double blockX, double blockZ, int cellSize) {
        double gridX = blockX / cellSize - 0.5D;
        double gridZ = blockZ / cellSize - 0.5D;
        int minimumX = floorCoordinate(gridX);
        int minimumZ = floorCoordinate(gridZ);
        return new GridPoint(
                minimumX,
                minimumZ,
                smooth(gridX - minimumX),
                smooth(gridZ - minimumZ)
        );
    }

    private static double weatherSeverity(WeatherSample weather) {
        double pressureDeficit = unit((1.02D - weather.pressure()) / 0.18D);
        return unit(
                weather.stormEnergy() * 0.42D
                        + weather.precipitationIntensity() * 0.20D
                        + weather.instability() * 0.16D
                        + weather.cloudWater() * 0.12D
                        + pressureDeficit * 0.10D
        );
    }

    private static double unitHash(int cellX, int cellZ, long epoch, long salt) {
        long value = salt;
        value ^= (long) cellX * 0x9E3779B97F4A7C15L;
        value = Long.rotateLeft(value, 23);
        value ^= (long) cellZ * 0xC2B2AE3D27D4EB4FL;
        value = Long.rotateLeft(value, 29);
        value ^= epoch * 0x165667B19E3779F9L;
        long mixed = mix(value);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double coordinate(double value) {
        return clamp(Double.isFinite(value) ? value : 0.0D, -30_000_000.0D, 30_000_000.0D);
    }

    private static int floorCoordinate(double value) {
        double bounded = clamp(value, Integer.MIN_VALUE, Integer.MAX_VALUE);
        return (int) Math.floor(bounded);
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    private static double smooth(double value) {
        double bounded = unit(value);
        return bounded * bounded * (3.0D - 2.0D * bounded);
    }

    private static double unit(double value) {
        return clamp(Double.isFinite(value) ? value : 0.0D, 0.0D, 1.0D);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * unit(amount);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record GridPoint(int minimumX, int minimumZ, double xAmount, double zAmount) {
    }

    private record GustState(double factor, double phase, long cycle) {
        private static final GustState NONE = new GustState(0.0D, 0.0D, 0L);
    }

    private record Vector2(double x, double z) {
        private Vector2 add(Vector2 other) {
            return new Vector2(x + other.x, z + other.z);
        }

        private Vector2 scale(double scale) {
            return new Vector2(x * scale, z * scale);
        }

        private Vector2 normalized(Vector2 fallback) {
            double magnitude = Math.hypot(x, z);
            if (!Double.isFinite(magnitude) || magnitude <= 1.0E-8D) {
                return fallback;
            }
            return new Vector2(x / magnitude, z / magnitude);
        }

        private static Vector2 lerp(Vector2 from, Vector2 to, double amount) {
            return new Vector2(
                    WindFieldModel.lerp(from.x, to.x, amount),
                    WindFieldModel.lerp(from.z, to.z, amount)
            );
        }
    }
}
