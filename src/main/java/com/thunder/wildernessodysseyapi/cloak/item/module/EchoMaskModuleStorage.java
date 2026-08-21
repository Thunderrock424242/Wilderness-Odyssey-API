package com.thunder.wildernessodysseyapi.cloak.item.module;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.cloak.item.BreathingMaskItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data-component storage helpers for extension-owned Echo-mask module interactions.
 *
 * <p>All mutations require an already registered module and operate only on the supplied stack;
 * the base mod intentionally provides no automatic or hidden installation path.</p>
 */
public final class EchoMaskModuleStorage {
    public static final String MODULES_TAG = ModConstants.MOD_ID + ":echo_mask_modules";

    private EchoMaskModuleStorage() {
    }

    public static List<ResourceLocation> installedModuleIds(ItemStack mask) {
        CustomData customData = mask.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return List.of();
        }

        CompoundTag tag = customData.copyTag();
        ListTag list = tag.getList(MODULES_TAG, StringTag.TAG_STRING);
        List<ResourceLocation> modules = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            try {
                modules.add(ResourceLocation.parse(list.getString(i)));
            } catch (RuntimeException ignored) {
                // Ignore stale or malformed module ids so one bad entry does not break the mask.
            }
        }
        return List.copyOf(modules);
    }

    public static List<EchoMaskModule> installedModules(ItemStack mask) {
        return installedModuleIds(mask).stream()
                .map(EchoMaskModuleRegistry::get)
                .flatMap(Optional::stream)
                .toList();
    }

    public static EchoMaskModuleModifiers combinedModifiers(ItemStack mask) {
        return EchoMaskModuleModifiers.combine(installedModules(mask));
    }

    public static int usedSlots(ItemStack mask) {
        return installedModules(mask).stream().mapToInt(EchoMaskModule::slotCost).sum();
    }

    public static int maxSlots(ItemStack mask) {
        return BreathingMaskItem.BASE_MODULE_SLOTS + Math.max(0, combinedModifiers(mask).extraSlots());
    }

    public static boolean canInstall(ItemStack mask, EchoMaskModule module) {
        if (!BreathingMaskItem.isEchoBreathingMask(mask) || contains(mask, module.id())) {
            return false;
        }
        return usedSlots(mask) + module.slotCost() <= maxSlots(mask);
    }

    public static boolean install(ItemStack mask, EchoMaskModule module) {
        if (!canInstall(mask, module)) {
            return false;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, mask, tag -> {
            ListTag list = tag.getList(MODULES_TAG, StringTag.TAG_STRING);
            list.add(StringTag.valueOf(module.id().toString()));
            tag.put(MODULES_TAG, list);
        });
        return true;
    }

    public static boolean remove(ItemStack mask, ResourceLocation moduleId) {
        if (!contains(mask, moduleId)) {
            return false;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, mask, tag -> {
            ListTag oldList = tag.getList(MODULES_TAG, StringTag.TAG_STRING);
            ListTag newList = new ListTag();
            for (int i = 0; i < oldList.size(); i++) {
                if (!moduleId.toString().equals(oldList.getString(i))) {
                    newList.add(StringTag.valueOf(oldList.getString(i)));
                }
            }
            tag.put(MODULES_TAG, newList);
        });
        return true;
    }

    public static boolean contains(ItemStack mask, ResourceLocation moduleId) {
        return installedModuleIds(mask).contains(moduleId);
    }
}
