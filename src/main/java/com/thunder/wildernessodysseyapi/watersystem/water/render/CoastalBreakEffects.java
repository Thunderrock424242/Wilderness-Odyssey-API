package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.item.ModSoundEvents;
import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalBreakAudioModel;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSegment;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalSeasonModel;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveModel;
import com.thunder.wildernessodysseyapi.watersystem.ocean.coast.CoastalWaveBreakEvent;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Emits one sparse local particle/audio accent when a cached segment breaks. */
public final class CoastalBreakEffects {

    private static final int MINIMUM_EFFECT_INTERVAL_TICKS = 28;
    private static final float VISUAL_TIDE_SCALE = 0.18f;
    private static final Map<Long, Long> LAST_EMITTED_CYCLE = new HashMap<>();
    private static long nextEffectTick;
    private static long nextCleanupTick;
    private static volatile CoastalWaveBreakEvent lastBreak;
    private static volatile long lastBreakTick = Long.MIN_VALUE;

    private CoastalBreakEffects() {
    }

    /** Samples cached deterministic waves and accents at most one breaker per interval. */
    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null
                || !WaterRenderingConfig.coastalWavesEnabled(level)) {
            clear();
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime >= nextCleanupTick) {
            retainActiveSegments(level);
            nextCleanupTick = gameTime + 200L;
        }
        if (gameTime < nextEffectTick) {
            return;
        }

        Candidate best = null;
        for (CoastalSegment segment : ClientCoastalSegmentStore.segments(level)) {
            OceanSeaState.Sample sea = WaterRenderingConfig.coastalWeatherInfluenceEnabled()
                    ? ClientOceanSeaState.sampleAt(level, segment.centerX(), segment.centerZ())
                    : OceanSeaState.CALM;
            float onshoreWind = sea.windDirectionX() * segment.landwardNormalX()
                    + sea.windDirectionZ() * segment.landwardNormalZ();
            CoastalWaveModel.Sample wave = CoastalWaveModel.sample(
                    segment.id(), gameTime, 0.0f, segment.profile(), sea,
                    segment.averageBeachSlope(),
                    segment.underwaterSlope(),
                    segment.averageWaterDepth(),
                    onshoreWind);
            boolean audibleBreak = WaterRenderingConfig.coastalAudioEnabled(level)
                    && segment.profile().crashSoundVolume() > 0.0f
                    && CoastalBreakAudioModel.isAudibleBreak(wave);
            boolean sprayBreak = WaterRenderingConfig.coastalSprayEnabled(level)
                    && CoastalBreakAudioModel.isAudibleBreak(wave)
                    && wave.spray() >= 0.12f;
            if (wave.stage() != CoastalWaveModel.Stage.BREAKING
                    || (!audibleBreak && !sprayBreak)
                    || LAST_EMITTED_CYCLE.getOrDefault(segment.id(), Long.MIN_VALUE)
                    == wave.cycleIndex()
                    || segment.shoreline().isEmpty()) {
                continue;
            }
            double deltaX = segment.centerX() + 0.5 - minecraft.player.getX();
            double deltaZ = segment.centerZ() + 0.5 - minecraft.player.getZ();
            double distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
            float radius = segment.profile().crashSoundRadiusBlocks();
            if (distanceSquared > radius * radius) {
                continue;
            }
            float score = CoastalBreakAudioModel.impactStrength(wave)
                    / (1.0f + (float) Math.sqrt(distanceSquared) * 0.04f);
            if (best == null || score > best.score()) {
                best = new Candidate(segment, wave, sea.strength(), distanceSquared, score);
            }
        }

        if (best == null) {
            return;
        }
        emit(level, best);
        LAST_EMITTED_CYCLE.put(best.segment().id(), best.wave().cycleIndex());
        long cadenceVariation = Math.floorMod(
                best.segment().id() ^ best.wave().cycleIndex(), 9L);
        nextEffectTick = gameTime + MINIMUM_EFFECT_INTERVAL_TICKS + cadenceVariation;
    }

    /** Clears per-level cosmetic cadence state. */
    public static void clear() {
        LAST_EMITTED_CYCLE.clear();
        nextEffectTick = 0L;
        nextCleanupTick = 0L;
        lastBreak = null;
        lastBreakTick = Long.MIN_VALUE;
    }

    /** Returns the latest local break selected for effects and diagnostics. */
    public static Optional<CoastalWaveBreakEvent> lastBreak() {
        return Optional.ofNullable(lastBreak);
    }

    /** Returns the latest breaker only while it remains an active debug event. */
    public static Optional<CoastalWaveBreakEvent> activeBreak(
            ClientLevel level,
            long maximumAgeTicks
    ) {
        CoastalWaveBreakEvent event = lastBreak;
        long age = level == null || lastBreakTick == Long.MIN_VALUE
                ? Long.MAX_VALUE : level.getGameTime() - lastBreakTick;
        return event != null && age >= 0L && age <= Math.max(0L, maximumAgeTicks)
                ? Optional.of(event) : Optional.empty();
    }

    private static void emit(ClientLevel level, Candidate candidate) {
        CoastalSegment segment = candidate.segment();
        CoastalWaveModel.Sample wave = candidate.wave();
        CoastalSeasonModel.Sample season = ClientCoastalClimate.sample(level, segment);
        int pointIndex = Math.floorMod(
                segment.id() ^ wave.cycleIndex(), segment.shoreline().size());
        CoastalSegment.ShorelinePoint point = segment.shoreline().get(pointIndex);
        CoastalSegment.NearshoreCell breakerCell = closestNearshoreCell(
                point, wave.breakerDistanceBlocks());
        double x = breakerCell.blockX() + 0.5
                + segment.landwardNormalX() * 0.18;
        double y = breakerCell.waterSurfaceY()
                + TideSystem.getTideOffset(level) * VISUAL_TIDE_SCALE
                + Math.max(0.04, wave.breakerLift() * 0.55);
        double z = breakerCell.blockZ() + 0.5
                + segment.landwardNormalZ() * 0.18;
        CoastalWaveBreakEvent breakEvent = new CoastalWaveBreakEvent(
                segment.id(),
                wave.cycleIndex(),
                x,
                y,
                z,
                CoastalBreakAudioModel.impactStrength(wave),
                wave.waveHeight(),
                segment.profile().shoreType(),
                candidate.weatherIntensity()
        );
        lastBreak = breakEvent;
        lastBreakTick = level.getGameTime();

        double sprayDistance = WaterRenderingConfig.coastalSprayDistanceBlocks();
        if (WaterRenderingConfig.coastalSprayEnabled(level)
                && wave.spray() >= 0.055f
                && candidate.distanceSquared() <= sprayDistance * sprayDistance) {
            int particles = WaterRenderingConfig.coastalSprayParticleBudget();
            float steepImpact = Math.min(1.0f, segment.averageBeachSlope() / 0.85f);
            for (int index = 0; index < particles; index++) {
                double tangent = (index - (particles - 1) * 0.5) * 0.12;
                level.addParticle(
                        index % 3 == 2 ? ParticleTypes.CLOUD : ParticleTypes.SPLASH,
                        x - segment.landwardNormalZ() * tangent,
                        y,
                        z + segment.landwardNormalX() * tangent,
                        segment.landwardNormalX() * (0.04 + wave.spray() * 0.10),
                        0.08 + wave.spray() * 0.18
                                + steepImpact * 0.26 + index * 0.015,
                        segment.landwardNormalZ() * (0.04 + wave.spray() * 0.10)
                );
            }
            WaterEnvironmentalEffectPool.offer(
                    WaterEnvironmentalEffectPool.Kind.FOAM,
                    breakEvent.x(), breakerCell.waterSurfaceY(), breakEvent.z(),
                    segment.landwardNormalX() * 0.025,
                    segment.landwardNormalZ() * 0.025,
                    wave.foam(),
                    level.getGameTime()
            );
            if (wave.spray() >= 0.48f || season.mist() >= 0.24f) {
                WaterEnvironmentalEffectPool.offer(
                        WaterEnvironmentalEffectPool.Kind.MIST,
                        breakEvent.x(), breakEvent.y(), breakEvent.z(),
                        segment.landwardNormalX() * 0.02,
                        segment.landwardNormalZ() * 0.02,
                        Math.min(1.0f, wave.spray() * 0.72f + season.mist() * 0.34f),
                        level.getGameTime()
                );
            }
        }

        if (WaterRenderingConfig.coastalAudioEnabled(level)) {
            CoastalBreakAudioModel.Mix mix = CoastalBreakAudioModel.mix(
                    segment.profile(), wave, WaterRenderingConfig.coastalSoundVolume(), segment.id());
            level.playLocalSound(
                    breakEvent.x(),
                    breakEvent.y(),
                    breakEvent.z(),
                    soundFor(segment.profile().shoreType(), wave.energy()),
                    SoundSource.AMBIENT,
                    mix.impactVolume(),
                    mix.pitch(),
                    false
            );
            // sounds.json entries are random alternatives, not simultaneous
            // layers. Add the quieter water body explicitly at the same crest.
            level.playLocalSound(
                    breakEvent.x(),
                    breakEvent.y(),
                    breakEvent.z(),
                    SoundEvents.WATER_AMBIENT,
                    SoundSource.AMBIENT,
                    mix.washVolume(),
                    mix.pitch() * 0.90f,
                    false
            );
        }
    }

    private static SoundEvent soundFor(
            CoastalWaveProfile.ShoreType shoreType,
            float energy
    ) {
        if (energy >= 0.78f) {
            return ModSoundEvents.COAST_BREAK_STORM.get();
        }
        if (shoreType == CoastalWaveProfile.ShoreType.ROCKY
                || shoreType == CoastalWaveProfile.ShoreType.GLACIAL) {
            return ModSoundEvents.COAST_BREAK_ROCKY.get();
        }
        return energy < 0.34f
                ? ModSoundEvents.COAST_WASH_SOFT.get()
                : ModSoundEvents.COAST_BREAK.get();
    }

    private static void retainActiveSegments(ClientLevel level) {
        Set<Long> active = new HashSet<>();
        for (CoastalSegment segment : ClientCoastalSegmentStore.segments(level)) {
            active.add(segment.id());
        }
        LAST_EMITTED_CYCLE.keySet().retainAll(active);
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
        float closestDifference = Math.abs(closest.distanceFromShoreBlocks() - targetDistance);
        for (int index = 1; index < point.nearshoreCells().size(); index++) {
            CoastalSegment.NearshoreCell candidate = point.nearshoreCells().get(index);
            float difference = Math.abs(candidate.distanceFromShoreBlocks() - targetDistance);
            if (difference < closestDifference) {
                closest = candidate;
                closestDifference = difference;
            }
        }
        return closest;
    }

    private record Candidate(
            CoastalSegment segment,
            CoastalWaveModel.Sample wave,
            float weatherIntensity,
            double distanceSquared,
            float score
    ) {
    }
}
