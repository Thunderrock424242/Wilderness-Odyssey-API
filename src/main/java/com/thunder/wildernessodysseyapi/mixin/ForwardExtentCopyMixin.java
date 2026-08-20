package com.thunder.wildernessodysseyapi.mixin;

import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.entity.EntityType;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Preserves Create contraption entities when WorldEdit copies a matching region.
 *
 * <p>WorldEdit does not copy entities by default for //paste, which causes Create
 * contraptions in a selected region to break apart. The mixin first inspects the
 * selection and enables entity copying only after it finds a Create-owned entity,
 * leaving ordinary WorldEdit operations and the caller's original option intact.</p>
 *
 * <p>WorldEdit is optional, so a pseudo target and the mixin config plugin's
 * class-resource gate prevent its classes from being resolved when absent.</p>
 */
@Pseudo
@Mixin(
        targets = "com.sk89q.worldedit.function.operation.ForwardExtentCopy",
        remap = false
)
public abstract class ForwardExtentCopyMixin {

    private static final String CREATE_NAMESPACE = "create:";

    @Shadow private boolean copyingEntities;
    @Shadow private Extent source;
    @Shadow private Region region;

    @Shadow public abstract void setCopyingEntities(boolean copyingEntities);

    @Unique
    private boolean wildernessodysseyapi$checkedForCreateGlue;

    @Inject(method = "resume", at = @At("HEAD"))
    private void wildernessodysseyapi$autoCopyCreateGlue(CallbackInfoReturnable<?> callbackInfo) {
        if (wildernessodysseyapi$checkedForCreateGlue || copyingEntities) {
            return;
        }
        wildernessodysseyapi$checkedForCreateGlue = true;

        if (!ModList.get().isLoaded("create")) {
            return;
        }

        if (source == null || region == null) {
            return;
        }

        try {
            for (Entity entity : source.getEntities(region)) {
                if (entity == null) {
                    continue;
                }
                EntityType type = entity.getState() != null ? entity.getState().getType() : null;
                if (type == null) {
                    continue;
                }

                String id = type.id();
                if (id != null && id.startsWith(CREATE_NAMESPACE)) {
                    // Enable copying only for a selection that actually contains a
                    // Create entity; vanilla mobs and item entities keep WorldEdit's
                    // original caller-controlled behavior.
                    setCopyingEntities(true);
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // Enumeration is optional compatibility work. Leaving the flag
            // untouched preserves the user's original WorldEdit choice.
        }
    }
}
