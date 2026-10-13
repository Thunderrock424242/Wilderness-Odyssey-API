package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Renders the real fragment shader into a tiny hidden target; no Minecraft world is required. */
@Tag("gpu")
class WaterSurfaceCoverageRenderTest {

    private static final float[] BACKGROUND = {0.60f, 0.40f, 0.20f, 0.0f};
    private static final int[][] NARROW_SHAPES = {{1, 1}, {1, 8}, {8, 1}};
    private static long window;
    private static int program;
    private static int target;
    private static int targetTexture;
    private static int vertexArray;
    private static final int[] textures = new int[3];

    @BeforeAll
    static void openRenderTarget() throws IOException {
        assumeTrue(glfwInit(), "A desktop OpenGL context is required for the pixel regression");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(4, 4, "Water coverage regression", 0L, 0L);
        assumeTrue(window != 0L, "OpenGL 3.2 is unavailable for the pixel regression");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("Water pixel regression: " + glGetString(GL_RENDERER)
                + " / " + glGetString(GL_VERSION));

        String fragment;
        try (var input = WaterSurfaceCoverageRenderTest.class.getClassLoader().getResourceAsStream(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.fsh")) {
            if (input == null) {
                throw new IOException("Missing production water fragment shader");
            }
            fragment = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int vertexShader = compile(GL_VERTEX_SHADER, fixtureVertex(fragment));
        int fragmentShader = compile(GL_FRAGMENT_SHADER, fragment);
        program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
        glUseProgram(program);

        target = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, target);
        targetTexture = texture(new float[4], 4, 4);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, targetTexture, 0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
        vertexArray = glGenVertexArrays();
        glBindVertexArray(vertexArray);
        glViewport(0, 0, 4, 4);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_DITHER);

        float[][] samples = {{0.1f, 0.5f, 0.7f, 1.0f}, BACKGROUND, {1, 1, 1, 1}};
        String[] samplers = {"Sampler0", "SceneColor", "SceneDepth"};
        for (int unit = 0; unit < textures.length; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            textures[unit] = texture(samples[unit], 1, 1);
            glUniform1i(glGetUniformLocation(program, samplers[unit]), unit);
        }
        float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        for (String matrix : new String[]{"ModelViewMat", "ProjMat", "InverseProjMat"}) {
            glUniformMatrix4fv(glGetUniformLocation(program, matrix), false, identity);
        }
        glUniform4f(glGetUniformLocation(program, "ColorModulator"), 1, 1, 1, 1);
        glUniform4f(glGetUniformLocation(program, "Weather"), 0, 0, 1, 0);
        glUniform4f(glGetUniformLocation(program, "EnvironmentColor"), 0.35f, 0.55f, 0.75f, 1);
        glUniform2f(glGetUniformLocation(program, "ScreenSize"), 4, 4);
        glUniform1f(glGetUniformLocation(program, "FogEnd"), 1000);
        glUniform1f(glGetUniformLocation(program, "SurfaceOpacityStrength"), 1);
        glUniform3f(glGetUniformLocation(program, "AbsorptionCoefficients"), 0.082f, 0.030f, 0.014f);
    }

    @Test
    void capturedWaterRemainsVisibleInOneBlockPoolsAndRows() {
        for (int[] shape : NARROW_SHAPES) {
            float[] pixel = render(narrowContinuity(shape[0], shape[1]), true);
            float difference = Math.max(Math.abs(pixel[0] - BACKGROUND[0]),
                    Math.max(Math.abs(pixel[1] - BACKGROUND[1]), Math.abs(pixel[2] - BACKGROUND[2])));
            assertTrue(difference > 0.02f,
                    "Owned " + shape[0] + "x" + shape[1] + " water only reproduced its dry background: "
                            + difference);
            assertEquals(1.0f, pixel[3], 0.0001f);
        }
    }

    @Test
    void noCaptureFallbackKeepsItsMaterialOpacityInNarrowRows() {
        for (int[] shape : NARROW_SHAPES) {
            float[] pixel = render(narrowContinuity(shape[0], shape[1]), false);
            assertTrue(pixel[3] >= 0.09f,
                    "Wave damping reduced " + shape[0] + "x" + shape[1]
                            + " water below the material's minimum opacity: " + pixel[3]);
        }
    }

    @Test
    void openWaterControlProducesAVisibleCapturedSurface() {
        float continuity = WaterChunkMeshCache.displacementContinuityAt(8, 8, (x, z) -> true);
        float[] pixel = render(continuity, true);
        assertTrue(Math.abs(pixel[0] - BACKGROUND[0]) > 0.02f);
        assertEquals(1.0f, pixel[3], 0.0001f);
    }

    @Test
    void foregroundTerrainStillOccludesPinnedWater() {
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, textures[2]);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL_RGBA, GL_FLOAT,
                new float[]{0.25f, 0.25f, 0.25f, 1.0f});
        try {
            float[] pixel = render(narrowContinuity(1, 8), true);
            for (int channel = 0; channel < 4; channel++) {
                assertEquals(BACKGROUND[channel], pixel[channel], 0.0001f,
                        "Foreground terrain must keep the water behind it hidden");
            }
        } finally {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL_RGBA, GL_FLOAT,
                    new float[]{1.0f, 1.0f, 1.0f, 1.0f});
        }
    }

    private static float narrowContinuity(int width, int length) {
        float continuity = 0;
        for (int z = 0; z <= length; z++) {
            for (int x = 0; x <= width; x++) {
                continuity = WaterChunkMeshCache.displacementContinuityAt(x, z,
                        (columnX, columnZ) -> columnX >= 0 && columnX < width
                                && columnZ >= 0 && columnZ < length);
                assertEquals(0.0f, WaterSurfaceEquation.surfaceContinuityFactor(continuity), 0.0f,
                        "Narrow contained water should retain its pinned wave boundary");
            }
        }
        return continuity;
    }

    private static float[] render(float continuity, boolean captured) {
        glUniform1f(glGetUniformLocation(program, "FixtureContinuity"), continuity);
        glUniform1f(glGetUniformLocation(program, "SceneCaptureValid"), captured ? 1 : 0);
        glClearColor(BACKGROUND[0], BACKGROUND[1], BACKGROUND[2], BACKGROUND[3]);
        glClear(GL_COLOR_BUFFER_BIT);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        float[] pixel = new float[4];
        glReadPixels(1, 1, 1, 1, GL_RGBA, GL_FLOAT, pixel);
        assertEquals(GL_NO_ERROR, glGetError(), "Water pixel fixture produced an OpenGL error");
        return pixel;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }

    private static int texture(float[] rgba, int width, int height) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        // The target is cleared before drawing; only sampled 1x1 textures need initial pixels.
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT,
                width == 1 && height == 1 ? rgba : null);
        return texture;
    }

    private static String fixtureVertex(String fragment) {
        Map<String, String> inputs = Map.ofEntries(
                Map.entry("vertexColor", "vec4(0.04, 0.30, 0.40, 0.16)"),
                Map.entry("texCoord0", "vec2(0.5)"),
                Map.entry("lightColor", "vec3(1.0)"),
                Map.entry("viewPosition", "vec3(0.0, -2.0, -5.0)"),
                Map.entry("viewNormal", "vec3(0.0, 1.0, 0.0)"),
                Map.entry("worldNormal", "vec3(0.0, 1.0, 0.0)"),
                Map.entry("celestialDirection", "vec3(0.0, 1.0, 0.0)"),
                Map.entry("waterBodyBlend", "vec3(0.0, 0.0, 1.0)"),
                Map.entry("surfaceContinuity", "FixtureContinuity"),
                Map.entry("depthFactor", "0.04"),
                Map.entry("regionalWindDirection", "vec2(1.0, 0.0)")
        );
        StringBuilder declarations = new StringBuilder("#version 150\nuniform float FixtureContinuity;\n");
        StringBuilder assignments = new StringBuilder();
        var matcher = Pattern.compile("(?m)^(flat )?in (\\w+) (\\w+);").matcher(fragment);
        while (matcher.find()) {
            String qualifier = matcher.group(1) == null ? "" : matcher.group(1);
            String type = matcher.group(2);
            String name = matcher.group(3);
            declarations.append(qualifier).append("out ").append(type).append(' ').append(name).append(";\n");
            assignments.append(name).append(" = ")
                    .append(inputs.getOrDefault(name, type + "(0.0)")).append(";\n");
        }
        return declarations + "void main() {\n"
                + "vec2 positions[3] = vec2[3](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));\n"
                + "gl_Position = vec4(positions[gl_VertexID], 0.0, 1.0);\n" + assignments + "}\n";
    }

    @AfterAll
    static void closeRenderTarget() {
        if (window != 0L) {
            glDeleteProgram(program);
            glDeleteFramebuffers(target);
            glDeleteTextures(targetTexture);
            glDeleteTextures(textures);
            glDeleteVertexArrays(vertexArray);
            GL.setCapabilities(null);
            glfwMakeContextCurrent(0L);
            glfwDestroyWindow(window);
        }
        glfwTerminate();
    }
}
