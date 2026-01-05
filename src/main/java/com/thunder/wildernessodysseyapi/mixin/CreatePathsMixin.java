package com.thunder.wildernessodysseyapi.mixin;

import com.simibubi.create.foundation.utility.CreatePaths;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * Redirects Create's schematic directories into this mod's built-in data pack.
 * <p>
 * This ensures the schematic cannon loads bundled templates from
 * {@code data/<modid>/schematics} instead of relying on an external uploads folder.
 */
@Mixin(CreatePaths.class)
public abstract class CreatePathsMixin {
    @Mutable
    @Shadow
    @Final
    public static Path SCHEMATICS_DIR;

    @Mutable
    @Shadow
    @Final
    public static Path UPLOADED_SCHEMATICS_DIR;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void wilderness$rerouteSchematics(CallbackInfo ci) {
        Path modSchematicsDir = CreatePaths.GAME_DIR
                .resolve("data")
                .resolve(ModConstants.MOD_ID)
                .resolve("schematics");
        SCHEMATICS_DIR = modSchematicsDir;
        UPLOADED_SCHEMATICS_DIR = modSchematicsDir;
    }
}
