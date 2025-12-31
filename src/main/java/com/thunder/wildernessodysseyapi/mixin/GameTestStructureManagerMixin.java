package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.gametest.SchemGameTestStructureLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Allows the GameTest structure manager to read schem/schematic files directly.
 */
@Mixin(StructureTemplateManager.class)
public abstract class GameTestStructureManagerMixin {
    @Shadow
    private ResourceManager resourceManager;

    @Inject(method = "loadFromResource", at = @At("HEAD"), cancellable = true)
    private void wildernessOdysseyApi$loadSchem(ResourceLocation id,
                                               CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        Optional<StructureTemplate> fromSchem = SchemGameTestStructureLoader.tryLoad(this.resourceManager, id);
        if (fromSchem.isPresent()) {
            cir.setReturnValue(fromSchem);
        }
    }
}
