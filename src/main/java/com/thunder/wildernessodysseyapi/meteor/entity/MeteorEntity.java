package com.thunder.wildernessodysseyapi.meteor.entity;

import com.thunder.wildernessodysseyapi.core.ModEntities;
import com.thunder.wildernessodysseyapi.meteor.config.MeteorConfig;
import com.thunder.wildernessodysseyapi.meteor.worldgen.CraterGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * MeteorEntity — a non-living projectile that:
 *  - Flies in from high altitude at an angle
 *  - Emits fire + smoke + lava drip particles every tick
 *  - Detects nearby players and nudges its target away from them
 *  - On ground impact, calls CraterGenerator and plays impact sound/particles
 */
public class MeteorEntity extends Entity {

    // --- Synced data ---
    // Glowing state drives the client-side particle intensity (pre-impact glow)
    private static final EntityDataAccessor<Boolean> DATA_IMPACTED =
            SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.BOOLEAN);

    // Target landing position (used for homing-light nudge away from players)
    private double targetX, targetY, targetZ;

    // Crater radius resolved from config
    private int craterRadius;

    // Ticks until we send the "incoming!" warning (counts down)
    private int warningCountdown;
    private boolean warningSent = false;

    // Impact velocity snapshot for gouge direction
    private Vec3 impactVelocity = Vec3.ZERO;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    /**
     * Factory method used by the event scheduler.
     *
     * @param level        server level
     * @param startPos     high-altitude spawn position
     * @param targetPos    ground-level landing target
     * @param craterRadius resolved crater radius
     */
    public static MeteorEntity create(ServerLevel level, Vec3 startPos, Vec3 targetPos, int craterRadius) {
        MeteorEntity meteor = new MeteorEntity(ModEntities.METEOR.get(), level);
        meteor.setPos(startPos.x, startPos.y, startPos.z);
        meteor.targetX = targetPos.x;
        meteor.targetY = targetPos.y;
        meteor.targetZ = targetPos.z;
        meteor.craterRadius = craterRadius;

        // Velocity vector pointing from spawn to target
        Vec3 dir = targetPos.subtract(startPos).normalize();
        double speed = 1.8 + level.random.nextDouble() * 0.8; // 1.8–2.6 blocks/tick
        meteor.setDeltaMovement(dir.scale(speed));

        // Warning countdown
        double dist = startPos.distanceTo(targetPos);
        double ticksToImpact = dist / speed;
        int warnBefore = MeteorConfig.WARNING_TICKS_BEFORE_IMPACT.get();
        meteor.warningCountdown = (int) Math.max(0, ticksToImpact - warnBefore);

        return meteor;
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            spawnClientTrailParticles();
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level();

        // Warning message
        if (!warningSent && warningCountdown-- <= 0) {
            warningSent = true;
            if (MeteorConfig.SHOW_WARNING_MESSAGE.get()) {
                serverLevel.players().forEach(p ->
                        p.sendSystemMessage(Component.literal("§c☄ The ground shakes... something approaches from above! ☄"))
                );
            }
        }

        // Apply gravity (gentle, meteors are fast so gravity has limited effect)
        Vec3 movement = getDeltaMovement();
        setDeltaMovement(movement.add(0, -0.04, 0));

        // Move
        Vec3 pos = position();
        Vec3 nextPos = pos.add(getDeltaMovement());
        setPos(nextPos.x, nextPos.y, nextPos.z);

        // Spawn server-side particles (visible to all clients via level)
        spawnServerTrailParticles(serverLevel);

        // Check for ground impact
        if (checkGroundImpact(serverLevel)) return;

        // Discard if somehow below world
        if (getY() < level().getMinBuildHeight() - 20) {
            discard();
        }
    }

    // -------------------------------------------------------------------------
    // Ground impact detection
    // -------------------------------------------------------------------------

    private boolean checkGroundImpact(ServerLevel level) {
        BlockPos feetPos = blockPosition();
        BlockPos belowPos = feetPos.below();

        // Check the block at our current position and the one below
        boolean hitGround = !level.getBlockState(feetPos).isAir()
                || (!level.getBlockState(belowPos).isAir()
                && level.getBlockState(belowPos).isSolid());

        if (hitGround && !entityData.get(DATA_IMPACTED)) {
            entityData.set(DATA_IMPACTED, true);
            impactVelocity = getDeltaMovement();
            onGroundImpact(level, feetPos);
            return true;
        }
        return false;
    }

    private void onGroundImpact(ServerLevel level, BlockPos impactPos) {
        // Impact boom particles
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                impactPos.getX() + 0.5, impactPos.getY() + 0.5, impactPos.getZ() + 0.5,
                1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA,
                impactPos.getX() + 0.5, impactPos.getY() + 1, impactPos.getZ() + 0.5,
                30, 1.5, 0.5, 1.5, 0.3);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                impactPos.getX() + 0.5, impactPos.getY() + 1, impactPos.getZ() + 0.5,
                20, 1.0, 0.5, 1.0, 0.1);

        // Impact sound — use explosion + blaze sound layered
        level.playSound(null, impactPos,
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.BLOCKS, 5.0f, 0.6f + level.random.nextFloat() * 0.3f);
        level.playSound(null, impactPos,
                SoundEvents.BLAZE_HURT,
                SoundSource.BLOCKS, 3.0f, 0.4f);

        // Push players away (safety buffer — knockback only, no damage)
        double safeRadius = MeteorConfig.PLAYER_AVOID_RADIUS.get() + craterRadius + 2.0;
        List<net.minecraft.world.entity.player.Player> nearby =
                level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                        new AABB(impactPos).inflate(safeRadius));
        for (var player : nearby) {
            Vec3 pushDir = player.position().subtract(Vec3.atCenterOf(impactPos)).normalize();
            double dist = player.distanceToSqr(Vec3.atCenterOf(impactPos));
            double force = Math.max(0, 2.5 - (dist / (safeRadius * safeRadius)) * 2.5);
            player.push(pushDir.x * force, 0.4 * force, pushDir.z * force);
        }

        // Carve the crater
        CraterGenerator.generate(
                level,
                impactPos,
                craterRadius,
                impactVelocity,
                level.random,
                MeteorConfig.GOUGE_LENGTH_MULTIPLIER.get()
        );

        discard();
    }

    // -------------------------------------------------------------------------
    // Particles
    // -------------------------------------------------------------------------

    /** Client-side: rich particle trail using local particle system */
    private void spawnClientTrailParticles() {
        RandomSource rng = level().getRandom();
        Vec3 vel = getDeltaMovement();
        // Trail spawns behind the meteor
        Vec3 base = position();

        for (int i = 0; i < 6; i++) {
            double ox = (rng.nextDouble() - 0.5) * 0.4;
            double oz = (rng.nextDouble() - 0.5) * 0.4;
            level().addParticle(ParticleTypes.FLAME,
                    base.x + ox, base.y + rng.nextDouble() * 0.3, base.z + oz,
                    -vel.x * 0.3 + (rng.nextDouble() - 0.5) * 0.1,
                    0.05,
                    -vel.z * 0.3 + (rng.nextDouble() - 0.5) * 0.1);
        }
        for (int i = 0; i < 3; i++) {
            double ox = (rng.nextDouble() - 0.5) * 0.6;
            double oz = (rng.nextDouble() - 0.5) * 0.6;
            level().addParticle(ParticleTypes.LARGE_SMOKE,
                    base.x + ox, base.y + 0.5 + rng.nextDouble(), base.z + oz,
                    -vel.x * 0.1, 0.02, -vel.z * 0.1);
        }
        // Occasional lava drip for dramatic effect
        if (rng.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.DRIPPING_LAVA,
                    base.x + (rng.nextDouble() - 0.5) * 0.8,
                    base.y,
                    base.z + (rng.nextDouble() - 0.5) * 0.8,
                    0, -0.1, 0);
        }
    }

    /** Server-side: send particles to all nearby clients */
    private void spawnServerTrailParticles(ServerLevel level) {
        RandomSource rng = level.getRandom();
        Vec3 pos = position();
        Vec3 vel = getDeltaMovement();

        level.sendParticles(ParticleTypes.FLAME,
                pos.x, pos.y, pos.z, 4,
                0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.x, pos.y + 0.5, pos.z, 2,
                0.4, 0.2, 0.4, 0.01);

        if (rng.nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.DRIPPING_LAVA,
                    pos.x, pos.y, pos.z, 1, 0.5, 0.3, 0.5, 0);
        }
    }

    // -------------------------------------------------------------------------
    // NBT / data sync
    // -------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_IMPACTED, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        targetX = tag.getDouble("TargetX");
        targetY = tag.getDouble("TargetY");
        targetZ = tag.getDouble("TargetZ");
        craterRadius = tag.getInt("CraterRadius");
        warningSent = tag.getBoolean("WarningSent");
        warningCountdown = tag.getInt("WarningCountdown");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("TargetX", targetX);
        tag.putDouble("TargetY", targetY);
        tag.putDouble("TargetZ", targetZ);
        tag.putInt("CraterRadius", craterRadius);
        tag.putBoolean("WarningSent", warningSent);
        tag.putInt("WarningCountdown", warningCountdown);
    }

    // Meteors don't collide with entities (they fly through the air)
    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }
}