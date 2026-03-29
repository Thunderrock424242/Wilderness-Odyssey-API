package com.thunder.wildernessodysseyapi.item.neural;

import com.thunder.wildernessodysseyapi.core.ModDamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Curio item that cannot be removed safely; forced unequip deals fatal
 * neural-frame removal damage.
 */
public class NeuralFrameItem extends Item implements ICurioItem {
    public NeuralFrameItem(Properties properties) {
        super(properties);
    }

    /**
     * Neural frames are intentionally locked once equipped.
     */
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return false;
    }

    /**
     * Applies configured neural-frame removal damage when an unequip occurs.
     */
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        LivingEntity wearer = slotContext.entity();
        if (wearer.level().isClientSide) {
            return;
        }

        wearer.hurt(wearer.damageSources().source(ModDamageTypes.NEURAL_FRAME_REMOVAL), Float.MAX_VALUE);
    }
}
