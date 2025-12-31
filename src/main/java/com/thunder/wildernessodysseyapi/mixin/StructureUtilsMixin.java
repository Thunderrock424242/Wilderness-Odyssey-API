package com.thunder.wildernessodysseyapi.mixin;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.gametest.GameTestSchematicTemplateLoader;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Supplier;

@Mixin(StructureUtils.class)
public abstract class StructureUtilsMixin {
    @Redirect(
            method = "prepareTestStructure",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;orElseThrow(Ljava/util/function/Supplier;)Ljava/lang/Object;")
    )
    private static Object wildernessodysseyapi$loadSchematicWhenMissing(
            Optional<StructureTemplate> template,
            Supplier<? extends RuntimeException> missingSupplier,
            GameTestInfo info,
            BlockPos origin,
            Rotation rotation,
            ServerLevel level
    ) {
        if (template.isPresent()) {
            return template.get();
        }

        ResourceLocation structureId = ResourceLocation.parse(info.getStructureName());
        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate schematicTemplate = GameTestSchematicTemplateLoader.loadFromSchematic(manager, structureId);
        if (schematicTemplate != null) {
            ModConstants.LOGGER.info("[GameTest schematics] Loaded schematic-backed template for {}.", structureId);
            return schematicTemplate;
        }

        throw missingSupplier.get();
    }
}
