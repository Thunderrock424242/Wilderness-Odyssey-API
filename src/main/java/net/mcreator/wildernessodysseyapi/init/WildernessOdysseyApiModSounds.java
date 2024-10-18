
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.wildernessodysseyapi.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import net.mcreator.wildernessodysseyapi.WildernessOdysseyApiMod;

public class WildernessOdysseyApiModSounds {
	public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(Registries.SOUND_EVENT, WildernessOdysseyApiMod.MODID);
	public static final DeferredHolder<SoundEvent, SoundEvent> PLAYING_WITH_LIGHT = REGISTRY.register("playing_with_light",
			() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("wilderness_odyssey_api", "playing_with_light")));
	public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLES_DROP = REGISTRY.register("bubbles_drop", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("wilderness_odyssey_api", "bubbles_drop")));
}
