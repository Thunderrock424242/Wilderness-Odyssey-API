package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Forces Starter Structure to keep entity spawning enabled so tile-entity-powered
 * machines inside the bunker are not stripped when the schematic is placed.
 */
@Mixin(targets = "com.natamus.starterstructure.config.ConfigHandler", remap = false)
public class StarterStructureConfigMixin {
    private static boolean loggedOnce = false;

    @Inject(method = "initConfig", at = @At("TAIL"))
    private static void wildernessOdysseyApi$ensureEntitiesEnabled(CallbackInfo ci) {
        try {
            Class<?> clazz = Class.forName("com.natamus.starterstructure.config.ConfigHandler");
            Field spawnEntities = clazz.getDeclaredField("spawnNonSignEntitiesFromSupportedSchematics");
            boolean current = spawnEntities.getBoolean(null);
            if (!current) {
                spawnEntities.setBoolean(null, true);
                if (!loggedOnce) {
                    ModConstants.LOGGER.info("[Starter Structure compat] Forced entity spawning on for schematics to preserve bunker machines.");
                    loggedOnce = true;
                }
            }
        } catch (Throwable throwable) {
            if (!loggedOnce) {
                ModConstants.LOGGER.warn("[Starter Structure compat] Unable to enforce entity spawning; bunker machines may be missing. {}", throwable.toString());
                loggedOnce = true;
            }
        }
    }
}
