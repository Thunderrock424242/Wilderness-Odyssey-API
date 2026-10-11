package com.thunder.wildernessodysseyapi.anomaly.registry;

import com.thunder.wildernessodysseyapi.anomaly.block.AnomalyPortalBlock;
import com.thunder.wildernessodysseyapi.anomaly.block.AnomalyBlockMaterial;
import com.thunder.wildernessodysseyapi.anomaly.block.DistortedTerrainBlock;
import com.thunder.wildernessodysseyapi.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

public final class AnomalyBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);

    public static final DeferredBlock<Block> ANOMALY_ORE = BLOCKS.register(
            "anomaly_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .requiresCorrectToolForDrops()
                    .strength(4.5F, 9.0F)
                    .lightLevel(state -> 3)
                    .sound(SoundType.AMETHYST))
    );

    public static final DeferredBlock<AnomalyPortalBlock> ANOMALY_GATEWAY = BLOCKS.register(
            "anomaly_gateway",
            () -> new AnomalyPortalBlock(BlockBehaviour.Properties.of()
                    .requiresCorrectToolForDrops()
                    .strength(8.0F, 1_200.0F)
                    .lightLevel(state -> 12)
                    .noCollission()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST))
    );

    public static final DeferredBlock<DistortedTerrainBlock> DISTORTED_STONE = registerTerrain(
            "distorted_stone", AnomalyBlockMaterial.SPATIAL,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops()
                    .strength(1.5F, 6.0F).sound(SoundType.STONE));
    public static final DeferredBlock<DistortedTerrainBlock> DISTORTED_GRASS = registerTerrain(
            "distorted_grass", AnomalyBlockMaterial.STABLE,
            BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.GRASS));
    public static final DeferredBlock<DistortedTerrainBlock> DISTORTED_DIRT = registerTerrain(
            "distorted_dirt", AnomalyBlockMaterial.STABLE,
            BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.GRAVEL));
    public static final DeferredBlock<DistortedTerrainBlock> DISTORTED_MOSS = registerTerrain(
            "distorted_moss", AnomalyBlockMaterial.STABLE,
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.1F).sound(SoundType.MOSS));
    public static final DeferredBlock<DistortedTerrainBlock> DISTORTED_CLIFF_STONE = registerTerrain(
            "distorted_cliff_stone", AnomalyBlockMaterial.SPATIAL,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F).sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<DistortedTerrainBlock> UNSTABLE_RIFT_STONE = registerTerrain(
            "unstable_rift_stone", AnomalyBlockMaterial.CRITICAL,
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops()
                    .strength(3.5F, 9.0F).lightLevel(state -> 2).sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<DistortedTerrainBlock> ANOMALY_CRYSTAL_BLOCK = registerTerrain(
            "anomaly_crystal_block", AnomalyBlockMaterial.STABLE,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F).lightLevel(state -> 3).sound(SoundType.AMETHYST));
    public static final DeferredBlock<DistortedTerrainBlock> FRACTURED_ENERGY_BLOCK = registerTerrain(
            "fractured_energy_block", AnomalyBlockMaterial.SPATIAL,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).requiresCorrectToolForDrops()
                    .strength(4.0F, 10.0F).lightLevel(state -> 4).sound(SoundType.AMETHYST));

    static {
        ModItems.ITEMS.register("anomaly_ore", () -> new BlockItem(ANOMALY_ORE.get(), new Item.Properties()));
        ModItems.ITEMS.register("anomaly_gateway", () -> new BlockItem(ANOMALY_GATEWAY.get(), new Item.Properties().stacksTo(16)));
    }

    private AnomalyBlocks() {
    }

    private static DeferredBlock<DistortedTerrainBlock> registerTerrain(
            String name, AnomalyBlockMaterial material, BlockBehaviour.Properties properties) {
        DeferredBlock<DistortedTerrainBlock> block = BLOCKS.register(name,
                () -> new DistortedTerrainBlock(properties, material));
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    /** Registered terrain values shared by the creative tab and operator preview inventory. */
    public static List<Block> terrainBlocks() {
        return List.of(DISTORTED_STONE.get(), DISTORTED_GRASS.get(), DISTORTED_DIRT.get(), DISTORTED_MOSS.get(),
                DISTORTED_CLIFF_STONE.get(), UNSTABLE_RIFT_STONE.get(), ANOMALY_CRYSTAL_BLOCK.get(),
                FRACTURED_ENERGY_BLOCK.get());
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
