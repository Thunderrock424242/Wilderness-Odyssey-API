package com.thunder.wildernessodysseyapi.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Marker effect used when behavior is driven by event handlers and client visuals.
 */
public class SimpleStatusEffect extends MobEffect {
    private SimpleStatusEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    public static SimpleStatusEffect harmful(int color) {
        return new SimpleStatusEffect(MobEffectCategory.HARMFUL, color);
    }
}
