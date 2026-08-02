package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-world parity checks for vanilla systems backed by Wilderness water. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VanillaWaterParityGameTests {

    private VanillaWaterParityGameTests() {
    }

    /** Verifies bubble columns form through a full Wilderness source block. */
    @GameTest(template = "empty")
    public static void bubbleColumnFormsInWildernessWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos basePos = sourcePos.below();
        BlockState soulSand = Blocks.SOUL_SAND.defaultBlockState();
        level.setBlock(basePos, soulSand, 3);
        level.setBlock(
                sourcePos,
                WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get().defaultBlockState(),
                3
        );

        // GameTest levels bypass normal authority activation, so explicitly
        // import the source before exercising the projection-preservation path.
        CanonicalWater.getOrImport(level, sourcePos);

        BubbleColumnBlock.updateColumn(level, sourcePos, soulSand);
        CanonicalWater.reprojectCompatibility(level, sourcePos);

        helper.assertTrue(
                level.getBlockState(sourcePos).is(Blocks.BUBBLE_COLUMN),
                "Bubble column rejected a full Wilderness source or was overwritten by canonical projection"
        );
        helper.succeed();
    }

    /** Verifies NeoForge hydration and tag-based fishing/pathing see the fluid as water. */
    @GameTest(template = "empty")
    public static void vanillaWaterContractsRecognizeWildernessFluid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos farmlandPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos waterPos = farmlandPos.east();
        BlockState farmland = Blocks.FARMLAND.defaultBlockState();
        BlockState wildernessWater = WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()
                .defaultBlockState();
        level.setBlock(farmlandPos, farmland, 3);
        level.setBlock(waterPos, wildernessWater, 3);

        helper.assertTrue(
                farmland.canBeHydrated(level, farmlandPos, level.getFluidState(waterPos), waterPos),
                "Wilderness FluidType did not hydrate farmland"
        );
        helper.assertTrue(
                level.getFluidState(waterPos).is(FluidTags.WATER)
                        && level.getFluidState(waterPos).isSource(),
                "Wilderness source is missing the vanilla tag/source contract used by fishing and swim pathing"
        );
        helper.assertTrue(
                VanillaWaterParity.matchesRequestedWaterBlock(wildernessWater, Blocks.WATER),
                "Exact path-navigation water translation rejected Wilderness water"
        );
        helper.succeed();
    }

    /** Verifies a DATA marker becomes one source block and loses block-entity NBT. */
    @GameTest(template = "empty")
    public static void structureWaterMarkerConvertsOnce(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        tag.putString("metadata", StructureWaterMarkerAdapter.WATER_MARKER);
        StructureTemplate.StructureBlockInfo marker = new StructureTemplate.StructureBlockInfo(
                helper.absolutePos(new BlockPos(2, 2, 2)),
                Blocks.STRUCTURE_BLOCK.defaultBlockState()
                        .setValue(StructureBlock.MODE, StructureMode.DATA),
                tag
        );

        StructureTemplate.StructureBlockInfo converted =
                StructureWaterMarkerAdapter.convertMarker(marker);

        helper.assertTrue(
                converted.state().is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get())
                        && converted.nbt() == null
                        && converted.pos().equals(marker.pos()),
                "Structure water marker did not become one NBT-free Wilderness source"
        );
        helper.succeed();
    }
}
