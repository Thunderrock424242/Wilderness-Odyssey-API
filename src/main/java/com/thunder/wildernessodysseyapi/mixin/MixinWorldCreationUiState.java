package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(WorldCreationUiState.class)
public class MixinWorldCreationUiState {

    @Inject(method = "updatePresetLists", at = @At("TAIL"))
    private void forceLargeBiomesPreset(CallbackInfo ci) {
        WorldCreationUiState state = (WorldCreationUiState)(Object)this;
        WorldCreationContext context = state.getSettings();

        Registry<WorldPreset> presetRegistry = (Registry<WorldPreset>) context.worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET);

        // Get the large_biomes preset directly
        WorldPreset preset = presetRegistry.get(WorldPresets.LARGE_BIOMES);

        if (preset != null) {
            state.setWorldType(new WorldCreationUiState.WorldTypeEntry((Holder<WorldPreset>) preset));
        }
    }
}