package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.PasteBuilder;
import com.sk89q.worldedit.world.World;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureSchematic.Format;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Places Starter Structure schematics using WorldEdit so Create contraptions and tile entities remain intact.
 */
public final class StarterStructureWorldEditPlacer {
    private StarterStructureWorldEditPlacer() {
    }

    /**
     * Attempts to paste the given schematic with WorldEdit. Returns {@code true} when WorldEdit successfully
     * performs the paste, {@code false} otherwise.
     */
    public static boolean placeWithWorldEdit(ServerLevel serverLevel, StarterStructureSchematic schematic, BlockPos origin) {
        if (!StructureConfig.STARTER_STRUCTURE_USE_WORLDEDIT.get()) return false;
        if (!ModList.get().isLoaded("worldedit")) return false;
        if (serverLevel == null || schematic == null) return false;

        Path schematicPath = schematic.path();
        if (schematicPath == null || !Files.isRegularFile(schematicPath)) return false;

        try {
            ClipboardFormat format = schematic.clipboardFormat();
            if (format == null) {
                format = ClipboardFormats.findByFile(schematicPath.toFile());
            }
            if (format == null && schematic.format() == Format.NBT) {
                format = ClipboardFormats.findByAlias("schematic");
            }
            if (format == null) {
                ModConstants.LOGGER.warn("[Starter Structure compat] WorldEdit could not detect format for {}.", schematicPath.getFileName());
                return false;
            }

            try (InputStream in = Files.newInputStream(schematicPath);
                 ClipboardReader reader = format.getReader(in)) {
                Clipboard clipboard = reader.read();
                BlockVector3 destination = BlockVector3.at(origin.getX(), origin.getY(), origin.getZ());
                World weWorld = com.sk89q.worldedit.neoforge.NeoForgeAdapter.adapt(serverLevel);

                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(weWorld)
                        .maxBlocks(-1)
                        .build()) {
                    PasteBuilder paste = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(destination)
                            .copyBiomes(true)
                            .copyEntities(true)
                            .ignoreAirBlocks(false)
                            .ignoreStructureVoidBlocks(true);
                    Operations.complete(paste.build());
                }
            }

            ModConstants.LOGGER.info("[Starter Structure compat] Pasted starter bunker with WorldEdit at {} to preserve machines.", origin);
            return true;
        } catch (Exception e) {
            ModConstants.LOGGER.warn("[Starter Structure compat] WorldEdit paste failed for {}.", schematicPath, e);
            return false;
        }
    }
}
