package com.thunder.wildernessodysseyapi.entity;

import com.thunder.wildernessodysseyapi.crouching.CrouchNoiseHelper;
import com.thunder.wildernessodysseyapi.cloak.item.CloakState;
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
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * A riftfall predator that behaves like a BT: quiet until it hears breath or movement,
 * then it manifests, stalks, and tries to drag the player into the rift.
 */
public class RiftboundWraithEntity extends Monster implements GeoEntity {
    public static final int STATE_VANISHED = 0;
    public static final int STATE_LISTENING = 1;
    public static final int STATE_HUNTING = 2;
    public static final int STATE_GRASPING = 3;

    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation LISTEN_ANIMATION = RawAnimation.begin().thenLoop("listen");
    private static final RawAnimation MOVE_ANIMATION = RawAnimation.begin().thenLoop("move");
    private static final RawAnimation HUNT_ANIMATION = RawAnimation.begin().thenLoop("hunt");
    private static final RawAnimation GRASP_ANIMATION = RawAnimation.begin().thenLoop("grasp");
    private static final EntityDataAccessor<Integer> DATA_WRAITH_STATE =
            SynchedEntityData.defineId(RiftboundWraithEntity.class, EntityDataSerializers.INT);

    private static final double LISTEN_RANGE = 56.0D;
    private static final double INTEREST_SCORE = 0.10D;
    private static final double HUNT_SCORE = 0.48D;
    private static final double GRASP_DISTANCE_SQR = 5.76D;
    private static final int GRASP_COOLDOWN_TICKS = 120;
    private static final int DRAG_DURATION_TICKS = 100;
    private static final int ESCAPE_JUMPS_REQUIRED = 5;
    private static final int JUMP_DETECT_COOLDOWN_TICKS = 7;
    private static final int DRAG_DAMAGE_INTERVAL_TICKS = 20;

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    private BlockPos investigatePos;
    private UUID draggedTarget;
    private int attentionTicks;
    private int graspCooldown;
    private int graspTicks;
    private int dragTicks;
    private int escapeJumpCount;
    private int jumpDetectCooldown;
    private boolean draggedTargetWasGrounded;

    public RiftboundWraithEntity(EntityType<? extends RiftboundWraithEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 28;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 110.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D);
    }

    public static boolean checkRiftboundWraithSpawnRules(EntityType<RiftboundWraithEntity> type,
                                                         ServerLevelAccessor level,
                                                         MobSpawnType reason,
                                                         BlockPos pos,
                                                         RandomSource random) {
        return Monster.isDarkEnoughToSpawn(level, pos, random)
                && checkMobSpawnRules(type, level, reason, pos, random)
                && RiftfallSystem.stage().isActiveDanger()
                && (level.getLevel().isRaining() || level.getLevel().isThundering());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new WraithSoundGoal());
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            return;
        }

        if (graspCooldown > 0) {
            graspCooldown--;
        }
        if (graspTicks > 0) {
            graspTicks--;
        }

        if (draggedTarget != null) {
            tickDragging();
        } else {
            LivingEntity target = getTarget();
            if (target instanceof ServerPlayer player && isValidHuntTarget(player) && distanceToSqr(player) <= GRASP_DISTANCE_SQR) {
                triggerGrasp(player);
            }
        }

        if (level() instanceof ServerLevel serverLevel && tickCount % 6 == 0) {
            int state = getWraithState();
            if (state != STATE_VANISHED) {
                double spread = state == STATE_HUNTING || state == STATE_GRASPING ? 0.8D : 0.35D;
                int count = state == STATE_GRASPING ? 18 : state == STATE_HUNTING ? 9 : 4;
                serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + getBbHeight() * 0.58D, getZ(),
                        count, spread, 0.7D, spread, 0.025D);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        attentionTicks = Math.max(attentionTicks, 120);
        setWraithState(STATE_HUNTING);

        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingEntity) {
            setTarget(livingEntity);
        }

        return super.hurt(source, Math.max(1.0F, amount * 0.35F));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WRAITH_STATE, STATE_VANISHED);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        attentionTicks = tag.getInt("AttentionTicks");
        graspCooldown = tag.getInt("GraspCooldown");
        graspTicks = tag.getInt("GraspTicks");
        dragTicks = tag.getInt("DragTicks");
        escapeJumpCount = tag.getInt("EscapeJumpCount");
        jumpDetectCooldown = tag.getInt("JumpDetectCooldown");
        draggedTargetWasGrounded = tag.getBoolean("DraggedTargetWasGrounded");
        entityData.set(DATA_WRAITH_STATE, tag.getInt("WraithState"));
        if (tag.hasUUID("DraggedTarget")) {
            draggedTarget = tag.getUUID("DraggedTarget");
        }
        if (tag.contains("InvestigateX")) {
            investigatePos = new BlockPos(tag.getInt("InvestigateX"), tag.getInt("InvestigateY"), tag.getInt("InvestigateZ"));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AttentionTicks", attentionTicks);
        tag.putInt("GraspCooldown", graspCooldown);
        tag.putInt("GraspTicks", graspTicks);
        tag.putInt("DragTicks", dragTicks);
        tag.putInt("EscapeJumpCount", escapeJumpCount);
        tag.putInt("JumpDetectCooldown", jumpDetectCooldown);
        tag.putBoolean("DraggedTargetWasGrounded", draggedTargetWasGrounded);
        tag.putInt("WraithState", getWraithState());
        if (draggedTarget != null) {
            tag.putUUID("DraggedTarget", draggedTarget);
        }
        if (investigatePos != null) {
            tag.putInt("InvestigateX", investigatePos.getX());
            tag.putInt("InvestigateY", investigatePos.getY());
            tag.putInt("InvestigateZ", investigatePos.getZ());
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "wraith_controller", 5,
                animationState -> animationState.setAndContinue(animationForState(animationState.isMoving()))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    public int getWraithState() {
        return entityData.get(DATA_WRAITH_STATE);
    }

    private void setWraithState(int state) {
        entityData.set(DATA_WRAITH_STATE, state);
    }

    private void triggerGrasp(ServerPlayer player) {
        if (graspCooldown > 0) {
            return;
        }

        graspCooldown = GRASP_COOLDOWN_TICKS;
        graspTicks = DRAG_DURATION_TICKS;
        dragTicks = DRAG_DURATION_TICKS;
        escapeJumpCount = 0;
        jumpDetectCooldown = 0;
        draggedTargetWasGrounded = player.onGround();
        draggedTarget = player.getUUID();
        attentionTicks = 120;
        setWraithState(STATE_GRASPING);
        setTarget(player);
        getNavigation().stop();

        player.hurt(player.damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, true, false, true));
        player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.riftbound_wraith_grasp", ESCAPE_JUMPS_REQUIRED), true);

        pullVictim(player);

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + getBbHeight() * 0.5D, getZ(),
                    35, 1.0D, 0.8D, 1.0D, 0.08D);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.35D, player.getZ(),
                    12, 0.35D, 0.2D, 0.35D, 0.02D);
        }
    }

    private RawAnimation animationForState(boolean moving) {
        return switch (getWraithState()) {
            case STATE_LISTENING -> LISTEN_ANIMATION;
            case STATE_HUNTING -> HUNT_ANIMATION;
            case STATE_GRASPING -> GRASP_ANIMATION;
            default -> moving ? MOVE_ANIMATION : IDLE_ANIMATION;
        };
    }

    private void tickDragging() {
        ServerPlayer victim = resolveDraggedTarget();
        if (victim == null || !isValidHuntTarget(victim) || distanceToSqr(victim) > 144.0D || dragTicks <= 0) {
            releaseDraggedTarget(STATE_HUNTING);
            return;
        }

        dragTicks--;
        graspTicks = Math.max(graspTicks, 2);
        attentionTicks = Math.max(attentionTicks, 80);
        setWraithState(STATE_GRASPING);
        setTarget(victim);
        getNavigation().stop();

        pullVictim(victim);
        detectEscapeJump(victim);

        if (escapeJumpCount >= ESCAPE_JUMPS_REQUIRED) {
            victim.displayClientMessage(Component.translatable("message.wildernessodysseyapi.riftbound_wraith_escaped"), true);
            releaseDraggedTarget(STATE_HUNTING);
            return;
        }

        if (tickCount % 10 == 0) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, true, false, true));
        }

        if (dragTicks > 0 && dragTicks % DRAG_DAMAGE_INTERVAL_TICKS == 0) {
            float dragDamage = (float) Math.max(2.0D, getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.55D);
            victim.hurt(victim.damageSources().mobAttack(this), dragDamage);
        }
    }

    private ServerPlayer resolveDraggedTarget() {
        if (draggedTarget == null || level().getServer() == null) {
            return null;
        }
        return level().getServer().getPlayerList().getPlayer(draggedTarget);
    }

    private void detectEscapeJump(ServerPlayer victim) {
        if (jumpDetectCooldown > 0) {
            jumpDetectCooldown--;
        }

        boolean jumped = draggedTargetWasGrounded && !victim.onGround() && victim.getDeltaMovement().y > 0.12D;
        if (jumped && jumpDetectCooldown <= 0) {
            escapeJumpCount++;
            jumpDetectCooldown = JUMP_DETECT_COOLDOWN_TICKS;
            draggedTargetWasGrounded = false;
            victim.displayClientMessage(Component.translatable(
                    "message.wildernessodysseyapi.riftbound_wraith_escape_progress",
                    escapeJumpCount,
                    ESCAPE_JUMPS_REQUIRED
            ), true);
            return;
        }

        if (victim.onGround()) {
            draggedTargetWasGrounded = true;
        }
    }

    private void releaseDraggedTarget(int nextState) {
        draggedTarget = null;
        dragTicks = 0;
        graspTicks = 0;
        escapeJumpCount = 0;
        jumpDetectCooldown = 0;
        draggedTargetWasGrounded = false;
        setWraithState(nextState);
    }

    private void pullVictim(ServerPlayer victim) {
        Vec3 direction = new Vec3(getX() - victim.getX(), 0.0D, getZ() - victim.getZ());
        if (direction.lengthSqr() < 0.001D) {
            return;
        }

        Vec3 pull = direction.normalize().scale(0.18D);
        victim.push(pull.x, 0.015D, pull.z);
    }

    private SoundSample findLoudestPlayer() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        List<ServerPlayer> players = serverLevel.getEntitiesOfClass(ServerPlayer.class,
                getBoundingBox().inflate(LISTEN_RANGE),
                this::isValidHuntTarget);

        SoundSample best = null;
        for (ServerPlayer player : players) {
            double score = acousticScore(player);
            if (score > 0.0D && (best == null || score > best.score())) {
                best = new SoundSample(player, score, player.blockPosition());
            }
        }
        return best;
    }

    private boolean isValidHuntTarget(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    private double acousticScore(ServerPlayer player) {
        double distance = distanceTo(player);
        if (distance > LISTEN_RANGE) {
            return 0.0D;
        }

        Vec3 movement = player.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        double sound = horizontalSpeed * 15.0D;

        if (player.isSprinting()) {
            sound += 1.1D;
        }
        if (player.isSwimming()) {
            sound += 0.7D;
        }
        if (!player.onGround()) {
            sound += 0.35D;
        }
        if (player.fallDistance > 1.25F) {
            sound += 0.75D;
        }
        if (player.isUsingItem()) {
            sound += 0.35D;
        }
        if (player.hurtTime > 0) {
            sound += 1.0D;
        }

        if (CloakState.isBreathStealthed(player)) {
            sound *= breathStealthSoundMultiplier(player, horizontalSpeed);
        }

        if (player.isCrouching()) {
            double armorNoise = CrouchNoiseHelper.getCrouchVisibilityMultiplier(player);
            sound *= Mth.clamp(0.10D + armorNoise * 0.22D, 0.06D, 0.42D);
        } else if (horizontalSpeed < 0.01D && player.onGround()) {
            sound *= 0.12D;
        }

        if (RiftfallSystem.stage().isActiveDanger()) {
            sound *= 1.2D;
        }

        double attenuation = 1.0D - distance / LISTEN_RANGE;
        return Math.max(0.0D, sound * attenuation);
    }

    private static double breathStealthSoundMultiplier(ServerPlayer player, double horizontalSpeed) {
        double multiplier = 0.0D;
        if (horizontalSpeed > 0.03D || player.isSwimming() || !player.onGround()) {
            multiplier = 0.25D;
        }
        if (player.isSprinting()) {
            multiplier = Math.max(multiplier, 0.45D);
        }
        if (player.isUsingItem() || player.hurtTime > 0) {
            multiplier = Math.max(multiplier, 0.55D);
        }
        return multiplier;
    }

    private record SoundSample(ServerPlayer player, double score, BlockPos pos) {
    }

    private final class WraithSoundGoal extends Goal {
        private WraithSoundGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public void tick() {
            if (draggedTarget != null || graspTicks > 0) {
                getNavigation().stop();
                return;
            }

            LivingEntity currentTarget = getTarget();
            if (currentTarget instanceof ServerPlayer player && isValidHuntTarget(player) && distanceTo(player) <= LISTEN_RANGE) {
                hunt(player, 1.04D);
                return;
            }

            SoundSample sample = findLoudestPlayer();
            if (sample != null && sample.score() >= INTEREST_SCORE) {
                investigatePos = sample.pos();
                attentionTicks = Math.min(160, attentionTicks + (sample.score() >= HUNT_SCORE ? 12 : 4));
                getLookControl().setLookAt(sample.player(), 30.0F, 30.0F);

                if (sample.score() >= HUNT_SCORE || attentionTicks >= 85) {
                    hunt(sample.player(), 1.0D);
                } else {
                    setWraithState(STATE_LISTENING);
                    setTarget(null);
                    getNavigation().moveTo(sample.pos().getX() + 0.5D, sample.pos().getY(), sample.pos().getZ() + 0.5D, 0.58D);
                }
                return;
            }

            if (attentionTicks > 0) {
                attentionTicks--;
            }

            if (investigatePos != null && attentionTicks > 8) {
                setWraithState(STATE_LISTENING);
                setTarget(null);
                getNavigation().moveTo(investigatePos.getX() + 0.5D, investigatePos.getY(), investigatePos.getZ() + 0.5D, 0.52D);
                if (distanceToSqr(Vec3.atCenterOf(investigatePos)) < 5.0D) {
                    investigatePos = null;
                }
            } else {
                setWraithState(STATE_VANISHED);
                setTarget(null);
                getNavigation().stop();
            }
        }

        private void hunt(ServerPlayer player, double speed) {
            attentionTicks = Math.max(attentionTicks, 80);
            setWraithState(STATE_HUNTING);
            setTarget(player);
            getLookControl().setLookAt(player, 30.0F, 30.0F);
            getNavigation().moveTo(player, speed);
        }
    }
}
