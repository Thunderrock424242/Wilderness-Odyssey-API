package com.thunder.wildernessodysseyapi.gametest;

import com.thunder.wildernessodysseyapi.mixin.StructureTemplateAccessor;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Provides minimal in-memory templates for GameTest scaffolding when no structure files exist.
 */
public final class GameTestTemplateFallbacks {
    public static final ResourceLocation VANILLA_EMPTY = ResourceLocation.parse("minecraft:empty");

    private GameTestTemplateFallbacks() {
    }

    /**
     * Returns an empty structure template for {@code minecraft:empty} when the file is missing.
     */
    public static Optional<StructureTemplate> maybeEmptyTemplate(ResourceLocation id) {
        if (!VANILLA_EMPTY.equals(id)) {
            return Optional.empty();
        }

        StructureTemplate template = new StructureTemplate();
        StructureTemplateAccessor accessor = (StructureTemplateAccessor) template;
        accessor.setSize(Vec3i.ZERO);
        // Leave palettes and entities empty.
        return Optional.of(template);
    }
}
