package com.thunder.wildernessodysseyapi.item.cloak;

import com.thunder.wildernessodysseyapi.config.CloakChipConfig;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class CloakChipItem extends Item implements ICurioItem {
    public CloakChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        applySideEffects(slotContext);
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        applySideEffects(slotContext);
    }

    private void applySideEffects(SlotContext slotContext) {
        LivingEntity wearer = slotContext.entity();
        if (wearer.level().isClientSide) {
            return;
        }

        wearer.hurt(wearer.damageSources().magic(), 2.0F);
        if (CloakChipConfig.ENABLE_NAUSEA.get()) {
            wearer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, CloakChipConfig.NAUSEA_DURATION_TICKS, 0));
        }
    }
}
