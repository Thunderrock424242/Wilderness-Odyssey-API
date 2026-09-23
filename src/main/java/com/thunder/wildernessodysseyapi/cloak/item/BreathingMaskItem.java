package com.thunder.wildernessodysseyapi.cloak.item;

import com.thunder.wildernessodysseyapi.cloak.item.module.EchoMaskModule;
import com.thunder.wildernessodysseyapi.cloak.item.module.EchoMaskModuleModifiers;
import com.thunder.wildernessodysseyapi.cloak.item.module.EchoMaskModuleStorage;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class BreathingMaskItem extends ArmorItem {
    public static final int BASE_MODULE_SLOTS = 3;

    public BreathingMaskItem(Properties properties) {
        super(ArmorMaterials.NETHERITE, Type.HELMET, properties);
    }

    public static boolean isEchoBreathingMask(ItemStack stack) {
        return stack.getItem() instanceof BreathingMaskItem;
    }

    public int getBaseModuleSlots() {
        return BASE_MODULE_SLOTS;
    }

    public int getModuleSlotCapacity(ItemStack stack) {
        return EchoMaskModuleStorage.maxSlots(stack);
    }

    public int getUsedModuleSlots(ItemStack stack) {
        return EchoMaskModuleStorage.usedSlots(stack);
    }

    public List<EchoMaskModule> getInstalledModules(ItemStack stack) {
        return EchoMaskModuleStorage.installedModules(stack);
    }

    public EchoMaskModuleModifiers getInstalledModuleModifiers(ItemStack stack) {
        return EchoMaskModuleStorage.combinedModifiers(stack);
    }
}
