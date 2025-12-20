package com.thunder.wildernessodysseyapi.mixin;

import com.natamus.starterstructure_common_neoforge.config.ConfigHandler;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces Starter Structure to keep entity spawning enabled so tile-entity-powered
 * machines inside the bunker are not stripped when the schematic is placed.
 */
@Mixin(value = ConfigHandler.class, remap = false)
public class StarterStructureConfigMixin {
    private static boolean loggedOnce = false;

    @Inject(method = "initConfig", at = @At("TAIL"))
    private static void wildernessOdysseyApi$ensureEntitiesEnabled(CallbackInfo ci) {
        boolean current = ConfigHandler.spawnNonSignEntitiesFromSupportedSchematics;
        if (!current) {
            ConfigHandler.spawnNonSignEntitiesFromSupportedSchematics = true;
            if (!loggedOnce) {
                ModConstants.LOGGER.info("[Starter Structure compat] Forced entity spawning on for schematics to preserve bunker machines.");
                loggedOnce = true;
            }
        } else if (!loggedOnce) {
            ModConstants.LOGGER.debug("[Starter Structure compat] Entity spawning already enabled for schematics.");
            loggedOnce = true;
        }
    }
}
