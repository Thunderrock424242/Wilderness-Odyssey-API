package com.thunder.wildernessodysseyapi.watersystem.water.render;

/**
 * Converts pre-contact entity motion into one bounded cosmetic water impact.
 *
 * <p>The model is deliberately independent of Minecraft particles so impact
 * energy can be tested without a client. Inputs use normal entity units:
 * blocks per tick for velocity, blocks for size and fall distance.</p>
 */
final class WaterEntryImpactModel {

    private WaterEntryImpactModel() {
    }

    static Impact evaluate(
            float entityWidth,
            float entityHeight,
            double downwardSpeedPerTick,
            double horizontalSpeedPerTick,
            float fallDistance,
            boolean watercraft,
            int particleBudget
    ) {
        float width = finiteClamp(entityWidth, 0.20f, 3.0f, 0.60f);
        float height = finiteClamp(entityHeight, 0.20f, 4.0f, 1.0f);
        float downward = finiteClamp((float) downwardSpeedPerTick, 0.0f, 1.0f, 0.0f);
        float horizontal = finiteClamp((float) horizontalSpeedPerTick, 0.0f, 1.0f, 0.0f);
        float fall = finiteClamp(fallDistance, 0.0f, 12.0f, 0.0f);

        // Fall distance preserves the energy from the dry tick before vanilla
        // water drag reduces the entity velocity used by the post-tick event.
        float verticalEnergy = smoothStep(0.025f, 0.55f, downward + fall * 0.0125f);
        float horizontalEnergy = smoothStep(0.04f, 0.42f, horizontal);
        float bodyEnergy = smoothStep(0.35f, 2.0f, Math.max(width, height * 0.35f));
        float strength = clamp(
                0.10f
                        + verticalEnergy * 0.64f
                        + horizontalEnergy * 0.18f
                        + bodyEnergy * 0.12f
                        + (watercraft ? 0.10f : 0.0f),
                0.10f,
                1.0f
        );

        int budget = Math.max(0, particleBudget);
        int particles = budget == 0
                ? 0
                : Math.max(1, Math.min(budget, Math.round(1.0f + (budget - 1) * strength)));
        float bodyRadiusScale = clamp(width, 0.50f, 1.80f);
        float spawnRadius = clamp(
                (0.18f + strength * 0.72f) * bodyRadiusScale,
                0.16f,
                1.55f
        );

        return new Impact(
                strength,
                particles,
                spawnRadius,
                0.08f + strength * 0.34f,
                0.12f + strength * 0.38f,
                clamp(0.18f + strength * 0.90f, 0.18f, 1.0f)
        );
    }

    private static float smoothStep(float minimum, float maximum, float value) {
        float factor = clamp((value - minimum) / (maximum - minimum), 0.0f, 1.0f);
        return factor * factor * (3.0f - 2.0f * factor);
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? clamp(value, minimum, maximum) : fallback;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Impact(
            float strength,
            int particleCount,
            float spawnRadius,
            float outwardSpeed,
            float upwardSpeed,
            float rippleStrength
    ) {
    }
}
