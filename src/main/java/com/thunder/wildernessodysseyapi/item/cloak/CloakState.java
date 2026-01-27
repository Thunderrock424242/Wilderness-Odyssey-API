package com.thunder.wildernessodysseyapi.item.cloak;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public final class CloakState {
    private static final String CLOAK_TAG = "wildernessodysseyapi_cloak_active";
    private static final int REFRESH_DURATION_TICKS = 220;

    private CloakState() {
    }

    public static boolean isCloaked(Player player) {
        return player.getPersistentData().getBoolean(CLOAK_TAG);
    }

    public static void setCloaked(Player player, boolean enabled) {
        CompoundTag data = player.getPersistentData();
        data.putBoolean(CLOAK_TAG, enabled);
    }

    public static void applyCloak(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, REFRESH_DURATION_TICKS, 0, false, false, true));
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
