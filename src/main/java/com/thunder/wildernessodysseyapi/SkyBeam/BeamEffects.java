package com.thunder.wildernessodysseyapi.SkyBeam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class BeamEffects {
    private static final int BLAST_RADIUS = 6;
    private static final int KNOCKBACK_RADIUS = 4;

    public static void summonBeamEffect(ServerLevel world, BlockPos pos) {
        for (int y = pos.getY(); y < world.getMaxBuildHeight(); y++) {
            BlockPos beamPos = new BlockPos(pos.getX(), y, pos.getZ());
            world.setBlock(beamPos, net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
        }
        world.scheduleTick(pos, net.minecraft.world.level.block.Blocks.BARRIER, 20);
    }

    public static void affectEntities(ServerLevel world, BlockPos pos) {
        List<Entity> entities = world.getEntities(null, new AABB(pos).inflate(BLAST_RADIUS));
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 1));

                double distance = entity.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                if (distance < KNOCKBACK_RADIUS * KNOCKBACK_RADIUS) {
                    double knockbackStrength = 1.5;
                    entity.setDeltaMovement(
                            (entity.getX() - pos.getX()) * knockbackStrength,
                            0.5,
                            (entity.getZ() - pos.getZ()) * knockbackStrength
                    );
                    entity.hurtMarked = true;
                }
            }
        }
    }
}