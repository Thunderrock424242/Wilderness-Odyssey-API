package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.thunder.wildernessodysseyapi.weather.api.CloudType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies world-stable atlas layout, quality budgets, and GPU pixel packing. */
class CloudFieldAtlasModelTest {

    @Test
    void altitudeBandsUseTightPaddedCarrierBounds() {
        assertEquals(-16.0F, CloudFieldAtlasModel.bandMinimumOffset(0));
        assertEquals(48.0F, CloudFieldAtlasModel.bandMaximumOffset(0));
        assertEquals(16.0F, CloudFieldAtlasModel.bandMinimumOffset(1));
        assertEquals(96.0F, CloudFieldAtlasModel.bandMaximumOffset(1));
        assertEquals(56.0F, CloudFieldAtlasModel.bandMinimumOffset(2));
        assertEquals(160.0F, CloudFieldAtlasModel.bandMaximumOffset(2));
        assertEquals(-16.0F, CloudFieldAtlasModel.bandMinimumOffset(3));
        assertEquals(144.0F, CloudFieldAtlasModel.bandMaximumOffset(3));
    }

    @Test
    void raymarchStepsSelectCoordinatedQualityFamilies() {
        assertEquals(
                CloudFieldAtlasModel.QualityPreset.PERFORMANCE,
                CloudFieldAtlasModel.qualityForSteps(16)
        );
        assertEquals(
                CloudFieldAtlasModel.QualityPreset.BALANCED,
                CloudFieldAtlasModel.qualityForSteps(17)
        );
        assertEquals(
                CloudFieldAtlasModel.QualityPreset.BALANCED,
                CloudFieldAtlasModel.qualityForSteps(32)
        );
        assertEquals(
                CloudFieldAtlasModel.QualityPreset.CINEMATIC,
                CloudFieldAtlasModel.qualityForSteps(33)
        );
        assertEquals(6, CloudFieldAtlasModel.QualityPreset.BALANCED.fieldSpacingBlocks());
        assertEquals(4, CloudFieldAtlasModel.QualityPreset.BALANCED.lightingSteps());
    }

    @Test
    void layoutStaysAnchoredUntilCameraCrossesPaddedRecenterBoundary() {
        CloudFieldAtlasModel.Layout layout = CloudFieldAtlasModel.layout(
                0.0, 0.0, 384, 1024, 48, 24, true
        );

        assertFalse(CloudFieldAtlasModel.shouldRecenter(layout, 23.99, -23.99));
        assertTrue(CloudFieldAtlasModel.shouldRecenter(layout, 24.0, 0.0));
        CloudFieldAtlasModel.Layout moved = CloudFieldAtlasModel.layout(
                24.0, 0.0, 384, 1024, 48, 24, true
        );
        assertEquals(24, moved.centerBlockX());
        assertEquals(0, moved.centerBlockZ());
    }

    @Test
    void nearAndDistantBandRowsNeverOverlap() {
        CloudFieldAtlasModel.Layout layout = CloudFieldAtlasModel.layout(
                18.0, -7.0, 384, 1024, 48, 24, true
        );
        int lastNearRow = CloudFieldAtlasModel.atlasRow(
                layout,
                false,
                CloudFieldAtlasModel.BAND_COUNT - 1,
                layout.nearDimension() - 1
        );
        int firstDistantRow = CloudFieldAtlasModel.atlasRow(layout, true, 0, 0);
        int lastDistantRow = CloudFieldAtlasModel.atlasRow(
                layout,
                true,
                CloudFieldAtlasModel.BAND_COUNT - 1,
                layout.distantDimension() - 1
        );
        int lastMorphologyRow = CloudFieldAtlasModel.morphologyAtlasRow(
                layout,
                true,
                CloudFieldAtlasModel.BAND_COUNT - 1,
                layout.distantDimension() - 1
        );

        assertEquals(layout.distantRowOffset() - 1, lastNearRow);
        assertEquals(layout.distantRowOffset(), firstDistantRow);
        assertEquals(layout.morphologyRowOffset() - 1, lastDistantRow);
        assertEquals(layout.atlasHeight() - 1, lastMorphologyRow);
        assertTrue(layout.atlasWidth() >= layout.nearDimension());
        assertTrue(layout.atlasWidth() >= layout.distantDimension());
        assertEquals(0, Math.floorMod(layout.distantOriginBlockX(), layout.distantSpacingBlocks()));
        assertEquals(0, Math.floorMod(layout.distantOriginBlockZ(), layout.distantSpacingBlocks()));
    }

    @Test
    void disabledDistantFieldUsesOnlyFourNearBandRows() {
        CloudFieldAtlasModel.Layout layout = CloudFieldAtlasModel.layout(
                0.0, 0.0, 384, 1024, 48, 24, false
        );

        assertFalse(layout.hasDistantField());
        assertEquals(0, layout.distantDimension());
        assertEquals(
                layout.nearDimension() * CloudFieldAtlasModel.BAND_COUNT * 2,
                layout.atlasHeight()
        );
    }

    @Test
    void packedPixelRoundTripsAllShaderChannelsWithinOneByte() {
        int packed = CloudFieldAtlasModel.packPixel(0.63, 71.0, 83.0, 0.42);
        CloudFieldAtlasModel.DecodedPixel decoded = CloudFieldAtlasModel.decodePixel(packed);

        assertEquals(0.63, decoded.coverage(), 1.0 / 255.0);
        assertEquals(71.0, decoded.baseOffset(), 176.0 / 255.0);
        assertEquals(83.0, decoded.depth(), 128.0 / 255.0);
        assertEquals(0.42, decoded.storm(), 1.0 / 255.0);
    }

    @Test
    void morphologyPixelsKeepFourCloudFamiliesInIndependentChannels() {
        assertEquals(0x000000FF, CloudFieldAtlasModel.packMorphologyPixel(CloudType.Shape.WISPY));
        assertEquals(0x0000FF00, CloudFieldAtlasModel.packMorphologyPixel(CloudType.Shape.LAYERED));
        assertEquals(0x00FF0000, CloudFieldAtlasModel.packMorphologyPixel(CloudType.Shape.CELLULAR));
        assertEquals(0xFF000000, CloudFieldAtlasModel.packMorphologyPixel(CloudType.Shape.CONVECTIVE));
        assertEquals(0, CloudFieldAtlasModel.packMorphologyPixel(CloudType.Shape.CLEAR));
    }
}
