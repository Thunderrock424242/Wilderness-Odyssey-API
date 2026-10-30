package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Captures the production vertex shader's output rather than reimplementing GLSL in Java. */
@Tag("gpu")
class WaterSurfaceVertexGpuTest {

    private static final long GAME_TIME = 80L;
    private static final float PARTIAL_TICK = 0.25f;
    private static long window;
    private static int program;
    private static int vertexArray;
    private static int feedbackBuffer;

    @BeforeAll
    static void openShader() throws IOException {
        assumeTrue(glfwInit(), "A desktop OpenGL context is required for vertex feedback");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(4, 4, "Water vertex regression", 0L, 0L);
        assumeTrue(window != 0L, "OpenGL 3.2 is unavailable for vertex feedback");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("Water vertex regression: " + glGetString(GL_RENDERER)
                + " / " + glGetString(GL_VERSION));

        // Minecraft's import processor removes the include's version directive
        // before insertion. Preserve the production program's GLSL 150 header.
        String light = resource("assets/minecraft/shaders/include/light.glsl")
                .replaceFirst("(?m)^#version[^\\r\\n]*", "");
        String source = resource("assets/wildernessodysseyapi/shaders/core/gerstner_water.vsh")
                .replace("#moj_import <light.glsl>", light);
        int shader = glCreateShader(GL_VERTEX_SHADER);
        try {
            glShaderSource(shader, source);
            glCompileShader(shader);
            assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
            program = glCreateProgram();
            glAttachShader(program, shader);
            glTransformFeedbackVaryings(program, new CharSequence[] {"viewPosition", "worldNormal", "depthFactor"},
                    GL_INTERLEAVED_ATTRIBS);
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
        } finally {
            glDeleteShader(shader);
        }
        glUseProgram(program);
        vertexArray = glGenVertexArrays();
        glBindVertexArray(vertexArray);
        feedbackBuffer = glGenBuffers();
        glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, feedbackBuffer);
        glBufferData(GL_TRANSFORM_FEEDBACK_BUFFER, 7L * Float.BYTES, GL_STREAM_READ);
        glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, feedbackBuffer);
        float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        glUniformMatrix4fv(uniform("ModelViewMat"), false, identity);
        glUniformMatrix4fv(uniform("ProjMat"), false, identity);
        glUniform1f(uniform("GpuWaveStrength"), 1.0f);
        glUniform2f(uniform("WindDirection"), 1.0f, 0.0f);
        glEnable(GL_RASTERIZER_DISCARD);
    }

    @Test
    void actualShaderMatchesCpuShoalingFreezeAndContinuity() {
        int comparisons = 0;
        for (WaveSpectrumState spectrum : new WaveSpectrumState[] {
                WaveSpectrumState.NEUTRAL, new WaveSpectrumState(1.6f, 2.1f, 1, 0, 0.75f)}) {
            waves(spectrum, true);
            for (float depth : new float[] {0, 0.5f, 1.5f, 3, 6, 10, 16, 24}) {
                for (float frozen : new float[] {0, 0.5f, 1}) {
                    for (float continuity : new float[] {0, 0.5f, 1}) {
                        int color = WaterSurfaceVertexData.encodeColor(0xFFFFFFFF, 0, 0, 0, depth);
                        float[] actual = sample(11.25f, 7.5f, color, frozen, continuity, 0.2f);
                        assertEquals(depth / 24, actual[6], 0.000001f, "Decoded GPU depth " + depth);
                        float expected = WaterSurfaceEquation.snapshotSurfaceHeight(63, 11.25, 7.5,
                                WaterAnimationClock.sampleTick(GAME_TIME, PARTIAL_TICK) / 20.0,
                                spectrum, WaveSpectrumState.NEUTRAL, 4, 0, 0,
                                1, 0, 0, 0, 0, continuity, 0.2f, 0, depth, frozen);
                        assertEquals(expected, actual[1], 0.0001f,
                                "depth=" + depth + ", frozen=" + frozen + ", continuity=" + continuity);
                        float normalLength = actual[3] * actual[3] + actual[4] * actual[4]
                                + actual[5] * actual[5];
                        assertEquals(1.0f, normalLength, 0.0001f);
                        comparisons++;
                    }
                }
            }
        }
        assertEquals(144, comparisons);
        System.out.println("Water CPU/GPU height comparisons: " + comparisons);
    }

    @Test
    void arbitraryDepthsUseTheSameQuantizedSurfaceOnCpuAndGpu() {
        waves(WaveSpectrumState.NEUTRAL, true);
        float[][] cases = {{0.1f, 0}, {0.8f, 0.5f}, {2.2f, 1.5f}, {4.4f, 3},
                {7.9f, 6}, {12.9f, 10}, {19.9f, 16}, {200, 24}};
        for (float[] depthCase : cases) {
            float depth = depthCase[0];
            int color = WaterSurfaceVertexData.encodeColor(0xFFFFFFFF, 0, 0, 0, depth);
            float quantizedDepth = depthCase[1];
            float[] actual = sample(11.25f, 7.5f, color, 0, 1, 0);
            assertEquals(quantizedDepth / 24, actual[6], 0.000001f);
            float expected = WaterSurfaceEquation.snapshotSurfaceHeight(63, 11.25, 7.5,
                    WaterAnimationClock.sampleTick(GAME_TIME, PARTIAL_TICK) / 20.0,
                    WaveSpectrumState.NEUTRAL, WaveSpectrumState.NEUTRAL, 4, 0, 0,
                    1, 0, 0, 0, 0, 1, 0, 0, quantizedDepth, 0);
            assertEquals(expected, actual[1], 0.0001f, "Input depth " + depth);
        }
    }

    @Test
    void actualShaderDampsImpulsesWithoutDampingTide() {
        waves(WaveSpectrumState.NEUTRAL, false);
        glUniform1f(uniform("ImpulseCount"), 1);
        glUniform4f(uniform("ImpulsePosition0"), 11.25f, 7.5f, 1.2f, 0.1f);
        glUniform4f(uniform("ImpulseShape0"), 0.4f, 0.2f, 0.75f, 1.0f);
        try {
            for (float frozen : new float[] {0, 0.5f, 1}) {
                float actual = sample(11.25f, 7.5f, 0xFFFFFFFF, frozen, 1, 0.2f)[1];
                float impulse = WaterSurfaceDisplacement.sampleImpulse(0, 1.2f, 0.1f, 0.4f, 0.2f, 0.75f);
                float expected = WaterSurfaceEquation.snapshotSurfaceHeight(63, 11.25, 7.5, 4,
                        WaveSpectrumState.NEUTRAL, WaveSpectrumState.NEUTRAL, 0, 0, 0,
                        1, 0, 0, 0, 0, 1, 0.2f, impulse, 24, frozen);
                assertEquals(expected, actual, 0.0001f);
            }
        } finally {
            glUniform1f(uniform("ImpulseCount"), 0);
        }
    }

    @Test
    void actualShaderSharesChunkEdgesNearAndFarFromOrigin() {
        waves(new WaveSpectrumState(1.6f, 2.1f, 1, 0, 0.75f), true);
        try {
            for (float origin : new float[] {0, 16_368, -16_384, 29_999_984, -30_000_000}) {
                glUniform2f(uniform("ChunkOrigin"), origin, origin);
                float[] west = sample(16, 7.5f, 0xFFFFFFFF, 0.5f, 1, 0.2f);
                glUniform2f(uniform("ChunkOrigin"), origin + 16, origin);
                float[] east = sample(0, 7.5f, 0xFFFFFFFF, 0.5f, 1, 0.2f);
                assertEquals(west[1], east[1], 0.0001f, "Surface edge at " + origin);
                for (int axis = 3; axis < 6; axis++) {
                    assertEquals(west[axis], east[axis], 0.0001f, "Normal edge at " + origin);
                }
            }
        } finally {
            glUniform2f(uniform("ChunkOrigin"), 0, 0);
        }
    }

    private static void waves(WaveSpectrumState spectrum, boolean enabled) {
        glUniform4f(uniform("SpectrumState"), spectrum.swellScale(), spectrum.chopScale(),
                spectrum.directionBlend(), 0);
        GerstnerWaveProfile profile = GerstnerWaveProfile.OCEAN;
        for (int index = 0; index < 4; index++) {
            glUniform4f(uniform("OceanWaveParam" + index), profile.dirX[index], profile.dirZ[index],
                    profile.waveNumber[index], profile.angularFrequency[index]);
            glUniform4f(uniform("OceanWaveShape" + index), enabled ? profile.amplitude[index] : 0,
                    profile.phaseOffset[index] - WaterAnimationClock.stablePhase(
                            GAME_TIME, PARTIAL_TICK, profile.angularFrequency[index]),
                    profile.steepness[index], index / 3.0f);
        }
    }

    private static float[] sample(float x, float z, int color, float frozen, float continuity, float tide) {
        glVertexAttrib3f(glGetAttribLocation(program, "Position"), x, 63, z);
        glVertexAttrib3f(glGetAttribLocation(program, "Normal"), continuity, 0, 0);
        glVertexAttrib4f(glGetAttribLocation(program, "Color"),
                ((color >>> 16) & 255) / 255.0f, ((color >>> 8) & 255) / 255.0f,
                (color & 255) / 255.0f, ((color >>> 24) & 255) / 255.0f);
        glUniform4f(uniform("Weather"), 0, 0, 1, frozen);
        glUniform1f(uniform("TideOffset"), tide);
        glBeginTransformFeedback(GL_POINTS);
        glDrawArrays(GL_POINTS, 0, 1);
        glEndTransformFeedback();
        float[] output = new float[7];
        glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER, 0, output);
        assertEquals(GL_NO_ERROR, glGetError(), "Vertex feedback produced an OpenGL error");
        return output;
    }

    private static int uniform(String name) {
        int location = glGetUniformLocation(program, name);
        assertTrue(location >= 0, "Missing active shader uniform " + name);
        return location;
    }

    private static String resource(String path) throws IOException {
        try (var input = WaterSurfaceVertexGpuTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing shader resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AfterAll
    static void closeShader() {
        if (window != 0L) {
            glDisable(GL_RASTERIZER_DISCARD);
            glDeleteBuffers(feedbackBuffer);
            glDeleteVertexArrays(vertexArray);
            glDeleteProgram(program);
            GL.setCapabilities(null);
            glfwMakeContextCurrent(0L);
            glfwDestroyWindow(window);
        }
        glfwTerminate();
    }
}
