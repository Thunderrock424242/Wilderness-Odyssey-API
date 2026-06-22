package com.thunder.wildernessodysseyapi.cloak.item;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.core.ModRegistries;
import com.thunder.wildernessodysseyapi.cloak.item.module.EchoMaskModuleModifiers;
import com.thunder.wildernessodysseyapi.cloak.item.module.EchoMaskModuleStorage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class CloakTickHandler {
    private static final int BUBBLE_TICKS = 30;
    private static final int LOW_OXYGEN_TICKS = BUBBLE_TICKS * 2;
    private static final int ECHO_HYPOXIA_AIR_TICKS = BUBBLE_TICKS;
    private static final int BREATH_HOLD_AIR_DRAIN_PER_TICK = 5;
    private static final int CLOAK_AIR_DRAIN_PER_TICK = 5;
    private static final int UNDERWATER_CLOAK_EXTRA_DRAIN = 1;
    private static final int BREATHING_MASK_DURABILITY_INTERVAL_TICKS = 80;
    private static final int NO_MASK_CRYO_SHAKES_TICKS = 20 * 6;
    private static final int NO_MASK_ECHO_HYPOXIA_TICKS = 20 * 10;
    private static final int MASK_SAFE_CLOAK_TICKS = 20 * 90;
    private static final int MASK_ECHO_HYPOXIA_TICKS = MASK_SAFE_CLOAK_TICKS + 20 * 20;
    private static final int MASK_PHASE_REJECTION_TICKS = MASK_SAFE_CLOAK_TICKS + 20 * 30;
    private static final int MASK_WARNING_INTERVAL_TICKS = 20 * 15;
    private static final int MASK_COOLDOWN_TICKS = 20 * 45;
    private static final double ECHO_HYPOXIA_HUNT_RANGE = 40.0D;
    private static final double PHASE_REJECTION_ALERT_RANGE = 56.0D;

    private CloakTickHandler() {
    }

    public static boolean tryToggleCloak(Player player) {
        if (player.level().isClientSide) {
            return true;
        }

        if (CloakState.isCloaked(player)) {
            disableCloak(player, true);
            return true;
        }

        if (CloakState.isMaskCoolingDown(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.wildernessodysseyapi.echo_mask_cooling",
                    Math.max(1, CloakState.getMaskCooldownTicks(player) / 20)
            ), true);
            return false;
        }

        CloakState.beginCloakSession(player);
        player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.cloak_enabled"), true);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        CloakState.tickMaskCooldown(player);

        boolean holdingBreath = CloakState.isHoldingBreath(player);
        ItemStack maskStack = getEquippedEchoMask(player);
        boolean wearingBreathingMask = !maskStack.isEmpty();
        boolean wasHoldingBreath = CloakState.wasHoldingBreath(player);
        int maxBreath = CloakState.getCurrentMaxBreath(player);

        if (holdingBreath && !wasHoldingBreath && !wearingBreathingMask && !CloakState.isCloaked(player)) {
            CloakState.incrementBreathPenalty(player);
            maxBreath = CloakState.getCurrentMaxBreath(player);
            if (player.getAirSupply() > maxBreath) {
                player.setAirSupply(maxBreath);
            }
        }

        CloakState.setWasHoldingBreath(player, holdingBreath);
        if (!holdingBreath) {
            CloakState.resetBreathMaskSession(player);
        }

        if (holdingBreath && !CloakState.isCloaked(player)) {
            if (wearingBreathingMask && !CloakState.isMaskCoolingDown(player)) {
                tickMaskedBreath(player, maskStack);
            } else {
                CloakState.resetBreathMaskSession(player);
                drainAir(player, BREATH_HOLD_AIR_DRAIN_PER_TICK);
                applyLowOxygenDarkness(player);
                hurtForNoAir(player);
            }
            return;
        }

        if (!CloakState.isCloaked(player)) {
            recoverAir(player, maxBreath);
            return;
        }

        if (wearingBreathingMask && CloakState.isMaskCoolingDown(player)) {
            triggerPhaseRejection(player, maskStack);
            return;
        }

        CloakState.refreshIfNeeded(player);
        int cloakTicks = CloakState.incrementCloakSessionTicks(player);
        spawnCloakDistortion(player, cloakTicks, maskStack);

        if (wearingBreathingMask) {
            tickMaskedCloak(player, maskStack, cloakTicks);
        } else {
            tickUnmaskedCloak(player, cloakTicks);
        }
    }

    private static ItemStack getEquippedEchoMask(Player player) {
        ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
        if (BreathingMaskItem.isEchoBreathingMask(headStack)) {
            return headStack;
        }

        return ItemStack.EMPTY;
    }

    private static void spawnCloakDistortion(Player player, int cloakTicks, ItemStack mask) {
        if (!(player.level() instanceof ServerLevel serverLevel) || player.tickCount % 8 != 0) {
            return;
        }

        double intensity = !mask.isEmpty() && cloakTicks <= getSafeUseTicks(mask) ? 0.55D : 1.0D;
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ(),
                Math.max(1, (int) Math.round(3 * intensity)),
                0.32D, player.getBbHeight() * 0.35D, 0.32D, 0.025D);
        serverLevel.sendParticles(ParticleTypes.WITCH,
                player.getX(), player.getY() + player.getBbHeight() * 0.55D, player.getZ(),
                Math.max(1, (int) Math.round(2 * intensity)),
                0.22D, player.getBbHeight() * 0.28D, 0.22D, 0.01D);
    }

    private static void tickMaskedBreath(Player player, ItemStack mask) {
        int breathTicks = CloakState.incrementBreathMaskSessionTicks(player);
        damageBreathingMask(player, mask);

        if (breathTicks <= getSafeUseTicks(mask)) {
            player.setAirSupply(player.getMaxAirSupply());
            return;
        }

        sendBreathMaskOveruseWarningIfNeeded(player, breathTicks);
        player.addEffect(new MobEffectInstance(
                ModRegistries.CRYO_SHAKES_EFFECT,
                80,
                0,
                true,
                false,
                true
        ));

        if (player.tickCount % 6 == 0) {
            drainAir(player, getMaskedOveruseAirDrain(mask));
        }

        if (breathTicks >= getEchoHypoxiaTicks(mask)) {
            applyEchoHypoxia(player, LOW_OXYGEN_TICKS);
            if (player instanceof ServerPlayer serverPlayer && player.tickCount % 60 == 0) {
                CloakHunterTargets.alertNearbyHunters(serverPlayer, getHunterRange(mask, ECHO_HYPOXIA_HUNT_RANGE));
            }
        }

        if (breathTicks >= getPhaseRejectionTicks(mask)) {
            triggerPhaseRejection(player, mask);
        }
    }

    private static void tickUnmaskedCloak(Player player, int cloakTicks) {
        drainAir(player, CLOAK_AIR_DRAIN_PER_TICK + (player.isUnderWater() ? UNDERWATER_CLOAK_EXTRA_DRAIN : 0));
        applyLowOxygenDarkness(player);

        if (cloakTicks >= NO_MASK_CRYO_SHAKES_TICKS || player.getAirSupply() <= LOW_OXYGEN_TICKS) {
            player.addEffect(new MobEffectInstance(
                    ModRegistries.CRYO_SHAKES_EFFECT,
                    80,
                    0,
                    true,
                    false,
                    true
            ));
        }

        if (cloakTicks >= NO_MASK_ECHO_HYPOXIA_TICKS || player.getAirSupply() <= ECHO_HYPOXIA_AIR_TICKS) {
            applyEchoHypoxia(player, ECHO_HYPOXIA_AIR_TICKS);
            if (player instanceof ServerPlayer serverPlayer && player.tickCount % 40 == 0) {
                CloakHunterTargets.alertNearbyHunters(serverPlayer, ECHO_HYPOXIA_HUNT_RANGE);
            }
        }

        hurtForNoAir(player);
    }

    private static void tickMaskedCloak(Player player, ItemStack mask, int cloakTicks) {
        damageBreathingMask(player, mask);

        if (cloakTicks <= getSafeUseTicks(mask)) {
            player.setAirSupply(player.getMaxAirSupply());
            return;
        }

        sendMaskOveruseWarningIfNeeded(player, cloakTicks);
        player.addEffect(new MobEffectInstance(
                ModRegistries.CRYO_SHAKES_EFFECT,
                80,
                0,
                true,
                false,
                true
        ));

        if (player.tickCount % 6 == 0) {
            drainAir(player, getMaskedOveruseAirDrain(mask));
        }

        if (cloakTicks >= getEchoHypoxiaTicks(mask)) {
            applyEchoHypoxia(player, LOW_OXYGEN_TICKS);
            if (player instanceof ServerPlayer serverPlayer && player.tickCount % 60 == 0) {
                CloakHunterTargets.alertNearbyHunters(serverPlayer, getHunterRange(mask, ECHO_HYPOXIA_HUNT_RANGE));
            }
        }

        if (cloakTicks >= getPhaseRejectionTicks(mask)) {
            triggerPhaseRejection(player, mask);
        }
    }

    private static void sendMaskOveruseWarningIfNeeded(Player player, int cloakTicks) {
        int lastWarningTicks = CloakState.getLastMaskWarningTicks(player);
        if (lastWarningTicks == 0 || cloakTicks - lastWarningTicks >= MASK_WARNING_INTERVAL_TICKS) {
            player.sendSystemMessage(Component.translatable("message.wildernessodysseyapi.echo_mask_overuse_warning"));
            CloakState.setLastMaskWarningTicks(player, cloakTicks);
        }
    }

    private static void sendBreathMaskOveruseWarningIfNeeded(Player player, int breathTicks) {
        int lastWarningTicks = CloakState.getLastBreathMaskWarningTicks(player);
        if (lastWarningTicks == 0 || breathTicks - lastWarningTicks >= MASK_WARNING_INTERVAL_TICKS) {
            player.sendSystemMessage(Component.translatable("message.wildernessodysseyapi.echo_mask_breath_overuse_warning"));
            CloakState.setLastBreathMaskWarningTicks(player, breathTicks);
        }
    }

    private static void applyEchoHypoxia(Player player, int airCeilingTicks) {
        player.addEffect(new MobEffectInstance(
                ModRegistries.ECHO_HYPOXIA_EFFECT,
                100,
                0,
                true,
                false,
                true
        ));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0, true, false, true));
        if (player.getAirSupply() > airCeilingTicks) {
            player.setAirSupply(airCeilingTicks);
        }
    }

    private static void triggerPhaseRejection(Player player, ItemStack mask) {
        disableCloak(player, false);
        CloakState.resetBreathMaskSession(player);
        CloakState.setMaskCooldownTicks(player, getMaskCooldownTicks(mask));
        player.addEffect(new MobEffectInstance(
                ModRegistries.DESYNCED_EFFECT,
                getMaskCooldownTicks(mask),
                0,
                false,
                true,
                true
        ));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, true, false, true));
        player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.phase_rejection"), false);

        if (!mask.isEmpty() && !player.isCreative()) {
            mask.hurtAndBreak(getMaskDamageAmount(mask, 20), player, EquipmentSlot.HEAD);
        }

        if (player instanceof ServerPlayer serverPlayer && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(),
                    90, 1.4D, 0.8D, 1.4D, 0.12D);
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(),
                    45, 0.8D, 0.6D, 0.8D, 0.08D);
            CloakHunterTargets.alertNearbyHunters(serverPlayer, getHunterRange(mask, PHASE_REJECTION_ALERT_RANGE));
        }
    }

    private static void disableCloak(Player player, boolean showMessage) {
        CloakState.endCloakSession(player);
        if (showMessage) {
            player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.cloak_disabled"), true);
        }
    }

    private static void recoverAir(Player player, int maxBreath) {
        if (player.hasEffect(ModRegistries.ECHO_HYPOXIA_EFFECT)) {
            if (player.getAirSupply() > ECHO_HYPOXIA_AIR_TICKS) {
                player.setAirSupply(ECHO_HYPOXIA_AIR_TICKS);
            }
            return;
        }

        if (!player.isUnderWater() && player.getAirSupply() < maxBreath) {
            player.setAirSupply(Math.min(maxBreath, player.getAirSupply() + 2));
        }
    }

    private static void drainAir(Player player, int amount) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        player.setAirSupply(Math.max(0, player.getAirSupply() - amount));
    }

    private static void applyLowOxygenDarkness(Player player) {
        if (player.getAirSupply() <= LOW_OXYGEN_TICKS) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false, true));
        }
    }

    private static void hurtForNoAir(Player player) {
        if (player.isCreative() || player.isSpectator() || player.getAirSupply() > 0 || player.tickCount % 20 != 0) {
            return;
        }

        player.hurt(player.damageSources().drown(), 2.0F);
    }

    private static void damageBreathingMask(Player player, ItemStack mask) {
        if (player.isCreative() || player.tickCount % BREATHING_MASK_DURABILITY_INTERVAL_TICKS != 0) {
            return;
        }

        mask.hurtAndBreak(getMaskDamageAmount(mask, 1), player, EquipmentSlot.HEAD);
    }

    private static int getSafeUseTicks(ItemStack mask) {
        return MASK_SAFE_CLOAK_TICKS + getModuleModifiers(mask).safeUseTicksBonus();
    }

    private static int getEchoHypoxiaTicks(ItemStack mask) {
        return getSafeUseTicks(mask) + 20 * 20 + getModuleModifiers(mask).echoHypoxiaTicksBonus();
    }

    private static int getPhaseRejectionTicks(ItemStack mask) {
        return getSafeUseTicks(mask) + 20 * 30 + getModuleModifiers(mask).phaseRejectionTicksBonus();
    }

    private static int getMaskCooldownTicks(ItemStack mask) {
        return Math.max(20 * 5, MASK_COOLDOWN_TICKS - getModuleModifiers(mask).cooldownReductionTicks());
    }

    private static int getMaskedOveruseAirDrain(ItemStack mask) {
        return Math.max(1, (int) Math.ceil(getModuleModifiers(mask).oxygenDrainMultiplier()));
    }

    private static double getHunterRange(ItemStack mask, double baseRange) {
        return baseRange * getModuleModifiers(mask).hunterRangeMultiplier();
    }

    private static int getMaskDamageAmount(ItemStack mask, int baseDamage) {
        return Math.max(0, (int) Math.ceil(baseDamage * getModuleModifiers(mask).maskDamageMultiplier()));
    }

    private static EchoMaskModuleModifiers getModuleModifiers(ItemStack mask) {
        return mask.isEmpty() ? EchoMaskModuleModifiers.NONE : EchoMaskModuleStorage.combinedModifiers(mask);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        CloakState.setHoldingBreath(player, false);
        CloakState.setWasHoldingBreath(player, false);
        CloakState.resetBreathMaskSession(player);
        if (CloakState.isCloaked(player)) {
            CloakState.endCloakSession(player);
        }
    }
}
