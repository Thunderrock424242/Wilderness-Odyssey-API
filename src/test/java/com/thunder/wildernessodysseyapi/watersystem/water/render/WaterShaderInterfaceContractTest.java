package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates complete JSON-to-GLSL interfaces for both built-in water programs. */
class WaterShaderInterfaceContractTest {

    private static final String SHADER_ROOT =
            "assets/wildernessodysseyapi/shaders/core/";
    private static final Pattern UNIFORM = Pattern.compile(
            "(?m)^\\s*uniform\\s+(\\w+)\\s+([A-Za-z_]\\w*)\\s*;");

    @Test
    void surfaceDescriptorExactlyMatchesBothShaderStages() throws IOException {
        assertCompleteInterface("gerstner_water");
    }

    @Test
    void underwaterDescriptorExactlyMatchesBothShaderStages() throws IOException {
        assertCompleteInterface("underwater_optics");
    }

    private static void assertCompleteInterface(String program) throws IOException {
        String descriptorText = readResource(SHADER_ROOT + program + ".json");
        String vertex = readResource(SHADER_ROOT + program + ".vsh");
        String fragment = readResource(SHADER_ROOT + program + ".fsh");
        JsonObject descriptor = JsonParser.parseString(descriptorText).getAsJsonObject();

        Set<String> declaredUniforms = new TreeSet<>();
        Set<String> declaredSamplers = new TreeSet<>();
        collectShaderDeclarations(vertex, declaredUniforms, declaredSamplers);
        collectShaderDeclarations(fragment, declaredUniforms, declaredSamplers);

        Set<String> describedUniforms = names(descriptor.getAsJsonArray("uniforms"));
        Set<String> describedSamplers = names(descriptor.getAsJsonArray("samplers"));
        assertEquals(declaredUniforms, describedUniforms,
                program + " JSON uniforms drifted from GLSL declarations");
        assertEquals(declaredSamplers, describedSamplers,
                program + " JSON samplers drifted from GLSL declarations");
        assertTrue(vertex.startsWith("#version 150"));
        assertTrue(fragment.startsWith("#version 150"));
    }

    private static void collectShaderDeclarations(
            String shader,
            Set<String> uniforms,
            Set<String> samplers
    ) {
        Matcher matcher = UNIFORM.matcher(shader);
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            if (type.startsWith("sampler")) {
                samplers.add(name);
            } else {
                uniforms.add(name);
            }
        }
    }

    private static Set<String> names(JsonArray declarations) {
        Set<String> names = new TreeSet<>();
        for (var declaration : declarations) {
            names.add(declaration.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = WaterShaderInterfaceContractTest.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing shader resource " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
