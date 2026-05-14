package com.thunder.wildernessodysseyapi.entity;

import com.thunder.wildernessodysseyapi.core.ModEntities;
import com.thunder.wildernessodysseyapi.crouching.CrouchNoiseHelper;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Riftfall stealth threat that hunts sound instead of normal line-of-sight.
 */
public class RiftListenerEntity extends Monster {
    public static final int STATE_QUIET = 0;
    public static final int STATE_LISTENING = 1;
    public static final int STATE_HUNTING = 2;

    private static final EntityDataAccessor<Integer> DATA_LISTENER_STATE =
            SynchedEntityData.defineId(RiftListenerEntity.class, EntityDataSerializers.INT);

    private static final double LISTEN_RANGE = 48.0D;
    private static final double HUNT_SCORE = 0.42D;
    private static final double INTEREST_SCORE = 0.12D;
    private static final double TOUCH_DISTANCE_SQR = 3.24D;
    private static final int TOUCH_COOLDOWN_TICKS = 160;

    private BlockPos investigatePos;
    private int attentionTicks;
    private int touchCooldown;

    public RiftListenerEntity(EntityType<? extends RiftListenerEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 90.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    public static boolean checkRiftListenerSpawnRules(EntityType<RiftListenerEntity> type,
                                                      ServerLevelAccessor level,
                                                      MobSpawnType reason,
                                                      BlockPos pos,
                                                      RandomSource random) {
        return Monster.isDarkEnoughToSpawn(level, pos, random)
                && checkMobSpawnRules(type, level, reason, pos, random)
                && level.getLevel().isRaining();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new ListenerSoundGoal());
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
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

        if (touchCooldown > 0) {
            touchCooldown--;
        }

        LivingEntity target = getTarget();
        if (target instanceof ServerPlayer player && target.isAlive() && distanceToSqr(player) <= TOUCH_DISTANCE_SQR) {
            triggerRiftTouch(player);
        }

        if (level() instanceof ServerLevel serverLevel && tickCount % 8 == 0) {
            int state = getListenerState();
            if (state != STATE_QUIET) {
                double spread = state == STATE_HUNTING ? 0.7D : 0.35D;
                serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + getBbHeight() * 0.55D, getZ(),
                        state == STATE_HUNTING ? 10 : 4, spread, 0.5D, spread, 0.02D);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        attentionTicks = Math.max(attentionTicks, 80);
        setListenerState(STATE_HUNTING);
        return super.hurt(source, Math.max(1.0F, amount * 0.2F));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LISTENER_STATE, STATE_QUIET);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        attentionTicks = tag.getInt("AttentionTicks");
        touchCooldown = tag.getInt("TouchCooldown");
        if (tag.contains("InvestigateX")) {
            investigatePos = new BlockPos(tag.getInt("InvestigateX"), tag.getInt("InvestigateY"), tag.getInt("InvestigateZ"));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AttentionTicks", attentionTicks);
        tag.putInt("TouchCooldown", touchCooldown);
        if (investigatePos != null) {
            tag.putInt("InvestigateX", investigatePos.getX());
            tag.putInt("InvestigateY", investigatePos.getY());
            tag.putInt("InvestigateZ", investigatePos.getZ());
        }
    }

    public int getListenerState() {
        return entityData.get(DATA_LISTENER_STATE);
    }

    private void setListenerState(int state) {
        entityData.set(DATA_LISTENER_STATE, state);
    }

    private void triggerRiftTouch(ServerPlayer player) {
        if (touchCooldown > 0 || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        touchCooldown = TOUCH_COOLDOWN_TICKS;
        attentionTicks = 60;
        setTarget(null);
        getNavigation().stop();

        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, true, false, true));
        player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.rift_listener_touch"), true);

        RiftMawEntity.spawnFromListener(serverLevel, this, player);
    }

    private SoundSample findLoudestPlayer() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        List<ServerPlayer> players = serverLevel.getEntitiesOfClass(ServerPlayer.class,
                getBoundingBox().inflate(LISTEN_RANGE),
                player -> player.isAlive() && !player.isSpectator() && !player.isCreative());

        SoundSample best = null;
        for (ServerPlayer player : players) {
            double score = acousticScore(player);
            if (score > 0.0D && (best == null || score > best.score())) {
                best = new SoundSample(player, score, player.blockPosition());
            }
        }
        return best;
    }

    private double acousticScore(ServerPlayer player) {
        double distance = distanceTo(player);
        if (distance > LISTEN_RANGE) {
            return 0.0D;
        }

        Vec3 movement = player.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        double sound = horizontalSpeed * 14.0D;

        if (player.isSprinting()) {
            sound += 0.9D;
        }
        if (player.isSwimming()) {
            sound += 0.6D;
        }
        if (!player.onGround()) {
            sound += 0.35D;
        }
        if (player.fallDistance > 1.5F) {
            sound += 0.6D;
        }
        if (player.isUsingItem()) {
            sound += 0.35D;
        }
        if (player.hurtTime > 0) {
            sound += 1.0D;
        }

        if (player.isCrouching()) {
            double armorNoise = CrouchNoiseHelper.getCrouchVisibilityMultiplier(player);
            sound *= Mth.clamp(0.12D + armorNoise * 0.25D, 0.08D, 0.45D);
        } else if (horizontalSpeed < 0.01D && player.onGround()) {
            sound *= 0.15D;
        }

        double attenuation = 1.0D - distance / LISTEN_RANGE;
        return Math.max(0.0D, sound * attenuation);
    }

    private record SoundSample(ServerPlayer player, double score, BlockPos pos) {
    }

    private final class ListenerSoundGoal extends Goal {
        private ListenerSoundGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public void tick() {
            SoundSample sample = findLoudestPlayer();
            if (sample != null && sample.score() >= INTEREST_SCORE) {
                investigatePos = sample.pos();
                attentionTicks = Math.min(120, attentionTicks + (sample.score() >= HUNT_SCORE ? 10 : 3));
                getLookControl().setLookAt(sample.player(), 30.0F, 30.0F);

                if (sample.score() >= HUNT_SCORE || attentionTicks >= 70) {
                    setListenerState(STATE_HUNTING);
                    setTarget(sample.player());
                    getNavigation().moveTo(sample.player(), 1.18D);
                } else {
                    setListenerState(STATE_LISTENING);
                    setTarget(null);
                    getNavigation().moveTo(sample.pos().getX() + 0.5D, sample.pos().getY(), sample.pos().getZ() + 0.5D, 0.72D);
                }
                return;
            }

            if (attentionTicks > 0) {
                attentionTicks--;
            }

            if (investigatePos != null && attentionTicks > 10) {
                setListenerState(STATE_LISTENING);
                getNavigation().moveTo(investigatePos.getX() + 0.5D, investigatePos.getY(), investigatePos.getZ() + 0.5D, 0.62D);
                if (distanceToSqr(Vec3.atCenterOf(investigatePos)) < 5.0D) {
                    investigatePos = null;
                }
            } else {
                setListenerState(STATE_QUIET);
                setTarget(null);
            }
        }
    }
}
