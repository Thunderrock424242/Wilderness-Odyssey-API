package com.thunder.wildernessodysseyapi.mixin;

import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.RunContext;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.entity.EntityType;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures Create contraptions (including Super Glue) are copied when pasting schematics.
 *
 * <p>WorldEdit does not copy entities by default for //paste, which causes Create
 * contraptions that rely on Super Glue or moving contraption entities (e.g. rope
 * pulley elevators) to break apart. If Create is present we force entity copying
 * up front, ensuring all Create entities are brought along with the block paste
 * and survive world saves/loads.</p>
 */
@Mixin(ForwardExtentCopy.class)
public abstract class ForwardExtentCopyMixin {

    private static final String CREATE_NAMESPACE = "create:";

    @Shadow private boolean copyingEntities;
    @Shadow private Extent source;
    @Shadow private Region region;

    @Shadow public abstract void setCopyingEntities(boolean copyingEntities);

    @Unique
    private boolean wildernessodysseyapi$checkedForCreateGlue;

    @Inject(method = "resume", at = @At("HEAD"))
    private void wildernessodysseyapi$autoCopyCreateGlue(RunContext context, CallbackInfoReturnable<Operation> cir) {
        if (wildernessodysseyapi$checkedForCreateGlue || copyingEntities) {
            return;
        }
        wildernessodysseyapi$checkedForCreateGlue = true;

        if (!ModList.get().isLoaded("create")) {
            return;
        }

        // Always enable entity copying when Create is installed so moving contraptions
        // (e.g. pulleys) and glue entities persist through pastes and world reloads.
        setCopyingEntities(true);

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
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // If the extent cannot enumerate entities, fall back to the user's original choice.
        }
    }
}
