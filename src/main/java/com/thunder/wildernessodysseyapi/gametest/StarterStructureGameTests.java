package com.thunder.wildernessodysseyapi.gametest;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.PasteBuilder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureBundler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GameTests that verify the starter bunker can spawn and retains Create machines when pasted.
 */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class StarterStructureGameTests {
    private static final String BATCH = "starterstructure";
    private static final String WORLD_EDIT_MOD_ID = "worldedit";
    private static final String CREATE_MOD_ID = "create";

    @GameTest(templateNamespace = ModConstants.MOD_ID, template = "empty", batch = BATCH, timeoutTicks = 400)
    public void bunkerSpawnsBlocks(GameTestHelper helper) {
        PlacementContext context = pasteBunker(helper);
        if (context == null) {
            return; // pasteBunker already reported failure
        }

        helper.runAtTickTime(2, () -> {
            boolean hasBlocks = hasAnyBlock(helper.getLevel(), context.minCorner(), context.maxCorner());
            helper.assertTrue(hasBlocks, "Bunker paste did not produce any non-air blocks in its footprint.");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = ModConstants.MOD_ID, template = "empty", batch = BATCH, timeoutTicks = 400)
    public void bunkerRetainsCreateMachines(GameTestHelper helper) {
        if (!ModList.get().isLoaded(CREATE_MOD_ID)) {
            helper.fail("Create mod is not loaded; cannot verify Create machines.");
            return;
        }

        PlacementContext context = pasteBunker(helper);
        if (context == null) {
            return; // pasteBunker already reported failure
        }

        helper.runAtTickTime(4, () -> {
            boolean foundCreateMachines = containsCreateBlockEntity(helper.getLevel(), context.minCorner(), context.maxCorner());
            helper.assertTrue(foundCreateMachines, "No Create block entities were found inside the pasted bunker footprint.");
            helper.succeed();
        });
    }

    private static PlacementContext pasteBunker(GameTestHelper helper) {
        if (!ModList.get().isLoaded(WORLD_EDIT_MOD_ID)) {
            helper.fail("WorldEdit is not loaded; cannot paste bunker schematic.");
            return null;
        }

        StarterStructureBundler.ensureBundledBunkerPresent();
        Path bunkerPath = StarterStructureBundler.getBundledBunkerPath();
        if (!Files.isRegularFile(bunkerPath)) {
            helper.fail("Bundled bunker schematic is missing at " + bunkerPath.toAbsolutePath());
            return null;
        }

        Clipboard clipboard = readClipboard(helper, bunkerPath);
        if (clipboard == null) {
            return null;
        }

        BlockPos origin = helper.absolutePos(BlockPos.ZERO.above(64));
        BlockPos min = translateVec(origin, clipboard.getMinimumPoint());
        BlockPos max = translateVec(origin, clipboard.getMaximumPoint());

        ServerLevel level = helper.getLevel();
        loadChunks(level, min, max);
        boolean pasted = pasteWithWorldEdit(level, bunkerPath, origin, clipboard);
        if (!pasted) {
            helper.fail("WorldEdit could not paste bunker schematic at " + origin);
            return null;
        }

        return new PlacementContext(min, max);
    }

    private static Clipboard readClipboard(GameTestHelper helper, Path path) {
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(path.toFile());
            if (format == null) {
                helper.fail("WorldEdit could not detect schematic format for " + path.getFileName());
                return null;
            }

            try (InputStream in = Files.newInputStream(path); ClipboardReader reader = format.getReader(in)) {
                return reader.read();
            }
        } catch (IOException e) {
            helper.fail("Failed to read bunker schematic " + path.getFileName() + ": " + e.getMessage());
            return null;
        }
    }

    private static boolean pasteWithWorldEdit(ServerLevel level, Path schematicPath, BlockPos origin, Clipboard clipboard) {
        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(com.sk89q.worldedit.neoforge.NeoForgeAdapter.adapt(level))
                .maxBlocks(-1)
                .build()) {
            BlockVector3 destination = BlockVector3.at(origin.getX(), origin.getY(), origin.getZ());
            PasteBuilder paste = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(destination)
                    .copyBiomes(true)
                    .copyEntities(true)
                    .ignoreAirBlocks(false)
                    .ignoreStructureVoidBlocks(true);
            Operations.complete(paste.build());
            return true;
        } catch (Exception e) {
            ModConstants.LOGGER.warn("[Starter Structure gametest] WorldEdit paste failed for {}.", schematicPath, e);
            return false;
        }
    }

    private static BlockPos translateVec(BlockPos origin, BlockVector3 vec) {
        return origin.offset(vec.x(), vec.y(), vec.z());
    }

    private static void loadChunks(ServerLevel level, BlockPos min, BlockPos max) {
        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    private static boolean hasAnyBlock(ServerLevel level, BlockPos min, BlockPos max) {
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

    private static boolean containsCreateBlockEntity(ServerLevel level, BlockPos min, BlockPos max) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be == null) {
                        continue;
                    }

                    ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                    if (id != null && CREATE_MOD_ID.equals(id.getNamespace())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private record PlacementContext(BlockPos minCorner, BlockPos maxCorner) {
    }
}
