package com.thunder.wildernessodysseyapi.weather.client.cloud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the checked-in cloud shader retains cloud-family decoding. */
class VolumetricCloudShaderContractTest {

    @Test
    void fragmentShaderDecodesAltitudeAndMeteorologicalMorphology() throws IOException {
        Path project = Path.of(System.getProperty("wildernessodysseyapi.projectDir", "."));
        String shader = Files.readString(project.resolve(
                "src/main/resources/assets/wildernessodysseyapi/shaders/core/volumetric_clouds.fsh"
        ));

        assertTrue(shader.contains("packedLayer"));
        assertTrue(shader.contains("morphology"));
        assertTrue(shader.contains("wispyShape"));
        assertTrue(shader.contains("sheetShape"));
        assertTrue(shader.contains("towerShape"));
    }
}
