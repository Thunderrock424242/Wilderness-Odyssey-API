package com.thunder.wildernessodysseyapi.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

public class RadiationEffect extends MobEffect {

    // Ticks between damage pulses per amplifier level (0-3)
    private static final int[]   DAMAGE_INTERVALS = { 80, 50, 30, 15 };
    // Damage per pulse per amplifier level
    private static final float[] DAMAGE_AMOUNTS   = { 0.5f, 1.0f, 2.0f, 4.0f };

    public RadiationEffect() {
        // HARMFUL category, sickly green particle colour
        super(MobEffectCategory.HARMFUL, 0x22AA44);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        int amp = Math.min(amplifier, DAMAGE_INTERVALS.length - 1);

        // --- Damage pulse ---
        if (entity.tickCount % DAMAGE_INTERVALS[amp] == 0) {
            entity.hurt(entity.damageSources().magic(), DAMAGE_AMOUNTS[amp]);
        }

        // --- Secondary effects ---
        if (amplifier >= 1 && entity.tickCount % 100 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION,  120, 0, false, false));
        }
        if (amplifier >= 2 && entity.tickCount % 80 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,   120, 0, false, false));
        }
        if (amplifier >= 3 && entity.tickCount % 60 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,      100, 1, false, false));
        }

        // --- Particles (server-side spawn) ---
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.EFFECT,
                entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(),
                3, 0.3, 0.5, 0.3, 0.01
            );
            if (entity.tickCount % 20 == 0) {
                serverLevel.sendParticles(
                    ParticleTypes.FALLING_SPORE_BLOSSOM,
                    entity.getX(), entity.getY() + 0.5, entity.getZ(),
                    2, 0.4, 0.4, 0.4, 0.0
                );
            }
        }

        // --- Action-bar warning for players ---
        if (entity instanceof ServerPlayer player && entity.tickCount % 200 == 1) {
            player.displayClientMessage(
                Component.literal("☢ You are being irradiated!")
                    .withStyle(s -> s.withColor(0x22FF44).withBold(true)),
                true
            );
        }

        return true;
    }

    /** NeoForge 1.21: called every tick — we do our own interval gating above */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void onEffectAdded(LivingEntity entity, AttributeMap attributes) {
        super.onEffectAdded(entity, attributes);
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(
                null,
                entity.blockPosition(),
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT,
                net.minecraft.sounds.SoundSource.PLAYERS,
                0.6f, 0.5f
            );
        }
    }

    // ------------------------------------------------------------------
    //  Static helper — called by RadiationTickHandler to pick amplifier
    // ------------------------------------------------------------------

    /**
     * Returns amplifier 0-3 based on how close the entity is to the meteor center.
     *
     * @param distanceSq      horizontal squared distance to meteor center
     * @param radiationRadius the full radiation zone radius in blocks
     */
    public static int getAmplifierForDistance(double distanceSq, double radiationRadius) {
        double ratio = Math.sqrt(distanceSq) / radiationRadius;
        if (ratio < 0.25) return 3; // epicenter — critical
        if (ratio < 0.50) return 2; // inner zone  — severe
        if (ratio < 0.75) return 1; // mid zone    — moderate
        return 0;                   // outer fringe — mild
    }
}
