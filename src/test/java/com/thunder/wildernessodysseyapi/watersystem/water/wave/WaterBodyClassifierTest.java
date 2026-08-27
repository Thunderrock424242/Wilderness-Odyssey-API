package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WaterBodyClassifierTest {

    @Test
    void generatedMetadataWinsBeforeFallbackShapeGuessing() {
        assertAll(
                () -> assertEquals(
                        WaterBodyClassifier.WaterType.OCEAN,
                        WaterBodyClassifier.mapGeneratedType(GeneratedWaterChunk.BodyType.OCEAN, false)
                ),
                () -> assertEquals(
                        WaterBodyClassifier.WaterType.COAST,
                        WaterBodyClassifier.mapGeneratedType(GeneratedWaterChunk.BodyType.OCEAN, true)
                ),
                () -> assertEquals(
                        WaterBodyClassifier.WaterType.RIVER,
                        WaterBodyClassifier.mapGeneratedType(GeneratedWaterChunk.BodyType.RIVER, true)
                ),
                () -> assertEquals(
                        WaterBodyClassifier.WaterType.LAKE,
                        WaterBodyClassifier.mapGeneratedType(GeneratedWaterChunk.BodyType.LAKE, false)
                ),
                () -> assertEquals(
                        WaterBodyClassifier.WaterType.POND,
                        WaterBodyClassifier.mapGeneratedType(GeneratedWaterChunk.BodyType.SPRING, false)
                )
        );
    }

    @Test
    void boundedFallbackSeparatesOceanCoastRiverLakeAndPond() {
        assertAll(
                () -> assertEquals(
                        WaterBodyClassifier.WaterType.OCEAN,
                        WaterBodyClassifier.classifyFallback(true, false, false, false, 60, 18, 18)
                ),
                () -> assertEquals(
                        WaterBodyClassifier.WaterType.COAST,
                        WaterBodyClassifier.classifyFallback(true, false, true, true, 40, 18, 10)
                ),
                () -> assertEquals(
                        WaterBodyClassifier.WaterType.RIVER,
                        WaterBodyClassifier.classifyFallback(false, false, false, false, 18, 18, 6)
                ),
                () -> assertEquals(
                        WaterBodyClassifier.WaterType.LAKE,
                        WaterBodyClassifier.classifyFallback(false, false, false, false, 45, 16, 14)
                ),
                () -> assertEquals(
                        WaterBodyClassifier.WaterType.POND,
                        WaterBodyClassifier.classifyFallback(false, false, false, true, 8, 6, 6)
                )
        );
    }
}
