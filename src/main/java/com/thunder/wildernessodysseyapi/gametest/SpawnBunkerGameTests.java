package com.thunder.wildernessodysseyapi.gametest;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
import com.thunder.wildernessodysseyapi.WorldGen.spawn.SpawnBunkerPlacer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.SchematicEntityRestorer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureSchematic;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureSpawnGuard;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureTerrainBlender;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureWorldEditPlacer;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * GameTests that verify the spawn bunker can be pasted with vanilla template mechanics.
 */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpawnBunkerGameTests {
    private static final String BATCH = "bunker";
    private static final String ENTITY_ID = "minecraft:armor_stand";

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void bunkerSpawnsBlocks(GameTestHelper helper) {
        NBTStructurePlacer.PlacementResult result = placeBunker(helper);
        if (result == null) {
            return; // placeBunker already reported failure
        }

        helper.runAtTickTime(2, () -> {
            boolean hasBlocks = hasAnyBlock(helper.getLevel(), result);
            helper.assertTrue(hasBlocks, "Bunker paste did not produce any non-air blocks in its footprint.");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void bunkerContainsCryoTubes(GameTestHelper helper) {
        NBTStructurePlacer.PlacementResult result = placeBunker(helper);
        if (result == null) {
            return; // placeBunker already reported failure
        }

        helper.runAtTickTime(2, () -> {
            boolean foundCryo = containsCryoTube(helper.getLevel(), result);
            helper.assertTrue(foundCryo, "No cryo tubes were found inside the pasted bunker footprint.");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void nbtSchematicRestoresEntities(GameTestHelper helper) {
        try {
            StarterStructureSpawnGuard.clearAll();
            Path schematicPath = createEntityNbt();
            PlacementRun run = runPlacementPipeline(helper, schematicPath, false, true);

            helper.runAtTickTime(2, () -> {
                helper.assertTrue(hasArmorStand(helper.getLevel(), run.origin()),
                        "Armor stand from NBT schematic was not spawned.");
                helper.assertTrue(StarterStructureSpawnGuard.isDenied(helper.getLevel(), run.origin()),
                        "Spawn guard zone was not registered after NBT placement.");
                helper.assertTrue(run.schematic().footprint() != null,
                        "Derived footprint for NBT schematic should not be null.");
                helper.succeed();
            });
        } catch (Exception e) {
            helper.fail("Failed to prepare NBT schematic for test: " + e.getMessage());
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void schemPlacementWithoutWorldEdit(GameTestHelper helper) {
        try {
            StarterStructureSpawnGuard.clearAll();
            Path schemPath = createEntitySchem();
            PlacementRun run = runPlacementPipeline(helper, schemPath, false, true);

            helper.runAtTickTime(2, () -> {
                helper.assertFalse(run.pastedWithWorldEdit(),
                        "WorldEdit should be bypassed when disabled in config.");
                helper.assertTrue(hasArmorStand(helper.getLevel(), run.origin()),
                        "Armor stand from schem should spawn even without WorldEdit.");
                helper.assertTrue(isBlended(helper.getLevel(), run.origin()),
                        "Blending did not affect the surrounding terrain for schem placement.");
                helper.succeed();
            });
        } catch (Exception e) {
            helper.fail("Failed to prepare schem for test: " + e.getMessage());
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void schemPlacementWithWorldEdit(GameTestHelper helper) {
        if (!ModList.get().isLoaded("worldedit")) {
            helper.succeed();
            return;
        }
        try {
            StarterStructureSpawnGuard.clearAll();
            Path schemPath = createEntitySchem();
            PlacementRun run = runPlacementPipeline(helper, schemPath, true, true);

            helper.runAtTickTime(2, () -> {
                helper.assertTrue(run.pastedWithWorldEdit(),
                        "WorldEdit should paste schematics when enabled and available.");
                helper.assertTrue(helper.getLevel().getBlockState(run.origin()).isSolid(),
                        "WorldEdit paste did not leave any solid blocks at the origin.");
                helper.assertTrue(hasArmorStand(helper.getLevel(), run.origin()),
                        "Entities from schem should still be restored after WorldEdit placement.");
                helper.assertTrue(StarterStructureSpawnGuard.isDenied(helper.getLevel(), run.origin()),
                        "Spawn guard zone should be registered after WorldEdit placement.");
                helper.succeed();
            });
        } catch (Exception e) {
            helper.fail("Failed to prepare WorldEdit schem for test: " + e.getMessage());
        }
    }

    private static NBTStructurePlacer.PlacementResult placeBunker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO.above(64));
        NBTStructurePlacer.PlacementResult result = SpawnBunkerPlacer.placeBunker(level, anchor);
        if (result == null) {
            helper.fail(" Bunker template '" + ModConstants.MOD_ID + ":bunker' is missing or empty.");
            return null;
        }

        return result;
    }

    private static boolean hasAnyBlock(ServerLevel level, NBTStructurePlacer.PlacementResult result) {
        BlockPos min = new BlockPos(
                (int) Math.floor(result.bounds().minX),
                (int) Math.floor(result.bounds().minY),
                (int) Math.floor(result.bounds().minZ));
        BlockPos max = new BlockPos(
                (int) Math.ceil(result.bounds().maxX) - 1,
                (int) Math.ceil(result.bounds().maxY) - 1,
                (int) Math.ceil(result.bounds().maxZ) - 1);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean containsCryoTube(ServerLevel level, NBTStructurePlacer.PlacementResult result) {
        BlockPos min = new BlockPos(
                (int) Math.floor(result.bounds().minX),
                (int) Math.floor(result.bounds().minY),
                (int) Math.floor(result.bounds().minZ));
        BlockPos max = new BlockPos(
                (int) Math.ceil(result.bounds().maxX) - 1,
                (int) Math.ceil(result.bounds().maxY) - 1,
                (int) Math.ceil(result.bounds().maxZ) - 1);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(CryoTubeBlock.CRYO_TUBE.get())) {
                        return true;
                    }
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be != null && be.getType() == CryoTubeBlock.CRYO_TUBE_ENTITY.get()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static PlacementRun runPlacementPipeline(GameTestHelper helper, Path path, boolean allowWorldEdit, boolean placeFallbackBlock) throws IOException {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO.above(60));
        StarterStructureSchematic schematic = StarterStructureSchematic.capture(level, path, origin, null);

        boolean previousWorldEdit = StructureConfig.STARTER_STRUCTURE_USE_WORLDEDIT.get();
        int previousCoverDepth = StructureConfig.STARTER_STRUCTURE_EXTRA_COVER_DEPTH.get();
        StructureConfig.STARTER_STRUCTURE_USE_WORLDEDIT.set(allowWorldEdit);
        StructureConfig.STARTER_STRUCTURE_EXTRA_COVER_DEPTH.set(0);

        boolean pastedWithWorldEdit;
        try {
            pastedWithWorldEdit = StarterStructureWorldEditPlacer.placeWithWorldEdit(level, schematic, origin);
            if (!pastedWithWorldEdit && placeFallbackBlock) {
                level.setBlock(origin, Blocks.STONE.defaultBlockState(), 2);
            }

            StarterStructureSpawnGuard.registerSpawnDenyZone(level, origin);
            StarterStructureTerrainBlender.blendPlacedStructure(level, origin, schematic.footprint());
            SchematicEntityRestorer.spawnRestoredEntities(level, schematic.entities());
        } finally {
            StructureConfig.STARTER_STRUCTURE_USE_WORLDEDIT.set(previousWorldEdit);
            StructureConfig.STARTER_STRUCTURE_EXTRA_COVER_DEPTH.set(previousCoverDepth);
        }

        return new PlacementRun(origin, schematic, pastedWithWorldEdit);
    }

    private static Path createEntityNbt() throws IOException {
        Path path = Files.createTempFile("starter-structure-entity-", ".nbt");
        CompoundTag root = new CompoundTag();
        root.put("entities", buildEntityList(BlockPos.ZERO));
        try (OutputStream out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(root, out);
        }
        path.toFile().deleteOnExit();
        return path;
    }

    private static Path createEntitySchem() throws IOException, WorldEditException {
        ClipboardFormat format = ClipboardFormats.findByAlias("sponge");
        if (format == null) {
            throw new IOException("Sponge schematic format is unavailable.");
        }

        BlockArrayClipboard clipboard = new BlockArrayClipboard(new CuboidRegion(BlockVector3.ZERO, BlockVector3.ZERO));
        clipboard.setOrigin(BlockVector3.ZERO);
        clipboard.setBlock(BlockVector3.ZERO, BlockTypes.STONE.getDefaultState());

        Path path = Files.createTempFile("starter-structure-entity-", ".schem");
        try (OutputStream out = Files.newOutputStream(path);
             ClipboardWriter writer = format.getWriter(out)) {
            writer.write(clipboard);
        }

        injectEntitiesIntoSchematic(path, BlockPos.ZERO);
        path.toFile().deleteOnExit();
        return path;
    }

    private static void injectEntitiesIntoSchematic(Path path, BlockPos relativePos) throws IOException {
        CompoundTag outer = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        CompoundTag target = outer.contains("Schematic", 10) ? outer.getCompound("Schematic") : outer;
        target.put("Entities", buildEntityList(relativePos));
        if (outer != target) {
            outer.put("Schematic", target);
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(outer, out);
        }
    }

    private static ListTag buildEntityList(BlockPos relativePos) {
        CompoundTag entityData = new CompoundTag();
        entityData.putString("id", ENTITY_ID);

        ListTag blockPos = new ListTag();
        blockPos.add(IntTag.valueOf(relativePos.getX()));
        blockPos.add(IntTag.valueOf(relativePos.getY()));
        blockPos.add(IntTag.valueOf(relativePos.getZ()));

        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(relativePos.getX() + 0.5D));
        pos.add(DoubleTag.valueOf(relativePos.getY()));
        pos.add(DoubleTag.valueOf(relativePos.getZ() + 0.5D));

        CompoundTag wrapper = new CompoundTag();
        wrapper.put("blockPos", blockPos);
        wrapper.put("Pos", pos);
        wrapper.put("nbt", entityData);

        ListTag entities = new ListTag();
        entities.add(wrapper);
        return entities;
    }

    private static boolean hasArmorStand(ServerLevel level, BlockPos origin) {
        AABB search = new AABB(origin).inflate(2.0D);
        return !level.getEntities(EntityType.ARMOR_STAND, search, entity -> true).isEmpty();
    }

    private static boolean isBlended(ServerLevel level, BlockPos origin) {
        BlockPos sample = origin.offset(3, 0, 0);
        return !level.getBlockState(sample).isAir();
    }

    private record PlacementRun(BlockPos origin, StarterStructureSchematic schematic, boolean pastedWithWorldEdit) { }
}
