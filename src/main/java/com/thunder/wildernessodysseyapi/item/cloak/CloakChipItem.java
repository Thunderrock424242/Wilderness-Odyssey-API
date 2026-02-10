package com.thunder.wildernessodysseyapi.item.cloak;

import com.thunder.wildernessodysseyapi.item.chip.ChipSetItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

public class CloakChipItem extends ChipSetItem {
    public CloakChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        super.onEquip(slotContext, prevStack, stack);

        if (slotContext.entity().level().isClientSide) {
            return;
        }

        if (slotContext.entity() instanceof Player player) {
            player.sendSystemMessage(Component.translatable("message.wildernessodysseyapi.cloak_chip_equipped"));
        }
    }
}
