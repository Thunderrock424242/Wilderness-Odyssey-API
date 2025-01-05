package com.thunder.wildernessodysseyapi.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(StructureTemplatePool.class)
public class VillagePoolsMixin {
    @Inject(method = "<init>*", at = @At("RETURN"))
    private void modifyTemplates(CallbackInfo ci) {
        StructureTemplatePool pool = (StructureTemplatePool) (Object) this;

        // Only modify vanilla village pools
        ResourceLocation poolName = pool.getFallback().unwrapKey().orElseThrow().location();
        if (poolName.getNamespace().equals("minecraft")) {
            switch (poolName.getPath()) {
                case "village/plains/houses":
                    replaceTemplates(pool, "customvillages:custom_plains_house");
                    break;
                case "village/savanna/houses":
                    replaceTemplates(pool, "customvillages:custom_savanna_house");
                    break;
                case "village/taiga/houses":
                    replaceTemplates(pool, "customvillages:custom_taiga_house");
                    break;
                default:
                    break; // Skip other pools
            }
        }
    }

    @Unique
    private void replaceTemplates(StructureTemplatePool pool, String customStructure) {
        List<Pair<StructurePoolElement, Integer>> rawTemplates = new ArrayList<>();
        rawTemplates.add(Pair.of(new LegacySinglePoolElement(customStructure), 1)); // Add custom structure with weight 1

        // Replace the raw templates and regenerate the templates list
        pool.templates.clear();
        for (Pair<StructurePoolElement, Integer> pair : rawTemplates) {
            StructurePoolElement element = pair.getFirst();
            for (int i = 0; i < pair.getSecond(); i++) {
                pool.templates.add(element);
            }
        }
    }
}
