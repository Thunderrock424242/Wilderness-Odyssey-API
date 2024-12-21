package com.thunder.wildernessodysseyapi.structure;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.io.InputStream;

public class SchematicLoader {

    private final ResourceLocation schematicLocation;

    public SchematicLoader(ResourceLocation schematicLocation) {
        this.schematicLocation = schematicLocation;
    }

    public void generateStructure(Level world, BlockPos position) {
        try {
            // Load the schematic file from the resource location
            InputStream schemStream = SchematicLoader.class.getResourceAsStream(
                    "/assets/" + schematicLocation.getNamespace() + "/" + schematicLocation.getPath()
            );
            if (schemStream == null) {
                System.out.println("Schematic file not found: " + schematicLocation);
                return;
            }

            ClipboardFormat format = ClipboardFormats.findByAlias("schem");
            if (format == null) {
                System.out.println("Error: Unsupported schematic format!");
                return;
            }

            try (ClipboardReader reader = format.getReader(schemStream)) {
                Clipboard clipboard = reader.read();

                // Paste the schematic at the specified position
                try (EditSession editSession = WorldEdit.getInstance().newEditSession((World) world)) {
                    ClipboardHolder holder = new ClipboardHolder(clipboard);
                    holder.createPaste(editSession)
                            .to(BlockVector3.at(position.getX(), position.getY(), position.getZ()))
                            .ignoreAirBlocks(false)
                            .build();
                }
            }
        } catch (Exception e) {
            System.out.println("Error generating structure:");
            e.printStackTrace();
        }
    }
}
