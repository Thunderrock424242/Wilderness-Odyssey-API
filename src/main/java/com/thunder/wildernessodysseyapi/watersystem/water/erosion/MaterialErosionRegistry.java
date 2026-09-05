package com.thunder.wildernessodysseyapi.watersystem.water.erosion;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Data-pack material resistance with immunity taking precedence over every category. */
public final class MaterialErosionRegistry {
    public static final TagKey<Block> SOFT = tag("erosion_soft");
    public static final TagKey<Block> MEDIUM = tag("erosion_medium");
    public static final TagKey<Block> HARD = tag("erosion_hard");
    public static final TagKey<Block> IMMUNE = tag("erosion_immune");

    private MaterialErosionRegistry() { }

    /** Returns required accumulated exposure seconds, or infinity for unsupported material. */
    public static float resistance(BlockState state) {
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.is(IMMUNE)) {
            return Float.POSITIVE_INFINITY;
        }
        // More resistant tags win if a pack accidentally lists a block twice.
        if (state.is(HARD)) return 7_200.0f * ErosionConfig.resistanceScale();
        if (state.is(MEDIUM)) return 1_800.0f * ErosionConfig.resistanceScale();
        if (state.is(SOFT)) return 600.0f * ErosionConfig.resistanceScale();
        return Float.POSITIVE_INFINITY;
    }

    /** Conserved sediment categories; an eroded block produces exactly one unit. */
    public static Material material(BlockState state) {
        if (state.is(Blocks.SAND) || state.is(Blocks.SANDSTONE)) return Material.SAND;
        if (state.is(Blocks.RED_SAND) || state.is(Blocks.RED_SANDSTONE)) return Material.RED_SAND;
        if (state.is(Blocks.CLAY)) return Material.CLAY;
        if (state.is(HARD) || state.is(Blocks.GRAVEL)) return Material.GRAVEL;
        return Material.SOIL;
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path));
    }

    /** Compact mass categories, with stable persisted identifiers. */
    public enum Material {
        SAND(Blocks.SAND), RED_SAND(Blocks.RED_SAND), GRAVEL(Blocks.GRAVEL), SOIL(Blocks.DIRT), CLAY(Blocks.CLAY);
        private final Block deposit;
        Material(Block deposit) { this.deposit = deposit; }
        /** Returns the settled block for one full sediment unit. */
        public BlockState depositState() { return deposit.defaultBlockState(); }
    }
}
