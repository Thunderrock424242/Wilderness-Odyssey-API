package com.thunder.wildernessodysseyapi.structuregen.nbt;

import java.io.IOException;

/**
 * Signals that an NBT file was readable but was not a valid Minecraft structure-template document.
 *
 * <p>Keeping shape failures distinct from ordinary I/O failures lets the CLI explain whether a
 * path was inaccessible or whether a specific tag inside the template was malformed.</p>
 */
public final class StructureNbtFormatException extends IOException {

    /** Creates a format failure with a precise tag path and explanation. */
    public StructureNbtFormatException(String message) {
        super(message);
    }
}
