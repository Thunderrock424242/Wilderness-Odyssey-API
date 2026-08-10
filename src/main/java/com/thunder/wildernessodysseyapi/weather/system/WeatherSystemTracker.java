package com.thunder.wildernessodysseyapi.weather.system;

import com.thunder.wildernessodysseyapi.weather.api.WindVector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Owns persistent identities for moving storms and fronts.
 *
 * <p>Each update predicts existing centers downwind, associates observations,
 * strengthens or dissipates identities, merges overlaps, and permits bounded
 * deterministic splits under highly organized shear. Atmospheric cells remain
 * the physical source of observations.</p>
 */
public final class WeatherSystemTracker {

    private final List<TrackedWeatherSystem> systems = new ArrayList<>();
    private long nextId = 1L;

    /** Returns an immutable system snapshot for diagnostics, effects, and forecasts. */
    public List<TrackedWeatherSystem> systems() {
        return List.copyOf(systems);
    }

    /** Returns the next persistent id for storage. */
    public long nextId() {
        return nextId;
    }

    /** Replaces tracker state after validated storage decode. */
    public void restore(long restoredNextId, List<TrackedWeatherSystem> restoredSystems) {
        systems.clear();
        if (restoredSystems != null) {
            systems.addAll(restoredSystems);
        }
        systems.sort(Comparator.comparingLong(TrackedWeatherSystem::id));
        long maximumId = systems.stream().mapToLong(TrackedWeatherSystem::id).max().orElse(0L);
        nextId = Math.max(Math.max(1L, restoredNextId), maximumId + 1L);
    }

    /**
     * Advances lifecycle state from one bounded observation set.
     *
     * @return whether persisted tracker state changed
     */
    public boolean update(
            List<Observation> sourceObservations,
            long gameTick,
            int elapsedTicks,
            TrackingSettings settings
    ) {
        TrackingSettings controls = settings == null ? TrackingSettings.DEFAULT : settings;
        if (!controls.enabled()) {
            boolean changed = !systems.isEmpty();
            systems.clear();
            return changed;
        }

        List<TrackedWeatherSystem> before = List.copyOf(systems);
        long previousNextId = nextId;
        List<Observation> observations = deduplicate(sourceObservations, controls.observationSeparationBlocks());
        boolean[] used = new boolean[observations.size()];
        List<TrackedWeatherSystem> next = new ArrayList<>(systems.size() + observations.size());
        int deltaTicks = Math.max(1, elapsedTicks);

        for (TrackedWeatherSystem system : systems) {
            double seconds = deltaTicks / 20.0;
            double predictedX = system.centerX()
                    + system.motion().x() * controls.movementBlocksPerSecond() * seconds;
            double predictedZ = system.centerZ()
                    + system.motion().z() * controls.movementBlocksPerSecond() * seconds;
            int match = nearestCompatible(system, predictedX, predictedZ, observations, used, controls);
            if (match >= 0) {
                Observation observation = observations.get(match);
                used[match] = true;
                double previousIntensity = system.intensity();
                double intensity = approach(previousIntensity, observation.intensity(), 0.44);
                WeatherSystemStage stage = lifecycle(previousIntensity, intensity);
                if (!system.type().severe() && observation.type().severe()) {
                    // A newly promoted severe identity must be observed again
                    // before entity wind begins, avoiding one-sample touchdown.
                    stage = WeatherSystemStage.FORMING;
                }
                next.add(new TrackedWeatherSystem(
                        system.id(),
                        observation.type(),
                        stage,
                        approach(predictedX, observation.centerX(), 0.48),
                        approach(predictedZ, observation.centerZ(), 0.48),
                        approach(system.radiusBlocks(), observation.radiusBlocks(), 0.32),
                        intensity,
                        WindVector.lerp(system.motion(), observation.motion(), 0.35),
                        approach(system.organization(), observation.organization(), 0.40),
                        saturatingAdd(system.ageTicks(), deltaTicks),
                        gameTick,
                        system.lastSplitTick()
                ));
            } else {
                double decay = controls.dissipationPerUpdate()
                        * Math.max(1.0, deltaTicks / (double) controls.nominalIntervalTicks());
                double intensity = Math.max(0.0, system.intensity() - decay);
                if (intensity >= controls.minimumRetainedIntensity()) {
                    next.add(new TrackedWeatherSystem(
                            system.id(),
                            system.type(),
                            WeatherSystemStage.WEAKENING,
                            predictedX,
                            predictedZ,
                            system.radiusBlocks(),
                            intensity,
                            system.motion(),
                            Math.max(0.0, system.organization() - decay * 0.5),
                            saturatingAdd(system.ageTicks(), deltaTicks),
                            gameTick,
                            system.lastSplitTick()
                    ));
                }
            }
        }

        for (int index = 0; index < observations.size(); index++) {
            Observation observation = observations.get(index);
            if (used[index] || observation.intensity() < controls.spawnIntensity()) {
                continue;
            }
            next.add(new TrackedWeatherSystem(
                    allocateId(),
                    observation.type(),
                    WeatherSystemStage.FORMING,
                    observation.centerX(),
                    observation.centerZ(),
                    observation.radiusBlocks(),
                    observation.intensity(),
                    observation.motion(),
                    observation.organization(),
                    0L,
                    gameTick,
                    0L
            ));
        }

        mergeOverlaps(next, gameTick, controls);
        splitOrganizedSystems(next, gameTick, controls);
        next.sort(Comparator
                .comparingDouble(TrackedWeatherSystem::intensity).reversed()
                .thenComparingLong(TrackedWeatherSystem::id));
        if (next.size() > controls.maximumSystems()) {
            next.subList(controls.maximumSystems(), next.size()).clear();
        }
        next.sort(Comparator.comparingLong(TrackedWeatherSystem::id));
        systems.clear();
        systems.addAll(next);
        return previousNextId != nextId || !before.equals(systems);
    }

    /** Returns combined persistent-system influence at a world position. */
    public SystemInfluence influenceAt(double blockX, double blockZ) {
        double storm = 0.0;
        double lift = 0.0;
        double pressureDrop = 0.0;
        double windX = 0.0;
        double windZ = 0.0;
        for (TrackedWeatherSystem system : systems) {
            double distance = Math.sqrt(system.distanceSquared(blockX, blockZ));
            if (distance >= system.radiusBlocks()) {
                continue;
            }
            double weight = smoothstep(system.radiusBlocks(), 0.0, distance) * system.intensity();
            if (system.type().compatibleWith(WeatherSystemType.STORM)) {
                storm += weight;
                lift += weight * (system.type().severe() ? 0.80 : 0.45);
                pressureDrop += weight * (system.type() == WeatherSystemType.CYCLONE ? 0.060 : 0.025);
                double dx = blockX - system.centerX();
                double dz = blockZ - system.centerZ();
                double length = Math.max(1.0, Math.hypot(dx, dz));
                double rotation = system.type() == WeatherSystemType.TORNADO ? 0.75 : 0.34;
                windX += (-dz / length) * weight * rotation + system.motion().x() * weight * 0.20;
                windZ += (dx / length) * weight * rotation + system.motion().z() * weight * 0.20;
            } else {
                lift += weight * 0.34;
                windX += system.motion().x() * weight * 0.12;
                windZ += system.motion().z() * weight * 0.12;
            }
        }
        return new SystemInfluence(storm, lift, pressureDrop, new WindVector(windX, windZ).limited(1.0));
    }

    private long allocateId() {
        long id = nextId;
        if (nextId < Long.MAX_VALUE) {
            nextId++;
        }
        return Math.max(1L, id);
    }

    private static List<Observation> deduplicate(List<Observation> source, double separation) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Observation> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingDouble(Observation::intensity).reversed());
        List<Observation> result = new ArrayList<>();
        double distanceSquared = separation * separation;
        for (Observation candidate : sorted) {
            boolean overlaps = false;
            for (Observation accepted : result) {
                if (candidate.type().compatibleWith(accepted.type())
                        && squaredDistance(candidate.centerX(), candidate.centerZ(),
                        accepted.centerX(), accepted.centerZ()) < distanceSquared) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static int nearestCompatible(
            TrackedWeatherSystem system,
            double predictedX,
            double predictedZ,
            List<Observation> observations,
            boolean[] used,
            TrackingSettings settings
    ) {
        int result = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int index = 0; index < observations.size(); index++) {
            Observation candidate = observations.get(index);
            if (used[index] || !system.type().compatibleWith(candidate.type())) {
                continue;
            }
            double maximum = Math.max(
                    settings.matchDistanceBlocks(),
                    system.radiusBlocks() + candidate.radiusBlocks()
            );
            double distance = squaredDistance(predictedX, predictedZ, candidate.centerX(), candidate.centerZ());
            if (distance <= maximum * maximum && distance < best) {
                best = distance;
                result = index;
            }
        }
        return result;
    }

    private void mergeOverlaps(
            List<TrackedWeatherSystem> mutable,
            long gameTick,
            TrackingSettings settings
    ) {
        Set<Integer> removed = new HashSet<>();
        for (int first = 0; first < mutable.size(); first++) {
            if (removed.contains(first)) {
                continue;
            }
            TrackedWeatherSystem left = mutable.get(first);
            for (int second = first + 1; second < mutable.size(); second++) {
                if (removed.contains(second)) {
                    continue;
                }
                TrackedWeatherSystem right = mutable.get(second);
                if (!left.type().compatibleWith(right.type())) {
                    continue;
                }
                double threshold = Math.min(left.radiusBlocks(), right.radiusBlocks())
                        * settings.mergeRadiusMultiplier();
                if (left.distanceSquared(right.centerX(), right.centerZ()) > threshold * threshold) {
                    continue;
                }
                double total = Math.max(0.001, left.intensity() + right.intensity());
                double rightWeight = right.intensity() / total;
                WeatherSystemType mergedType = left.intensity() >= right.intensity()
                        ? left.type() : right.type();
                left = new TrackedWeatherSystem(
                        Math.min(left.id(), right.id()),
                        mergedType,
                        WeatherSystemStage.MATURE,
                        approach(left.centerX(), right.centerX(), rightWeight),
                        approach(left.centerZ(), right.centerZ(), rightWeight),
                        Math.min(8_192.0, Math.max(left.radiusBlocks(), right.radiusBlocks())
                                + Math.min(left.radiusBlocks(), right.radiusBlocks()) * 0.25),
                        unit(Math.max(left.intensity(), right.intensity())
                                + Math.min(left.intensity(), right.intensity()) * 0.30),
                        WindVector.lerp(left.motion(), right.motion(), rightWeight),
                        Math.max(left.organization(), right.organization()),
                        Math.max(left.ageTicks(), right.ageTicks()),
                        gameTick,
                        Math.max(left.lastSplitTick(), right.lastSplitTick())
                );
                mutable.set(first, left);
                removed.add(second);
            }
        }
        if (!removed.isEmpty()) {
            List<TrackedWeatherSystem> compact = new ArrayList<>(mutable.size() - removed.size());
            for (int index = 0; index < mutable.size(); index++) {
                if (!removed.contains(index)) {
                    compact.add(mutable.get(index));
                }
            }
            mutable.clear();
            mutable.addAll(compact);
        }
    }

    private void splitOrganizedSystems(
            List<TrackedWeatherSystem> mutable,
            long gameTick,
            TrackingSettings settings
    ) {
        if (!settings.splittingEnabled() || mutable.size() >= settings.maximumSystems()) {
            return;
        }
        List<TrackedWeatherSystem> children = new ArrayList<>();
        for (int index = 0; index < mutable.size(); index++) {
            TrackedWeatherSystem parent = mutable.get(index);
            if (!parent.type().compatibleWith(WeatherSystemType.STORM)
                    || parent.intensity() < settings.splitIntensity()
                    || parent.organization() < settings.splitOrganization()
                    || gameTick - parent.lastSplitTick() < settings.splitCooldownTicks()
                    || !deterministicSplit(parent.id(), gameTick, settings.nominalIntervalTicks())) {
                continue;
            }
            double length = Math.max(0.001, parent.motion().magnitude());
            double perpendicularX = -parent.motion().z() / length;
            double perpendicularZ = parent.motion().x() / length;
            if (length <= 0.01) {
                perpendicularX = ((parent.id() & 1L) == 0L) ? 1.0 : -1.0;
                perpendicularZ = 0.0;
            }
            double offset = parent.radiusBlocks() * 0.42;
            double childIntensity = parent.intensity() * 0.56;
            TrackedWeatherSystem weakenedParent = new TrackedWeatherSystem(
                    parent.id(),
                    parent.type(),
                    WeatherSystemStage.WEAKENING,
                    parent.centerX() - perpendicularX * offset * 0.22,
                    parent.centerZ() - perpendicularZ * offset * 0.22,
                    parent.radiusBlocks() * 0.82,
                    parent.intensity() * 0.72,
                    parent.motion(),
                    parent.organization() * 0.82,
                    parent.ageTicks(),
                    gameTick,
                    gameTick
            );
            mutable.set(index, weakenedParent);
            children.add(new TrackedWeatherSystem(
                    allocateId(),
                    parent.type() == WeatherSystemType.TORNADO ? WeatherSystemType.STORM : parent.type(),
                    WeatherSystemStage.FORMING,
                    parent.centerX() + perpendicularX * offset,
                    parent.centerZ() + perpendicularZ * offset,
                    parent.radiusBlocks() * 0.62,
                    childIntensity,
                    new WindVector(
                            parent.motion().x() + perpendicularX * 0.18,
                            parent.motion().z() + perpendicularZ * 0.18
                    ).limited(1.0),
                    parent.organization() * 0.74,
                    0L,
                    gameTick,
                    gameTick
            ));
            if (mutable.size() + children.size() >= settings.maximumSystems()) {
                break;
            }
        }
        mutable.addAll(children);
    }

    private static WeatherSystemStage lifecycle(double previous, double current) {
        if (current >= 0.56 && current >= previous - 0.015) {
            return WeatherSystemStage.MATURE;
        }
        return current > previous + 0.02 ? WeatherSystemStage.FORMING : WeatherSystemStage.WEAKENING;
    }

    private static boolean deterministicSplit(long id, long gameTick, int interval) {
        long window = gameTick / Math.max(1, interval);
        long mixed = id * 0x9E3779B97F4A7C15L ^ window * 0xC2B2AE3D27D4EB4FL;
        mixed ^= mixed >>> 29;
        return (mixed & 3L) == 0L;
    }

    private static double squaredDistance(double x0, double z0, double x1, double z1) {
        double x = x1 - x0;
        double z = z1 - z0;
        return x * x + z * z;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double amount = unit((value - edge0) / (edge1 - edge0));
        return amount * amount * (3.0 - 2.0 * amount);
    }

    private static double approach(double from, double to, double amount) {
        return from + (to - from) * unit(amount);
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }

    private static long saturatingAdd(long value, long addition) {
        if (Long.MAX_VALUE - value < addition) {
            return Long.MAX_VALUE;
        }
        return value + addition;
    }

    /** One atmospheric-cell observation offered to identity tracking. */
    public record Observation(
            WeatherSystemType type,
            double centerX,
            double centerZ,
            double radiusBlocks,
            double intensity,
            WindVector motion,
            double organization
    ) {
        public Observation {
            type = Objects.requireNonNullElse(type, WeatherSystemType.STORM);
            centerX = Math.max(-30_000_000.0, Math.min(30_000_000.0,
                    Double.isFinite(centerX) ? centerX : 0.0));
            centerZ = Math.max(-30_000_000.0, Math.min(30_000_000.0,
                    Double.isFinite(centerZ) ? centerZ : 0.0));
            radiusBlocks = Math.max(16.0, Math.min(8_192.0,
                    Double.isFinite(radiusBlocks) ? radiusBlocks : 16.0));
            intensity = unit(intensity);
            motion = Objects.requireNonNullElse(motion, WindVector.ZERO).limited(1.0);
            organization = unit(organization);
        }
    }

    /** Combined continuous feedback from persistent identities. */
    public record SystemInfluence(
            double stormBoost,
            double lift,
            double pressureDrop,
            WindVector wind
    ) {
        public static final SystemInfluence NONE = new SystemInfluence(0.0, 0.0, 0.0, WindVector.ZERO);

        public SystemInfluence {
            stormBoost = unit(stormBoost);
            lift = unit(lift);
            pressureDrop = Math.max(0.0, Math.min(0.15,
                    Double.isFinite(pressureDrop) ? pressureDrop : 0.0));
            wind = Objects.requireNonNullElse(wind, WindVector.ZERO).limited(1.0);
        }
    }

    /** Clamp-safe lifecycle and motion controls. */
    public record TrackingSettings(
            boolean enabled,
            int maximumSystems,
            int nominalIntervalTicks,
            double movementBlocksPerSecond,
            double observationSeparationBlocks,
            double matchDistanceBlocks,
            double spawnIntensity,
            double minimumRetainedIntensity,
            double dissipationPerUpdate,
            double mergeRadiusMultiplier,
            boolean splittingEnabled,
            double splitIntensity,
            double splitOrganization,
            int splitCooldownTicks
    ) {
        public static final TrackingSettings DEFAULT = new TrackingSettings(
                true, 48, 60, 3.0, 220.0, 520.0, 0.28, 0.08,
                0.035, 0.58, true, 0.86, 0.62, 6_000
        );

        public TrackingSettings {
            maximumSystems = Math.max(1, Math.min(256, maximumSystems));
            nominalIntervalTicks = Math.max(10, Math.min(1_200, nominalIntervalTicks));
            movementBlocksPerSecond = Math.max(0.0, Math.min(32.0, movementBlocksPerSecond));
            observationSeparationBlocks = Math.max(16.0, Math.min(4_096.0, observationSeparationBlocks));
            matchDistanceBlocks = Math.max(16.0, Math.min(8_192.0, matchDistanceBlocks));
            spawnIntensity = unit(spawnIntensity);
            minimumRetainedIntensity = unit(minimumRetainedIntensity);
            dissipationPerUpdate = unit(dissipationPerUpdate);
            mergeRadiusMultiplier = Math.max(0.1, Math.min(2.0, mergeRadiusMultiplier));
            splitIntensity = unit(splitIntensity);
            splitOrganization = unit(splitOrganization);
            splitCooldownTicks = Math.max(20, Math.min(1_728_000, splitCooldownTicks));
        }
    }
}
