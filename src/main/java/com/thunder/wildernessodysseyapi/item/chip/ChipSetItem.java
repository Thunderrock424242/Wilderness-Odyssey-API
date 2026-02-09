package com.thunder.wildernessodysseyapi.item.chip;

import com.thunder.wildernessodysseyapi.item.ModItemTags;
import com.thunder.wildernessodysseyapi.curios.CuriosIntegration;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class ChipSetItem extends Item implements ICurioItem {
    public static final String CHIP_SET_SLOT = "chip_set";
    private static final int NAUSEA_DURATION_TICKS = 20 * 20;
    private static final float CHIP_DAMAGE = 2.0F;

    public ChipSetItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        applyChipSetEffects(slotContext, stack);
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        applyChipSetEffects(slotContext, stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), true);
        }

        boolean equipped = CuriosIntegration.equipFromHand(player, hand, CHIP_SET_SLOT);
        if (equipped) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), false);
        }

        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    private void applyChipSetEffects(SlotContext slotContext, ItemStack stack) {
        if (!stack.is(ModItemTags.CHIP_SET)) {
            return;
        }

        LivingEntity wearer = slotContext.entity();
        if (wearer.level().isClientSide) {
            return;
        }

        wearer.hurt(wearer.damageSources().magic(), CHIP_DAMAGE);
        wearer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_DURATION_TICKS, 0));
    }
}
