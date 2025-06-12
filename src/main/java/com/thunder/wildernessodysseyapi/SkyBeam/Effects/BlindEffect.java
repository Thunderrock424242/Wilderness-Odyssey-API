package com.thunder.wildernessodysseyapi.SkyBeam.Effects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

public class BlindEffect {
    /** radius, duration ticks */
    public static void apply(ServerLevel w, BlockPos p, double r, int duration) {
        AABB area = new AABB(p).inflate(r);
        for (Entity e : w.getEntities(null, area)) {
            if (e instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 1));
            }
        }
    }
}
