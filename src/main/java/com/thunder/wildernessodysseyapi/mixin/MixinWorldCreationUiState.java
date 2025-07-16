package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldCreationUiState.class)
/**
 * Forces the large biomes preset to be selected by default.
 */
public class MixinWorldCreationUiState {

    /**
     * After preset lists are populated, switch to the large biomes preset.
     */
    @Inject(method = "updatePresetLists", at = @At("TAIL"))
    private void forceLargeBiomesPreset(CallbackInfo ci) {
        WorldCreationUiState state = (WorldCreationUiState)(Object)this;
        WorldCreationContext context = state.getSettings();

        HolderGetter<WorldPreset> getter = context.worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET);

        getter.get(WorldPresets.LARGE_BIOMES).ifPresent(holder -> {
            state.setWorldType(new WorldCreationUiState.WorldTypeEntry(holder));
        });
    }
}
