package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Format dispatch stays outside rendering and the Swing window. */
public final class StructureReaders {
    private StructureReaders(){}
    /** Reads a supported structure, retaining all recoverable diagnostics. */
    public static StructureData read(Path path)throws IOException{
        String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
        if(name.endsWith(".nbt"))return new NbtStructureReader().read(path);
        if(name.endsWith(".json"))return new JsonStructureReader().read(path);
        throw new IOException("Supported structure formats: .nbt and Blueprint-v1/palette .json");
    }
}
