package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persists local volumetric-water bodies inside each server dimension.
 *
 * <p>The logical server owns this data. Clients receive bounded snapshots and
 * never save their interpolated mirrors. Raw particle state is used for this
 * mobile representation so a pour resumes without changing volume. Settled
 * bodies leave this store and materialize into sparse chunk volume cells.</p>
 */
public final class SphWaterSavedData extends SavedData {

    static final int FORMAT_VERSION = 1;

    private static final String DATA_NAME = ModConstants.MOD_ID + "_sph_water";
    private static final String FORMAT_KEY = "format_version";
    private static final String SIMULATION_COUNT_KEY = "simulation_count";
    private static final String SIMULATIONS_KEY = "simulations";
    private static final String PARTICLES_KEY = "particles";
    private static final String PARTICLE_DATA_KEY = "particle_data";
    private static final String PARTICLE_COUNT_KEY = "particle_count";
    private static final String VOLUME_UNITS_KEY = "volume_units";
    private static final int PARTICLE_DATA_STRIDE = 8;

    private final List<StoredSimulation> simulations = new ArrayList<>();

    /**
     * Gets the dimension-owned SavedData instance.
     *
     * @param level server dimension
     * @return loaded or newly-created SPH water data
     */
    public static SphWaterSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(SphWaterSavedData::new, SphWaterSavedData::load),
                DATA_NAME
        );
    }

    // Package-private for strict codec tests without constructing dimension storage.
    static SphWaterSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int format = tag.contains(FORMAT_KEY, Tag.TAG_INT) ? tag.getInt(FORMAT_KEY) : 0;
        if (format < 0 || format > FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported SPH water format " + format);
        }

        SphWaterSavedData data = new SphWaterSavedData();
        ListTag storedSimulations = tag.getList(SIMULATIONS_KEY, Tag.TAG_COMPOUND);
        int simulationCount = storedSimulations.size();
        if (simulationCount > SPHConstants.MAX_ACTIVE_SIMULATIONS) {
            throw new IllegalArgumentException("SPH water save exceeds active simulation limit");
        }
        if (format > 0 && (!tag.contains(SIMULATION_COUNT_KEY, Tag.TAG_INT)
                || tag.getInt(SIMULATION_COUNT_KEY) != simulationCount)) {
            throw new IllegalArgumentException("SPH water simulation count does not match payload");
        }

        Set<UUID> simulationIds = new HashSet<>(simulationCount);
        for (int i = 0; i < simulationCount; i++) {
            CompoundTag simulationTag = storedSimulations.getCompound(i);
            UUID simulationId = new UUID(
                    simulationTag.getLong("id_most"),
                    simulationTag.getLong("id_least")
            );
            if (!simulationIds.add(simulationId)) {
                throw new IllegalArgumentException("Duplicate SPH water simulation " + simulationId);
            }
            List<SPHParticle> particles = loadParticles(simulationTag, format);
            if (!particles.isEmpty()) {
                int volumeUnits = simulationTag.contains(VOLUME_UNITS_KEY, Tag.TAG_INT)
                        ? simulationTag.getInt(VOLUME_UNITS_KEY)
                        : WaterVolumeChunk.UNITS_PER_BLOCK;
                if (volumeUnits < 0) {
                    throw new IllegalArgumentException("Negative SPH water volume for " + simulationId);
                }
                data.simulations.add(new StoredSimulation(simulationId, particles, volumeUnits));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(FORMAT_KEY, FORMAT_VERSION);
        tag.putInt(SIMULATION_COUNT_KEY, simulations.size());
        ListTag storedSimulations = new ListTag();
        for (StoredSimulation simulation : simulations) {
            CompoundTag simulationTag = new CompoundTag();
            simulationTag.putLong("id_most", simulation.simulationId.getMostSignificantBits());
            simulationTag.putLong("id_least", simulation.simulationId.getLeastSignificantBits());
            simulationTag.putInt(VOLUME_UNITS_KEY, simulation.volumeUnits);

            simulationTag.putInt(PARTICLE_COUNT_KEY, simulation.particles.size());
            simulationTag.putIntArray(PARTICLE_DATA_KEY, saveParticles(simulation.particles));
            storedSimulations.add(simulationTag);
        }
        tag.put(SIMULATIONS_KEY, storedSimulations);
        return tag;
    }

    /**
     * Replaces persisted state with the current non-transient server bodies.
     *
     * @param activeSimulations bodies currently owned by this dimension
     */
    public void capture(List<SPHSimulator> activeSimulations) {
        simulations.clear();
        for (SPHSimulator simulator : activeSimulations) {
            if (simulator.isRemoteMirror() || simulator.isTransientSimulation()) {
                continue;
            }

            List<SPHParticle> particles = new ArrayList<>(simulator.particleCount());
            for (SPHParticle particle : simulator.getRenderParticles()) {
                particles.add(new SPHParticle(particle));
            }
            if (!particles.isEmpty()) {
                simulations.add(new StoredSimulation(
                        simulator.getSimulationId(),
                        particles,
                        simulator.getCanonicalVolumeUnits()
                ));
            }
        }
        setDirty();
    }

    /**
     * Rebuilds authoritative simulators after a dimension loads.
     *
     * @param manager runtime simulation owner
     * @param level dimension receiving the restored bodies
     */
    public void restoreInto(SPHSimulationManager manager, ServerLevel level) {
        for (StoredSimulation simulation : simulations) {
            manager.restorePersistentSimulation(
                    simulation.simulationId,
                    level,
                    simulation.particles,
                    simulation.volumeUnits
            );
        }
    }

    private static SPHParticle loadParticle(CompoundTag tag) {
        float x = tag.getFloat("x");
        float y = tag.getFloat("y");
        float z = tag.getFloat("z");
        float velocityX = tag.getFloat("vx");
        float velocityY = tag.getFloat("vy");
        float velocityZ = tag.getFloat("vz");
        validateFiniteParticle(x, y, z, velocityX, velocityY, velocityZ);
        SPHParticle particle = new SPHParticle(x, y, z);
        particle.velocity.set(velocityX, velocityY, velocityZ);
        particle.isDroplet = tag.getBoolean("droplet");
        particle.dropletLife = tag.getInt("droplet_life");
        return particle;
    }

    // Primitive arrays avoid the heavy per-particle CompoundTag overhead while
    // preserving exact IEEE-754 state across world saves.
    private static int[] saveParticles(List<SPHParticle> particles) {
        if (particles.size() > SPHConstants.MAX_PARTICLES) {
            throw new IllegalStateException("SPH simulation exceeds persisted particle limit");
        }
        int[] data = new int[particles.size() * PARTICLE_DATA_STRIDE];
        for (int index = 0; index < particles.size(); index++) {
            SPHParticle particle = particles.get(index);
            validateFiniteParticle(
                    particle.position.x,
                    particle.position.y,
                    particle.position.z,
                    particle.velocity.x,
                    particle.velocity.y,
                    particle.velocity.z
            );
            int offset = index * PARTICLE_DATA_STRIDE;
            data[offset] = Float.floatToIntBits(particle.position.x);
            data[offset + 1] = Float.floatToIntBits(particle.position.y);
            data[offset + 2] = Float.floatToIntBits(particle.position.z);
            data[offset + 3] = Float.floatToIntBits(particle.velocity.x);
            data[offset + 4] = Float.floatToIntBits(particle.velocity.y);
            data[offset + 5] = Float.floatToIntBits(particle.velocity.z);
            data[offset + 6] = particle.isDroplet ? 1 : 0;
            data[offset + 7] = particle.dropletLife;
        }
        return data;
    }

    private static List<SPHParticle> loadParticles(CompoundTag simulationTag, int format) {
        int[] particleData = simulationTag.getIntArray(PARTICLE_DATA_KEY);
        if (particleData.length > 0 || format > 0) {
            if (particleData.length % PARTICLE_DATA_STRIDE != 0) {
                throw new IllegalArgumentException("SPH water payload has a trailing partial particle");
            }
            int particleCount = particleData.length / PARTICLE_DATA_STRIDE;
            if (particleCount > SPHConstants.MAX_PARTICLES) {
                throw new IllegalArgumentException("SPH water payload exceeds particle limit");
            }
            if (format > 0 && (!simulationTag.contains(PARTICLE_COUNT_KEY, Tag.TAG_INT)
                    || simulationTag.getInt(PARTICLE_COUNT_KEY) != particleCount)) {
                throw new IllegalArgumentException("SPH water particle count does not match payload");
            }
            List<SPHParticle> particles = new ArrayList<>(particleCount);
            for (int index = 0; index < particleCount; index++) {
                int offset = index * PARTICLE_DATA_STRIDE;
                float x = Float.intBitsToFloat(particleData[offset]);
                float y = Float.intBitsToFloat(particleData[offset + 1]);
                float z = Float.intBitsToFloat(particleData[offset + 2]);
                float velocityX = Float.intBitsToFloat(particleData[offset + 3]);
                float velocityY = Float.intBitsToFloat(particleData[offset + 4]);
                float velocityZ = Float.intBitsToFloat(particleData[offset + 5]);
                validateFiniteParticle(x, y, z, velocityX, velocityY, velocityZ);
                SPHParticle particle = new SPHParticle(
                        x,
                        y,
                        z
                );
                particle.velocity.set(
                        velocityX,
                        velocityY,
                        velocityZ
                );
                particle.isDroplet = particleData[offset + 6] != 0;
                particle.dropletLife = particleData[offset + 7];
                particles.add(particle);
            }
            return particles;
        }

        // Keep compatibility with early development worlds that stored one
        // compound per particle before the compact array format was introduced.
        ListTag storedParticles = simulationTag.getList(PARTICLES_KEY, Tag.TAG_COMPOUND);
        int particleCount = storedParticles.size();
        if (particleCount > SPHConstants.MAX_PARTICLES) {
            throw new IllegalArgumentException("Legacy SPH water payload exceeds particle limit");
        }
        List<SPHParticle> particles = new ArrayList<>(particleCount);
        for (int particleIndex = 0; particleIndex < particleCount; particleIndex++) {
            particles.add(loadParticle(storedParticles.getCompound(particleIndex)));
        }
        return particles;
    }

    private static void validateFiniteParticle(float... values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("SPH water payload contains a non-finite particle value");
            }
        }
    }

    private record StoredSimulation(UUID simulationId, List<SPHParticle> particles, int volumeUnits) {
    }
}
