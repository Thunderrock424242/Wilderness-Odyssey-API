package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies strict versioned SPH persistence and the unversioned array migration. */
class SphWaterSavedDataTest {

    @Test
    void unversionedCompactParticlesMigrateToTheVersionedFormat() {
        CompoundTag legacy = rootWithParticleData(validParticleData());

        SphWaterSavedData migrated = SphWaterSavedData.load(legacy, null);
        CompoundTag rewritten = migrated.save(new CompoundTag(), null);

        assertEquals(SphWaterSavedData.FORMAT_VERSION, rewritten.getInt("format_version"));
        assertEquals(1, rewritten.getInt("simulation_count"));
        CompoundTag simulation = rewritten.getList("simulations", Tag.TAG_COMPOUND)
                .getCompound(0);
        assertEquals(1, simulation.getInt("particle_count"));
        assertEquals(8, simulation.getIntArray("particle_data").length);
    }

    @Test
    void trailingAndNonFiniteParticleDataAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SphWaterSavedData.load(rootWithParticleData(new int[9]), null));

        int[] nonFinite = validParticleData();
        nonFinite[0] = Float.floatToIntBits(Float.NaN);
        assertThrows(IllegalArgumentException.class,
                () -> SphWaterSavedData.load(rootWithParticleData(nonFinite), null));
    }

    @Test
    void futureFormatIsRejected() {
        CompoundTag future = rootWithParticleData(validParticleData());
        future.putInt("format_version", SphWaterSavedData.FORMAT_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> SphWaterSavedData.load(future, null));
    }

    private static CompoundTag rootWithParticleData(int[] particleData) {
        CompoundTag simulation = new CompoundTag();
        simulation.putLong("id_most", 1L);
        simulation.putLong("id_least", 2L);
        simulation.putInt("volume_units", 4_096);
        simulation.putIntArray("particle_data", particleData);
        ListTag simulations = new ListTag();
        simulations.add(simulation);
        CompoundTag root = new CompoundTag();
        root.put("simulations", simulations);
        return root;
    }

    private static int[] validParticleData() {
        return new int[] {
                Float.floatToIntBits(1.0f),
                Float.floatToIntBits(2.0f),
                Float.floatToIntBits(3.0f),
                Float.floatToIntBits(0.25f),
                Float.floatToIntBits(-0.5f),
                Float.floatToIntBits(0.75f),
                0,
                -1
        };
    }
}
