package com.thunder.wildernessodysseyapi.temporalrift;

import com.thunder.wildernessodysseyapi.temporalrift.api.TheBeforeContentApi;
import com.thunder.wildernessodysseyapi.temporalrift.config.TemporalRiftConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public final class BeforeStructurePolicy {
    private BeforeStructurePolicy() {
    }

    public static boolean isAllowed(ResourceKey<StructureSet> structureSetKey) {
        ResourceLocation id = structureSetKey.location();
        return TemporalRiftConfig.BEFORE_ALLOWED_STRUCTURES.get().contains(id.toString())
                || TheBeforeContentApi.isStructureAllowed(id);
    }
}
