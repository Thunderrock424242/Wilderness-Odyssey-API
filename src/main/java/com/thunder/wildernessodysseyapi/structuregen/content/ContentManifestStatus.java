package com.thunder.wildernessodysseyapi.structuregen.content;

/**
 * Describes how much StructureGen can trust content-policy provenance read from NBT.
 */
public enum ContentManifestStatus {
    /** No StructureGen content-manifest extension was present. */
    ABSENT,

    /** A manifest was present, but its schema or one or more required values were not understood. */
    PARTIAL,

    /** The manifest matched the supported schema and every required value was structurally valid. */
    VERIFIED
}
