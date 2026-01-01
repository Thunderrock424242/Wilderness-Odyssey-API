package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(StructureTemplate.Palette.class)
public interface StructureTemplatePaletteAccessor {
    @Invoker("<init>")
    static StructureTemplate.Palette wildernessOdysseyApi$createPalette(List<StructureTemplate.StructureBlockInfo> blocks) {
        throw new AssertionError();
    }
}
