package com.thunder.wildernessodysseyapi.entity;

import com.thunder.wildernessodysseyapi.config.RiftfallConfig;
import com.thunder.wildernessodysseyapi.core.ModEntities;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Escape-horror phase spawned when a Rift Listener catches a player.
 */
public class RiftMawEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_EMERGE_TICKS =
            SynchedEntityData.defineId(RiftMawEntity.class, EntityDataSerializers.INT);

    private UUID boundTarget;
    private int lifeTicks = 600;
    private int missingTargetTicks;

    public RiftMawEntity(EntityType<? extends RiftMawEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 35;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FOLLOW_RANGE, 56.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public static void spawnFromListener(ServerLevel level, RiftListenerEntity listener, ServerPlayer victim) {
        if (hasNearbyMaw(level, victim)) {
            return;
        }

        RiftMawEntity maw = ModEntities.RIFT_MAW.get().create(level);
        if (maw == null) {
            return;
        }

        BlockPos spawn = findGroundNear(level, listener.blockPosition(), victim.blockPosition());
        maw.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        maw.boundTarget = victim.getUUID();
        maw.lifeTicks = RiftfallConfig.CONFIG.riftMawLifetimeTicks();
        maw.setTarget(victim);

        if (maw.checkSpawnObstruction(level)) {
            level.addFreshEntity(maw);
            level.sendParticles(ParticleTypes.PORTAL, maw.getX(), maw.getY() + 1.0D, maw.getZ(), 80, 1.6D, 1.0D, 1.6D, 0.08D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, maw.getX(), maw.getY() + 0.4D, maw.getZ(), 35, 1.2D, 0.5D, 1.2D, 0.02D);
        }
    }

    private static boolean hasNearbyMaw(ServerLevel level, ServerPlayer victim) {
        return !level.getEntities(ModEntities.RIFT_MAW.get(),
                new AABB(victim.blockPosition()).inflate(32.0D),
                maw -> maw.isAlive()).isEmpty();
    }

    private static BlockPos findGroundNear(ServerLevel level, BlockPos listenerPos, BlockPos victimPos) {
        BlockPos midpoint = new BlockPos(
                Mth.floor((listenerPos.getX() + victimPos.getX()) * 0.5D),
                victimPos.getY(),
                Mth.floor((listenerPos.getZ() + victimPos.getZ()) * 0.5D)
        );

        for (int i = 0; i < 10; i++) {
            int x = midpoint.getX() + level.random.nextInt(9) - 4;
            int z = midpoint.getZ() + level.random.nextInt(9) - 4;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.below()).isSolid()) {
                return candidate;
            }
        }

        return victimPos;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 0.82D, true));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            return;
        }

        if (!RiftfallSystem.stage().isActiveDanger()) {
            discard();
            return;
        }

        if (lifeTicks-- <= 0) {
            discard();
            return;
        }

        entityData.set(DATA_EMERGE_TICKS, Math.min(60, entityData.get(DATA_EMERGE_TICKS) + 1));

        ServerPlayer victim = resolveVictim();
        if (victim == null) {
            if (++missingTargetTicks > 100) {
                discard();
            }
            return;
        }

        missingTargetTicks = 0;
        setTarget(victim);
        getNavigation().moveTo(victim, 0.78D);
        pullVictim(victim);

        if (tickCount % 20 == 0) {
            victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, true, false, true));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0, true, false, true));
        }

        if (level() instanceof ServerLevel serverLevel && tickCount % 5 == 0) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + getBbHeight() * 0.45D, getZ(), 10, 1.1D, 0.8D, 1.1D, 0.04D);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, Math.max(0.5F, amount * 0.1F));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EMERGE_TICKS, 0);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lifeTicks = tag.getInt("LifeTicks");
        missingTargetTicks = tag.getInt("MissingTargetTicks");
        if (tag.hasUUID("BoundTarget")) {
            boundTarget = tag.getUUID("BoundTarget");
        }
        entityData.set(DATA_EMERGE_TICKS, tag.getInt("EmergeTicks"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LifeTicks", lifeTicks);
        tag.putInt("MissingTargetTicks", missingTargetTicks);
        tag.putInt("EmergeTicks", entityData.get(DATA_EMERGE_TICKS));
        if (boundTarget != null) {
            tag.putUUID("BoundTarget", boundTarget);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public int getEmergeTicks() {
        return entityData.get(DATA_EMERGE_TICKS);
    }

    private ServerPlayer resolveVictim() {
        if (boundTarget != null && level().getServer() != null) {
            ServerPlayer player = level().getServer().getPlayerList().getPlayer(boundTarget);
            if (player != null && player.isAlive() && !player.isCreative() && !player.isSpectator() && distanceToSqr(player) < 4096.0D) {
                return player;
            }
        }

        LivingEntity target = getTarget();
        if (target instanceof ServerPlayer player && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
            boundTarget = player.getUUID();
            return player;
        }
        return null;
    }

    private void pullVictim(ServerPlayer victim) {
        double distance = distanceTo(victim);
        if (distance <= 2.5D || distance > 18.0D) {
            return;
        }

        Vec3 direction = position().add(0.0D, 1.0D, 0.0D).subtract(victim.position());
        if (direction.lengthSqr() < 0.001D) {
            return;
        }

        double strength = Mth.clamp((18.0D - distance) / 18.0D * 0.13D, 0.025D, 0.13D);
        if (victim.isCrouching()) {
            strength *= 0.65D;
        }

        Vec3 pull = direction.normalize().scale(strength);
        victim.push(pull.x, 0.015D, pull.z);
    }
}
