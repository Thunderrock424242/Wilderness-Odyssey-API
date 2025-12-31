package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(StructureTemplate.Palette.class)
public interface StructureTemplatePaletteInvoker {
    @Invoker("<init>")
    static StructureTemplate.Palette wildernessodysseyapi$newPalette(List<StructureBlockInfo> blocks) {
        throw new UnsupportedOperationException();
    }
}
