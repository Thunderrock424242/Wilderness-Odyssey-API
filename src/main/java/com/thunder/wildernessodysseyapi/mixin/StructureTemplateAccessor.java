package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureEntityInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
    @Accessor("palettes")
    List<Palette> getPalettes();

    @Accessor("entityInfoList")
    List<StructureEntityInfo> getEntityInfoList();

    @Accessor("size")
    void setSize(Vec3i size);
}
