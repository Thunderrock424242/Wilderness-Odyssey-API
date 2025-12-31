package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
    @Accessor("palettes")
    List<StructureTemplate.Palette> getPalettes();

    @Accessor("entityInfoList")
    List<StructureTemplate.StructureEntityInfo> getEntityInfoList();

    @Accessor("size")
    void setSize(Vec3i size);

    @org.spongepowered.asm.mixin.gen.Invoker("<init>")
    static StructureTemplate.Palette wildernessOdysseyApi$createPalette(List<StructureTemplate.StructureBlockInfo> blocks) {
        throw new AssertionError();
    }
}
