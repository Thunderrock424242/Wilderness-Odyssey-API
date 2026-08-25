package com.thunder.wildernessodysseyapi.ecosystem.distant;

import com.thunder.wildernessodysseyapi.ecosystem.simulation.AbstractEcosystemModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * One persisted, group-level population that replaces many ticking entities.
 *
 * <p>The anchor, direction, reference time, and deterministic seed are enough
 * for clients to animate the entire group between infrequent snapshots. The
 * server never creates one simulation object per represented animal.</p>
 */
public record DistantWildlifeGroup(
        long id,
        ResourceLocation species,
        int populationEstimate,
        double populationRemainder,
        double anchorX,
        double anchorY,
        double anchorZ,
        double directionX,
        double directionZ,
        double cruiseSpeed,
        double activityScale,
        long seed,
        long referenceGameTime,
        long populationReferenceGameTime,
        double foodAvailability,
        double waterAvailability,
        double foodPressure,
        double disturbance,
        double weatherImpact,
        DistantWildlifeForm form,
        boolean nocturnal,
        boolean weatherSensitive
) {
    public static final int MAXIMUM_GROUP_POPULATION = 64;
    private static final double MAXIMUM_WORLD_COORDINATE = 30_000_000.0;

    public DistantWildlifeGroup {
        if (id <= 0L) {
            throw new IllegalArgumentException("Distant wildlife group id must be positive");
        }
        species = Objects.requireNonNull(species, "species");
        if (populationEstimate <= 0 || populationEstimate > MAXIMUM_GROUP_POPULATION) {
            throw new IllegalArgumentException("Invalid distant wildlife population: " + populationEstimate);
        }
        if (!Double.isFinite(populationRemainder)
                || populationRemainder < -0.5
                || populationRemainder > 0.5) {
            throw new IllegalArgumentException("Invalid distant wildlife population remainder: " + populationRemainder);
        }
        requireCoordinate(anchorX, "anchorX");
        requireCoordinate(anchorY, "anchorY");
        requireCoordinate(anchorZ, "anchorZ");
        if (!Double.isFinite(directionX) || !Double.isFinite(directionZ)) {
            throw new IllegalArgumentException("Distant wildlife direction must be finite");
        }
        double directionLength = Math.hypot(directionX, directionZ);
        if (directionLength < 1.0E-6) {
            directionX = 1.0;
            directionZ = 0.0;
        } else {
            directionX /= directionLength;
            directionZ /= directionLength;
        }
        if (!Double.isFinite(cruiseSpeed) || cruiseSpeed < 0.0 || cruiseSpeed > 4.0) {
            throw new IllegalArgumentException("Invalid distant wildlife cruise speed: " + cruiseSpeed);
        }
        if (!Double.isFinite(activityScale) || activityScale < 0.0 || activityScale > 2.0) {
            throw new IllegalArgumentException("Invalid distant wildlife activity scale: " + activityScale);
        }
        if (referenceGameTime < 0L) {
            throw new IllegalArgumentException("Distant wildlife reference time cannot be negative");
        }
        populationReferenceGameTime = Math.max(0L, populationReferenceGameTime);
        AbstractEcosystemModel.Environment environment = new AbstractEcosystemModel.Environment(
                foodAvailability, waterAvailability, foodPressure, disturbance, weatherImpact
        );
        foodAvailability = environment.foodAvailability();
        waterAvailability = environment.waterAvailability();
        foodPressure = environment.foodPressure();
        disturbance = environment.disturbance();
        weatherImpact = environment.weatherImpact();
        form = Objects.requireNonNullElse(form, DistantWildlifeForm.GROUND);
    }

    /** Retains the data-version-two construction shape without a fractional remainder. */
    public DistantWildlifeGroup(
            long id,
            ResourceLocation species,
            int populationEstimate,
            double anchorX,
            double anchorY,
            double anchorZ,
            double directionX,
            double directionZ,
            double cruiseSpeed,
            double activityScale,
            long seed,
            long referenceGameTime,
            long populationReferenceGameTime,
            double foodAvailability,
            double waterAvailability,
            double foodPressure,
            double disturbance,
            double weatherImpact,
            DistantWildlifeForm form,
            boolean nocturnal,
            boolean weatherSensitive
    ) {
        this(
                id, species, populationEstimate, 0.0,
                anchorX, anchorY, anchorZ,
                directionX, directionZ,
                cruiseSpeed, activityScale,
                seed, referenceGameTime, populationReferenceGameTime,
                foodAvailability, waterAvailability, foodPressure, disturbance, weatherImpact,
                form, nocturnal, weatherSensitive
        );
    }

    /** Retains the original construction shape for integrations compiled against data version one. */
    public DistantWildlifeGroup(
            long id,
            ResourceLocation species,
            int populationEstimate,
            double anchorX,
            double anchorY,
            double anchorZ,
            double directionX,
            double directionZ,
            double cruiseSpeed,
            double activityScale,
            long seed,
            long referenceGameTime,
            DistantWildlifeForm form,
            boolean nocturnal,
            boolean weatherSensitive
    ) {
        this(
                id, species, populationEstimate,
                anchorX, anchorY, anchorZ,
                directionX, directionZ,
                cruiseSpeed, activityScale,
                seed, referenceGameTime, referenceGameTime,
                AbstractEcosystemModel.Environment.NEUTRAL.foodAvailability(),
                AbstractEcosystemModel.Environment.NEUTRAL.waterAvailability(),
                AbstractEcosystemModel.Environment.NEUTRAL.foodPressure(),
                AbstractEcosystemModel.Environment.NEUTRAL.disturbance(),
                AbstractEcosystemModel.Environment.NEUTRAL.weatherImpact(),
                form, nocturnal, weatherSensitive
        );
    }

    /** Returns the group-level speed in blocks per real-time second. */
    public double speed() {
        return cruiseSpeed * activityScale;
    }

    /** Analytically advances the group without a tick-side pathfinder. */
    public Vec3 positionAt(double gameTime) {
        double elapsedSeconds = Math.max(0.0, gameTime - referenceGameTime) / 20.0;
        double distance = speed() * elapsedSeconds;
        return new Vec3(
                anchorX + directionX * distance,
                anchorY,
                anchorZ + directionZ * distance
        );
    }

    /** Re-anchors movement after one infrequent server policy evaluation. */
    public DistantWildlifeGroup withMotion(
            Vec3 anchor,
            double newDirectionX,
            double newDirectionZ,
            double newActivityScale,
            long gameTime
    ) {
        Objects.requireNonNull(anchor, "anchor");
        return new DistantWildlifeGroup(
                id, species, populationEstimate, populationRemainder,
                anchor.x, anchor.y, anchor.z,
                newDirectionX, newDirectionZ,
                cruiseSpeed, newActivityScale,
                seed, gameTime, populationReferenceGameTime,
                foodAvailability, waterAvailability, foodPressure, disturbance, weatherImpact,
                form, nocturnal, weatherSensitive
        );
    }

    /** Replaces only the owned population count after a committed transition. */
    public DistantWildlifeGroup withPopulation(int population) {
        return new DistantWildlifeGroup(
                id, species, population, populationRemainder,
                anchorX, anchorY, anchorZ,
                directionX, directionZ,
                cruiseSpeed, activityScale,
                seed, referenceGameTime, populationReferenceGameTime,
                foodAvailability, waterAvailability, foodPressure, disturbance, weatherImpact,
                form, nocturnal, weatherSensitive
        );
    }

    /**
     * Applies one analytic population/environment update after any elapsed dormant interval.
     *
     * <p>The call is O(1) regardless of elapsed time and never creates
     * individual animal simulation objects.</p>
     */
    public DistantWildlifeGroup withLazyPopulationUpdate(
            AbstractEcosystemModel.Environment observedEnvironment,
            long gameTime
    ) {
        long safeTime = Math.max(populationReferenceGameTime, gameTime);
        long elapsed = safeTime - populationReferenceGameTime;
        AbstractEcosystemModel.Environment previous = new AbstractEcosystemModel.Environment(
                foodAvailability, waterAvailability, foodPressure, disturbance, weatherImpact
        );
        AbstractEcosystemModel.PopulationState population = AbstractEcosystemModel.advancePopulationState(
                populationEstimate,
                populationRemainder,
                elapsed,
                previous
        );
        AbstractEcosystemModel.Environment advanced = AbstractEcosystemModel.advanceEnvironment(
                previous, elapsed, population.population()
        );
        AbstractEcosystemModel.Environment observed = observedEnvironment == null ? advanced : observedEnvironment;
        AbstractEcosystemModel.Environment merged = new AbstractEcosystemModel.Environment(
                (advanced.foodAvailability() + observed.foodAvailability()) * 0.5,
                (advanced.waterAvailability() + observed.waterAvailability()) * 0.5,
                Math.max(advanced.foodPressure(), observed.foodPressure()),
                Math.max(advanced.disturbance(), observed.disturbance()),
                Math.max(advanced.weatherImpact(), observed.weatherImpact())
        );
        int boundedPopulation = Math.max(1, Math.min(MAXIMUM_GROUP_POPULATION, population.population()));
        double boundedRemainder = boundedPopulation == population.population() ? population.remainder() : 0.0;
        return new DistantWildlifeGroup(
                id, species, boundedPopulation, boundedRemainder,
                anchorX, anchorY, anchorZ,
                directionX, directionZ,
                cruiseSpeed, activityScale,
                seed, referenceGameTime, safeTime,
                merged.foodAvailability(), merged.waterAvailability(), merged.foodPressure(),
                merged.disturbance(), merged.weatherImpact(),
                form, nocturnal, weatherSensitive
        );
    }

    /** Applies a validated ecology result while preserving current group motion. */
    public DistantWildlifeGroup withPopulationEcologyState(
            int population,
            double remainder,
            long populationGameTime,
            AbstractEcosystemModel.Environment environment
    ) {
        Objects.requireNonNull(environment, "environment");
        return new DistantWildlifeGroup(
                id, species, population, remainder,
                anchorX, anchorY, anchorZ,
                directionX, directionZ,
                cruiseSpeed, activityScale,
                seed, referenceGameTime, populationGameTime,
                environment.foodAvailability(), environment.waterAvailability(), environment.foodPressure(),
                environment.disturbance(), environment.weatherImpact(),
                form, nocturnal, weatherSensitive
        );
    }

    /** Merges one eligible real animal into this group at the current reference time. */
    public DistantWildlifeGroup absorb(Vec3 position, double sourceCruiseSpeed, long gameTime) {
        if (populationEstimate >= MAXIMUM_GROUP_POPULATION) {
            return this;
        }
        Vec3 current = positionAt(gameTime);
        int newPopulation = populationEstimate + 1;
        double existingWeight = populationEstimate / (double) newPopulation;
        double sourceWeight = 1.0 / newPopulation;
        Vec3 mergedAnchor = new Vec3(
                current.x * existingWeight + position.x * sourceWeight,
                current.y * existingWeight + position.y * sourceWeight,
                current.z * existingWeight + position.z * sourceWeight
        );
        double mergedCruiseSpeed = cruiseSpeed * existingWeight + sourceCruiseSpeed * sourceWeight;
        return new DistantWildlifeGroup(
                id, species, newPopulation, populationRemainder,
                mergedAnchor.x, mergedAnchor.y, mergedAnchor.z,
                directionX, directionZ,
                mergedCruiseSpeed, activityScale,
                seed, gameTime, populationReferenceGameTime,
                foodAvailability, waterAvailability, foodPressure, disturbance, weatherImpact,
                form, nocturnal, weatherSensitive
        );
    }

    private static void requireCoordinate(double coordinate, String name) {
        if (!Double.isFinite(coordinate) || Math.abs(coordinate) > MAXIMUM_WORLD_COORDINATE) {
            throw new IllegalArgumentException("Invalid distant wildlife " + name + ": " + coordinate);
        }
    }
}
