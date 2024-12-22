package com.thunder.wildernessodysseyapi.structure;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.InputStream;

public class WorldEditStructurePlacer {
    private final String schematicNamespace;
    private final String schematicPath;

    public WorldEditStructurePlacer(String namespace, String path) {
        this.schematicNamespace = namespace;
        this.schematicPath = path;
    }

    public boolean placeStructure(ServerLevel world, BlockPos position) {
        try {
            // Load the schematic from resources
            InputStream schemStream = getClass().getResourceAsStream(
                    "/assets/" + schematicNamespace + "/" + schematicPath
            );

            if (schemStream == null) {
                System.out.println("Schematic file not found: " + schematicNamespace + "/" + schematicPath);
                return false;
            }

            ClipboardFormat format = ClipboardFormat.findByAlias("schem");
            if (format == null) {
                System.out.println("Unsupported schematic format!");
                return false;
            }

            try (ClipboardReader reader = format.getReader(schemStream)) {
                Clipboard clipboard = reader.read();

                // Determine the surface position
                BlockPos surfacePos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, position);

                // Paste the schematic at the surface position
                try (EditSession editSession = WorldEdit.getInstance().newEditSession((World) world)) {
                    ClipboardHolder holder = new ClipboardHolder(clipboard);
                    holder.createPaste(editSession)
                            .to(BlockVector3.at(surfacePos.getX(), surfacePos.getY(), surfacePos.getZ()))
                            .ignoreAirBlocks(false)
                            .build();
                }
            }

            return true;
        } catch (Exception e) {
            System.out.println("Error placing structure:");
            e.printStackTrace();
            return false;
        }
    }
}
