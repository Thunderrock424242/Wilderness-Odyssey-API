package com.thunder.wildernessodysseyapi.watersystem.water.render;

/**
 * Loader-neutral view of an optional renderer's section coordinates.
 *
 * <p>Sodium and Embeddium classes remain absent from the normal compile-time
 * surface; guarded pseudo mixins implement this interface only when installed.</p>
 */
public interface WaterRendererSectionCoordinates {

    /** Returns the section-space X coordinate. */
    int wildernessOdysseyApi$sectionX();

    /** Returns the section-space Y coordinate. */
    int wildernessOdysseyApi$sectionY();

    /** Returns the section-space Z coordinate. */
    int wildernessOdysseyApi$sectionZ();
}
