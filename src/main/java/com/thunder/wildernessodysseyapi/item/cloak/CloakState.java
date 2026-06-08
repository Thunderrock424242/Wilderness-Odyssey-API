package com.thunder.wildernessodysseyapi.item.cloak;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public final class CloakState {
    private static final String CLOAK_TAG = "wildernessodysseyapi_cloak_active";
    private static final String CLOAK_HOLDING_BREATH_TAG = "wildernessodysseyapi_cloak_holding_breath";
    private static final String CLOAK_PREV_HOLDING_BREATH_TAG = "wildernessodysseyapi_cloak_prev_holding_breath";
    private static final String CLOAK_BREATH_PENALTY_TAG = "wildernessodysseyapi_cloak_breath_penalty";
    private static final String CLOAK_SESSION_TICKS_TAG = "wildernessodysseyapi_cloak_session_ticks";
    private static final String CLOAK_MASK_LAST_WARNING_TICKS_TAG = "wildernessodysseyapi_cloak_mask_last_warning_ticks";
    private static final String BREATH_MASK_SESSION_TICKS_TAG = "wildernessodysseyapi_breath_mask_session_ticks";
    private static final String BREATH_MASK_LAST_WARNING_TICKS_TAG = "wildernessodysseyapi_breath_mask_last_warning_ticks";
    private static final String CLOAK_MASK_COOLDOWN_TICKS_TAG = "wildernessodysseyapi_cloak_mask_cooldown_ticks";
    private static final int REFRESH_DURATION_TICKS = 220;
    private static final int BASE_BREATH_TICKS = 300;
    private static final int BREATH_LOSS_PER_USE = 20;
    private static final int MIN_BREATH_TICKS = 60;

    private CloakState() {
    }

    public static boolean isCloaked(Player player) {
        return player.getPersistentData().getBoolean(CLOAK_TAG);
    }

    public static void setCloaked(Player player, boolean enabled) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(CLOAK_TAG, enabled);
    }

    public static void beginCloakSession(Player player) {
        setCloaked(player, true);
        setCloakSessionTicks(player, 0);
        setLastMaskWarningTicks(player, 0);
        applyCloak(player);
    }

    public static void endCloakSession(Player player) {
        setCloaked(player, false);
        setCloakSessionTicks(player, 0);
        setLastMaskWarningTicks(player, 0);
        clearCloak(player);
    }

    public static boolean isHoldingBreath(Player player) {
        return player.getPersistentData().getBoolean(CLOAK_HOLDING_BREATH_TAG);
    }

    public static boolean isBreathStealthed(Player player) {
        return isHoldingBreath(player) && player.getAirSupply() > 0;
    }

    public static void setHoldingBreath(Player player, boolean holdingBreath) {
        player.getPersistentData().putBoolean(CLOAK_HOLDING_BREATH_TAG, holdingBreath);
    }

    public static boolean wasHoldingBreath(Player player) {
        return player.getPersistentData().getBoolean(CLOAK_PREV_HOLDING_BREATH_TAG);
    }

    public static void setWasHoldingBreath(Player player, boolean holdingBreath) {
        player.getPersistentData().putBoolean(CLOAK_PREV_HOLDING_BREATH_TAG, holdingBreath);
    }

    public static int getBreathPenalty(Player player) {
        return Math.max(0, player.getPersistentData().getInt(CLOAK_BREATH_PENALTY_TAG));
    }

    public static void incrementBreathPenalty(Player player) {
        int current = getBreathPenalty(player);
        int maxPenalty = (BASE_BREATH_TICKS - MIN_BREATH_TICKS) / BREATH_LOSS_PER_USE;
        player.getPersistentData().putInt(CLOAK_BREATH_PENALTY_TAG, Math.min(maxPenalty, current + 1));
    }

    public static void reduceBreathPenalty(Player player) {
        int current = getBreathPenalty(player);
        player.getPersistentData().putInt(CLOAK_BREATH_PENALTY_TAG, Math.max(0, current - 1));
        setCloakSessionTicks(player, Math.max(0, getCloakSessionTicks(player) - 120));
    }

    public static int getCurrentMaxBreath(Player player) {
        int maxBreath = BASE_BREATH_TICKS - (getBreathPenalty(player) * BREATH_LOSS_PER_USE);
        return Math.max(MIN_BREATH_TICKS, maxBreath);
    }

    public static int getCloakSessionTicks(Player player) {
        return Math.max(0, player.getPersistentData().getInt(CLOAK_SESSION_TICKS_TAG));
    }

    public static int incrementCloakSessionTicks(Player player) {
        int ticks = getCloakSessionTicks(player) + 1;
        setCloakSessionTicks(player, ticks);
        return ticks;
    }

    public static void setCloakSessionTicks(Player player, int ticks) {
        player.getPersistentData().putInt(CLOAK_SESSION_TICKS_TAG, Math.max(0, ticks));
    }

    public static int getLastMaskWarningTicks(Player player) {
        return Math.max(0, player.getPersistentData().getInt(CLOAK_MASK_LAST_WARNING_TICKS_TAG));
    }

    public static void setLastMaskWarningTicks(Player player, int ticks) {
        player.getPersistentData().putInt(CLOAK_MASK_LAST_WARNING_TICKS_TAG, Math.max(0, ticks));
    }

    public static int getBreathMaskSessionTicks(Player player) {
        return Math.max(0, player.getPersistentData().getInt(BREATH_MASK_SESSION_TICKS_TAG));
    }

    public static int incrementBreathMaskSessionTicks(Player player) {
        int ticks = getBreathMaskSessionTicks(player) + 1;
        setBreathMaskSessionTicks(player, ticks);
        return ticks;
    }

    public static void setBreathMaskSessionTicks(Player player, int ticks) {
        player.getPersistentData().putInt(BREATH_MASK_SESSION_TICKS_TAG, Math.max(0, ticks));
    }

    public static int getLastBreathMaskWarningTicks(Player player) {
        return Math.max(0, player.getPersistentData().getInt(BREATH_MASK_LAST_WARNING_TICKS_TAG));
    }

    public static void setLastBreathMaskWarningTicks(Player player, int ticks) {
        player.getPersistentData().putInt(BREATH_MASK_LAST_WARNING_TICKS_TAG, Math.max(0, ticks));
    }

    public static void resetBreathMaskSession(Player player) {
        setBreathMaskSessionTicks(player, 0);
        setLastBreathMaskWarningTicks(player, 0);
    }

    public static int getMaskCooldownTicks(Player player) {
        return Math.max(0, player.getPersistentData().getInt(CLOAK_MASK_COOLDOWN_TICKS_TAG));
    }

    public static void setMaskCooldownTicks(Player player, int ticks) {
        player.getPersistentData().putInt(CLOAK_MASK_COOLDOWN_TICKS_TAG, Math.max(0, ticks));
    }

    public static boolean isMaskCoolingDown(Player player) {
        return getMaskCooldownTicks(player) > 0;
    }

    public static void tickMaskCooldown(Player player) {
        int ticks = getMaskCooldownTicks(player);
        if (ticks > 0) {
            setMaskCooldownTicks(player, ticks - 1);
        }
    }

    public static void applyCloak(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, REFRESH_DURATION_TICKS, 0, false, false, false));
    }

    public static void clearCloak(Player player) {
        player.removeEffect(MobEffects.INVISIBILITY);
    }

    public static void refreshIfNeeded(Player player) {
        MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
        if (current == null || current.getDuration() < 40) {
            applyCloak(player);
        }
    }
}
