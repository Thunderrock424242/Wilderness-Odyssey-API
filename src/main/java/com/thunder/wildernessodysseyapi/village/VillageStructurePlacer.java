package com.thunder.wildernessodysseyapi.village;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class VillageStructurePlacer {
    private static final ResourceLocation STRUCTURE_LOC = new ResourceLocation("yourmod", "my_structure");

    public static void tryPlaceStructureOnce(ServerLevel level, BlockPos pos) {
        File schematicFile = new File("config/yourmod/schematics/my_structure.schem");
        if (!schematicFile.exists()) {
            throw new RuntimeException("Missing schematic: " + schematicFile.getAbsolutePath());
        }

        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) throw new IOException("Unsupported schematic format");

            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(Files.newInputStream(schematicFile.toPath()))) {
                clipboard = reader.read();
            }

            EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(level.getWorld()));
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(pos.getX(), pos.getY(), pos.getZ()))
                    .ignoreAirBlocks(false)
                    .build();

            Operations.complete(operation);
            editSession.flushSession();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
