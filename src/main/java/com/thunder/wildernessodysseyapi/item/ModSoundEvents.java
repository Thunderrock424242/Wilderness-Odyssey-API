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

    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_PRIORITY_TRACK = SOUND_EVENTS.register(
            "anomaly_priority_track",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "anomaly_priority_track"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> ANOMALY_BIOME_MUSIC = SOUND_EVENTS.register(
            "anomaly_biome_music",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "anomaly_biome_music"))
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT_SITE_MUSIC = SOUND_EVENTS.register(
            "impact_site_music",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MOD_ID, "impact_site_music"))
    );

    // Distant-thunder events reuse Minecraft's thunder sample pool through
    // sounds.json while keeping stable profiles for non-repeating client selection.
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_THUNDER_LOW_RUMBLE = SOUND_EVENTS.register(
            "distant_thunder_low_rumble",
            () -> SoundEvent.createFixedRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "distant_thunder_low_rumble"),
                    32.0F
            )
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_THUNDER_ROLLING = SOUND_EVENTS.register(
            "distant_thunder_rolling",
            () -> SoundEvent.createFixedRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "distant_thunder_rolling"),
                    32.0F
            )
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_THUNDER_DEEP_CRACK = SOUND_EVENTS.register(
            "distant_thunder_deep_crack",
            () -> SoundEvent.createFixedRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "distant_thunder_deep_crack"),
                    32.0F
            )
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_THUNDER_LONG_RUMBLE = SOUND_EVENTS.register(
            "distant_thunder_long_rumble",
            () -> SoundEvent.createFixedRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "distant_thunder_long_rumble"),
                    32.0F
            )
    );

    // Coastal events layer Minecraft's existing water samples through
    // sounds.json. The event identities and attenuation ranges stay stable so
    // bespoke recorded surf assets can replace the aliases later without code changes.
    public static final DeferredHolder<SoundEvent, SoundEvent> COAST_WASH_SOFT = fixedRange(
            "coast_wash_soft", 22.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> COAST_BREAK = fixedRange(
            "coast_break", 28.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> COAST_BREAK_ROCKY = fixedRange(
            "coast_break_rocky", 36.0F);
    public static final DeferredHolder<SoundEvent, SoundEvent> COAST_BREAK_STORM = fixedRange(
            "coast_break_storm", 48.0F);

    private ModSoundEvents() {
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> fixedRange(String name, float range) {
        return SOUND_EVENTS.register(
                name,
                () -> SoundEvent.createFixedRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(MOD_ID, name),
                        range
                )
        );
    }
}
