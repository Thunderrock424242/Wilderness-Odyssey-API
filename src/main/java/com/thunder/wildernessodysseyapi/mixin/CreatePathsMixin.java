package com.thunder.wildernessodysseyapi.mixin;

import com.simibubi.create.foundation.utility.CreatePaths;
import com.thunder.wildernessodysseyapi.compat.create.LegacyCreateSchematicMigration;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Restores Create's normal schematic paths without stranding legacy user files.
 *
 * <p>An earlier compatibility hook replaced Create's process-wide schematic
 * paths with {@code data/wildernessodysseyapi/schematics}. This injection no
 * longer mutates Create state. It only performs a non-destructive, one-time copy
 * from that legacy directory into Create's standard schematic directory after
 * Create has initialized its own paths.</p>
 *
 * <p>The migration never overwrites or deletes a schematic. Conflicting files
 * remain in the legacy directory and are reported so a user can reconcile them
 * manually.</p>
 */
@Mixin(CreatePaths.class)
public abstract class CreatePathsMixin {
    @Shadow
    @Final
    public static Path GAME_DIR;

    @Shadow
    @Final
    public static Path SCHEMATICS_DIR;

    @Shadow
    @Final
    public static Path UPLOADED_SCHEMATICS_DIR;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void wilderness$migrateLegacySchematics(CallbackInfo callbackInfo) {
        Path legacySchematics = GAME_DIR
                .resolve("data")
                .resolve(ModConstants.MOD_ID)
                .resolve("schematics");

        try {
            LegacyCreateSchematicMigration.MigrationResult result =
                    LegacyCreateSchematicMigration.migrate(
                            legacySchematics,
                            SCHEMATICS_DIR,
                            UPLOADED_SCHEMATICS_DIR
                    );
            if (result.copiedFiles() > 0 || result.alreadyPresentFiles() > 0) {
                ModConstants.LOGGER.info(
                        "Restored {} legacy Create schematic(s) to Create's standard directories; {} already present",
                        result.copiedFiles(),
                        result.alreadyPresentFiles()
                );
            }
            if (result.conflictingFiles() > 0) {
                ModConstants.LOGGER.warn(
                        "Left {} conflicting Create schematic(s) in {} because files with different contents already exist under {}",
                        result.conflictingFiles(),
                        legacySchematics,
                        SCHEMATICS_DIR
                );
            }
        } catch (IOException | RuntimeException exception) {
            // Create must keep its original paths even when legacy recovery is
            // unavailable. The source data remains untouched for manual recovery.
            ModConstants.LOGGER.warn(
                    "Could not migrate legacy Create schematics from {} to Create's standard directories under {}; legacy files were not changed",
                    legacySchematics,
                    SCHEMATICS_DIR,
                    exception
            );
        }
    }
}
