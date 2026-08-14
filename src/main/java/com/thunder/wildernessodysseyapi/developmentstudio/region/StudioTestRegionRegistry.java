package com.thunder.wildernessodysseyapi.developmentstudio.region;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;

/** Defines bounded operational areas inside the version-two Development Campus. */
public final class StudioTestRegionRegistry {
    public static final long MAX_REGION_VOLUME = 4_096L;
    public static final ResourceLocation STRUCTURE_LAB = id("structure_lab");
    public static final ResourceLocation ENTITY_LAB = id("entity_lab");
    public static final ResourceLocation WATER_LAB = id("water_lab");
    public static final ResourceLocation OUTDOOR_LAB = id("outdoor_lab");

    private static final List<RelativeRegion> DEFAULTS = List.of(
            new RelativeRegion(STRUCTURE_LAB, "Structure Lab", new BlockPos(5, 4, 26),
                    new BlockPos(18, 10, 38), StudioTestRegionType.STRUCTURE, StudioResetPolicy.BLOCK_SNAPSHOT),
            new RelativeRegion(ENTITY_LAB, "Entity Lab", new BlockPos(25, 5, 6),
                    new BlockPos(39, 9, 16), StudioTestRegionType.ENTITY, StudioResetPolicy.TAGGED_ENTITIES),
            new RelativeRegion(WATER_LAB, "Water Torture Lab", new BlockPos(47, 4, 26),
                    new BlockPos(59, 10, 38), StudioTestRegionType.WATER, StudioResetPolicy.NONE),
            new RelativeRegion(OUTDOOR_LAB, "Outdoor Test Range", new BlockPos(25, 4, 47),
                    new BlockPos(39, 10, 60), StudioTestRegionType.OUTDOOR, StudioResetPolicy.NONE)
    );

    private StudioTestRegionRegistry() {
    }

    /** Resolves stable relative definitions against the exact persisted campus origin. */
    public static List<StudioTestRegion> resolve(BlockPos campusOrigin) {
        return DEFAULTS.stream()
                .map(definition -> definition.resolve(campusOrigin))
                .toList();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path);
    }

    private record RelativeRegion(
            ResourceLocation id,
            String displayName,
            BlockPos minOffset,
            BlockPos maxOffset,
            StudioTestRegionType type,
            StudioResetPolicy resetPolicy
    ) {
        private StudioTestRegion resolve(BlockPos origin) {
            return new StudioTestRegion(
                    id,
                    displayName,
                    Level.OVERWORLD.location(),
                    origin.offset(minOffset),
                    origin.offset(maxOffset),
                    type,
                    resetPolicy
            );
        }
    }
}
