package com.thunder.wildernessodysseyapi.crouching;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CrouchNoiseHelper {
    public static final String SILENT_ARMOR_TAG = "wildernessodysseyapi:silent_armor";
    private static final float LEATHER_MULTIPLIER = 0.5F;
    private static final float CHAIN_MULTIPLIER = 0.6F;
    private static final float IRON_MULTIPLIER = 0.7F;
    private static final float GOLD_MULTIPLIER = 0.8F;
    private static final float DIAMOND_MULTIPLIER = 0.9F;
    private static final float NETHERITE_MULTIPLIER = 1.0F;

    private CrouchNoiseHelper() {
    }

    public static float getCrouchVisibilityMultiplier(Player player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty()) {
            return 0.0F;
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
        if (!stack.hasTag()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(SILENT_ARMOR_TAG);
    }
}
