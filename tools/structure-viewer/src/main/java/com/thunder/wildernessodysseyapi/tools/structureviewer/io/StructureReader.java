package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import java.io.IOException;
import java.nio.file.Path;

/** Import boundary for NBT and future JSON/schematic adapters. */
public interface StructureReader {
    /** Reads a structure without modifying the file or starting Minecraft. */
    StructureData read(Path path) throws IOException;
}

