package com.thunder.wildernessodysseyapi.item.cloak.module;

import com.thunder.wildernessodysseyapi.item.cloak.BreathingMaskItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Dependency-free Curios bridge. Real Curios slot registration can attach here later.
 */
public final class EchoMaskCuriosBridge {
    public static final String CURIOS_MOD_ID = "curios";
    public static final String FUTURE_SLOT_ID = "echo_breathing_mask";

    private EchoMaskCuriosBridge() {
    }

    public static boolean isCuriosLoaded() {
        return ModList.get().isLoaded(CURIOS_MOD_ID);
    }

    public static boolean canUseFutureCuriosSlot(ItemStack stack) {
        return isCuriosLoaded() && BreathingMaskItem.isEchoBreathingMask(stack);
    }
}
