package com.thunder.wildernessodysseyapi.watersystem.water.sph;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the bucket-scale density calibration and bounded contact correction. */
class SPHCalibrationTest {

    @Test
    void configuredBucketProducesUsefulPressureDensity() {
        int count = SPHConstants.PARTICLES_PER_BUCKET;
        float[][] positions = deterministicBucketPositions(count, 0x5EEDL);
        float totalDensity = 0.0f;
        int positivePressureParticles = 0;

        for (int particle = 0; particle < count; particle++) {
            float density = 0.0f;
            for (int neighbour = 0; neighbour < count; neighbour++) {
                float dx = positions[particle][0] - positions[neighbour][0];
                float dy = positions[particle][1] - positions[neighbour][1];
                float dz = positions[particle][2] - positions[neighbour][2];
                density += SPHConstants.PARTICLE_MASS
                        * SPHKernels.poly6(dx * dx + dy * dy + dz * dz);
            }
            totalDensity += density;
            if (SPHEquationOfState.pressureForDensity(density) > 0.0f) {
                positivePressureParticles++;
            }
        }

        float averageDensity = totalDensity / count;
        float positivePressureFraction = positivePressureParticles / (float) count;
        assertTrue(averageDensity >= SPHConstants.REST_DENSITY * 0.95f);
        assertTrue(averageDensity <= SPHConstants.REST_DENSITY * 1.30f);
        assertTrue(positivePressureFraction >= 0.35f);
        assertTrue(positivePressureFraction <= 0.80f);
    }

    @Test
    void taitPressureActivatesAtCompressionWithoutGoingNegative() {
        assertEquals(0.0f, SPHEquationOfState.pressureForDensity(800.0f), 1.0e-6f);
        assertEquals(0.0f,
                SPHEquationOfState.pressureForDensity(SPHConstants.REST_DENSITY), 1.0e-6f);
        assertTrue(SPHEquationOfState.pressureForDensity(1_100.0f) > 0.0f);
        assertTrue(SPHEquationOfState.pressureForDensity(100_000.0f) <= SPHConstants.MAX_PRESSURE);
    }

    @Test
    void groundAssistRequiresCompressionAndFadesWithMotion() {
        assertEquals(0.0f, SPHEquationOfState.groundAssistFactor(800.0f, 0.0f), 1.0e-6f);
        assertTrue(SPHEquationOfState.groundAssistFactor(1_100.0f, 0.0f) > 0.0f);
        assertEquals(0.0f,
                SPHEquationOfState.groundAssistFactor(1_200.0f, 1.25f), 1.0e-6f);
        assertEquals(0.0f,
                SPHEquationOfState.groundAssistFactor(Float.NaN, 0.0f), 1.0e-6f);
        assertEquals(0.0f,
                SPHEquationOfState.groundAssistFactor(1_200.0f, Float.NaN), 1.0e-6f);
    }

    private static float[][] deterministicBucketPositions(int count, long seed) {
        Random random = new Random(seed);
        float[][] positions = new float[count][3];
        float radius = SPHConstants.SPAWN_RADIUS;
        for (int index = 0; index < count; index++) {
            float x;
            float z;
            do {
                x = (random.nextFloat() * 2.0f - 1.0f) * radius;
                z = (random.nextFloat() * 2.0f - 1.0f) * radius;
            } while (x * x + z * z > radius * radius);
            positions[index][0] = x;
            positions[index][1] = (random.nextFloat() - 0.5f) * SPHConstants.SPAWN_HEIGHT;
            positions[index][2] = z;
        }
        return positions;
    }
}
