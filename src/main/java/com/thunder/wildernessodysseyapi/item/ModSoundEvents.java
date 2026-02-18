package com.thunder.wildernessodysseyapi.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Mod sound event registrations.
 */
public final class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DONT_BE_SO_SERIOUS = SOUND_EVENTS.register(
            "dont_be_so_serious",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "dont_be_so_serious"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> OUTSIDE_THE_BOX = SOUND_EVENTS.register(
            "outside_the_box",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "outside_the_box"))
    );

    private ModSoundEvents() {
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
