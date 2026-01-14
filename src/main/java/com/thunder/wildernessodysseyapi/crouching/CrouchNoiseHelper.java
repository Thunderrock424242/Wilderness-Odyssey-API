package com.thunder.wildernessodysseyapi.crouching;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class CrouchNoiseHelper {
    public static final String SILENT_ARMOR_TAG = "wildernessodysseyapi:silent_armor";
    private static final double LEATHER_MULTIPLIER = 0.5D;
    private static final double CHAIN_MULTIPLIER = 0.6D;
    private static final double IRON_MULTIPLIER = 0.7D;
    private static final double GOLD_MULTIPLIER = 0.8D;
    private static final double DIAMOND_MULTIPLIER = 0.9D;
    private static final double NETHERITE_MULTIPLIER = 1.0D;

    private CrouchNoiseHelper() {
    }

    public static double getCrouchVisibilityMultiplier(Player player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty()) {
            return 0.0D;
        }
        if (isSilenced(boots)) {
            return LEATHER_MULTIPLIER;
        }
        if (boots.getItem() == Items.LEATHER_BOOTS) {
            return LEATHER_MULTIPLIER;
        }
        if (boots.getItem() == Items.CHAINMAIL_BOOTS) {
            return CHAIN_MULTIPLIER;
        }
        if (boots.getItem() == Items.IRON_BOOTS) {
            return IRON_MULTIPLIER;
        }
        if (boots.getItem() == Items.GOLDEN_BOOTS) {
            return GOLD_MULTIPLIER;
        }
        if (boots.getItem() == Items.DIAMOND_BOOTS) {
            return DIAMOND_MULTIPLIER;
        }
        if (boots.getItem() == Items.NETHERITE_BOOTS) {
            return NETHERITE_MULTIPLIER;
        }
        if (boots.getItem() instanceof ArmorItem) {
            return NETHERITE_MULTIPLIER;
        }
        return LEATHER_MULTIPLIER;
    }

    private static boolean isSilenced(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().getBoolean(SILENT_ARMOR_TAG);
    }
}
