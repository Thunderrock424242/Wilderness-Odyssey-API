package com.thunder.wildernessodysseyapi.wage.gpu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Opt-in driver acceptance in a private context. This does not access a Minecraft
 * world or context, measure FPS, or replace visual acceptance in the target pack.
 */
@EnabledIfSystemProperty(named = "wildernessodysseyapi.gpuTests", matches = "true")
class CloudShaderGpuTest {
    private static final int SIZE = 16;
    private static final double PIXEL_TOLERANCE = 0.00002;

    @Test
    void packagedCloudShaderCompilesAndPreservesFixturePixels() throws Exception {
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err).set();
        long window = NULL;
        boolean contextReady = false;
        Fixture fixture = new Fixture();
        try {
            assertTrue(glfwInit(), "GLFW must initialize; run this task on a machine with a display driver");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            window = glfwCreateWindow(SIZE, SIZE, "WAGE shader acceptance", NULL, NULL);
            assertNotEquals(NULL, window, "OpenGL 3.2 core context must be available");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            contextReady = true;
            System.out.printf("WAGE GPU: vendor=%s renderer=%s version=%s%n",
                    glGetString(GL_VENDOR), glGetString(GL_RENDERER), glGetString(GL_VERSION));
            fixture.initialize();
            int fixtures = 0;
            int visiblePixels = 0;
            double maximumError = 0;
            for (int genus = 0; genus < 4; genus++) {
                for (float coverage : new float[] {0.5f, 1.0f}) {
                    fixture.atlas(genus, coverage);
                    for (float strength : new float[] {0, 0.65f, 1}) {
                        glUniform1f(fixture.uniform("DetailStrength"), strength);
                        glUniform1f(fixture.uniform("GameTime"), genus * 13.7f);
                        glUniform2f(fixture.uniform("WindOffset"), genus * 23.1f, -genus * 7.3f);
                        float[] original = fixture.render(false);
                        float[] bounded = fixture.render(true);
                        for (int channel = 0; channel < original.length; channel++) {
                            assertTrue(Float.isFinite(original[channel]) && Float.isFinite(bounded[channel]),
                                    "Shader must produce finite pixels");
                            maximumError = Math.max(maximumError, Math.abs(original[channel] - bounded[channel]));
                            if (channel % 4 == 3 && original[channel] > 0) visiblePixels++;
                        }
                        assertTrue(maximumError <= PIXEL_TOLERANCE,
                                "Cloud density paths differ: genus=" + genus + " coverage=" + coverage
                                        + " detail=" + strength + " maximum channel error=" + maximumError);
                        assertEquals(GL_NO_ERROR, glGetError(), "Fixture must render without GL errors");
                        glfwPollEvents();
                        fixtures++;
                    }
                }
            }
            assertTrue(visiblePixels > SIZE * SIZE, "Fixtures must include visible clouds, not only discarded pixels");
            System.out.printf("WAGE GPU fixtures=%d visiblePixels=%d maxChannelError=%.9f tolerance=%.9f%n",
                    fixtures, visiblePixels, maximumError, PIXEL_TOLERANCE);
            int matrixFixtures = 0;
            for (int layer = 0; layer < 8; layer++) {
                int layerPixels = 0;
                for (int genus = 0; genus < 4; genus++) {
                    for (float coverage : new float[] {0.5f, 1}) {
                        fixture.atlas(genus, coverage);
                        for (float blend : new float[] {0, 0.5f, 1}) {
                            fixture.scenario(layer, blend, layer % 2 == 0 ? 0 : 0.75f);
                            for (float strength : new float[] {0, 0.65f, 1}) {
                                glUniform1f(fixture.uniform("DetailStrength"), strength);
                                float[] original = fixture.render(false);
                                float[] bounded = fixture.render(true);
                                layerPixels += compare(original, bounded, "layer=" + layer
                                        + " genus=" + genus + " blend=" + blend + " detail=" + strength);
                                matrixFixtures++;
                            }
                        }
                    }
                }
                assertTrue(layerPixels > 0, "Each near/distant altitude band must draw visible clouds: " + layer);
            }
            System.out.printf("WAGE GPU matrixFixtures=%d bands=8 atlasBlends=3 lightning=on/off%n", matrixFixtures);

            fixture.resize(32);
            fixture.scenario(0, 0.5f, 0.75f);
            fixture.atlas(3, 1);
            compare(fixture.render(false), fixture.render(true), "resized framebuffer");
            fixture.close();
            fixture = new Fixture();
            fixture.initialize();
            fixture.atlas(3, 1);
            compare(fixture.render(false), fixture.render(true), "recreated shader/resources");
            assertEquals(GL_NO_ERROR, glGetError());

            fixture.close();
            GL.setCapabilities(null);
            contextReady = false;
            glfwMakeContextCurrent(NULL);
            glfwDestroyWindow(window);
            window = glfwCreateWindow(SIZE, SIZE, "WAGE context replacement", NULL, NULL);
            assertNotEquals(NULL, window);
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            contextReady = true;
            fixture = new Fixture();
            fixture.initialize();
            fixture.atlas(3, 1);
            compare(fixture.render(false), fixture.render(true), "replacement GL context");
            System.out.println("WAGE GPU lifecycle=resize,shader-resource-recreation,context-replacement passed");
            benchmark(fixture);
        } finally {
            if (window != NULL) {
                if (contextReady) {
                    fixture.close();
                    GL.setCapabilities(null);
                }
                glfwMakeContextCurrent(NULL);
                glfwDestroyWindow(window);
            }
            glfwTerminate();
            glfwSetErrorCallback(null);
            errors.free();
        }
    }

    private static int compare(float[] original, float[] bounded, String scenario) {
        assertEquals(original.length, bounded.length);
        int visible = 0;
        for (int channel = 0; channel < original.length; channel++) {
            assertTrue(Float.isFinite(original[channel]) && Float.isFinite(bounded[channel]), scenario);
            assertEquals(original[channel], bounded[channel], PIXEL_TOLERANCE, scenario + " channel=" + channel);
            if (channel % 4 == 3 && original[channel] > 0) visible++;
        }
        assertEquals(GL_NO_ERROR, glGetError(), scenario);
        return visible;
    }

    /** Reports a controlled shader microbenchmark, never game FPS or a pass/fail speed target. */
    private static void benchmark(Fixture fixture) throws InterruptedException {
        assertTrue(GL.getCapabilities().GL_ARB_timer_query, "Acceptance benchmark requires timestamp queries");
        fixture.resize(128);
        fixture.scenario(0, 0.5f, 0);
        fixture.atlas(2, 0.5f);
        glUniform1f(fixture.uniform("DetailStrength"), 1);
        long[] baseline = new long[30];
        long[] optimized = new long[30];
        for (int sample = -10; sample < baseline.length; sample++) {
            boolean optimizedFirst = (sample & 1) != 0;
            long first = timedBatch(fixture, optimizedFirst);
            long second = timedBatch(fixture, !optimizedFirst);
            if (sample >= 0) {
                baseline[sample] = optimizedFirst ? second : first;
                optimized[sample] = optimizedFirst ? first : second;
            }
        }
        Arrays.sort(baseline);
        Arrays.sort(optimized);
        System.out.printf("WAGE GPU microbenchmark size=128x128 drawsPerBatch=4 pairs=30 warmupPairs=10 "
                        + "baselineMedianMs=%.6f optimizedMedianMs=%.6f baselineP95Ms=%.6f optimizedP95Ms=%.6f%n",
                baseline[14] / 1e6, optimized[14] / 1e6, baseline[28] / 1e6, optimized[28] / 1e6);
    }

    private static long timedBatch(Fixture fixture, boolean bounded) throws InterruptedException {
        int start = glGenQueries();
        int end = glGenQueries();
        try {
            org.lwjgl.opengl.ARBTimerQuery.glQueryCounter(start, org.lwjgl.opengl.ARBTimerQuery.GL_TIMESTAMP);
            for (int draw = 0; draw < 4; draw++) fixture.draw(bounded);
            org.lwjgl.opengl.ARBTimerQuery.glQueryCounter(end, org.lwjgl.opengl.ARBTimerQuery.GL_TIMESTAMP);
            glFlush();
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (glGetQueryObjecti(end, GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) {
                assertTrue(System.nanoTime() < deadline, "GPU query acceptance deadline exceeded");
                Thread.sleep(1);
            }
            long nanos = org.lwjgl.opengl.ARBTimerQuery.glGetQueryObjectui64(end, GL_QUERY_RESULT)
                    - org.lwjgl.opengl.ARBTimerQuery.glGetQueryObjectui64(start, GL_QUERY_RESULT);
            assertTrue(nanos > 0, "Timestamp intervals must be positive");
            assertEquals(GL_NO_ERROR, glGetError());
            return nanos;
        } finally {
            glDeleteQueries(start);
            glDeleteQueries(end);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private int program;
        private int vertexShader;
        private int fragmentShader;
        private int vao;
        private int vbo;
        private int framebuffer;
        private int colorTexture;
        private int atlasTexture;
        private int previousAtlasTexture;
        private int size = SIZE;

        void initialize() throws Exception {
            vertexShader = compile(GL_VERTEX_SHADER, "raymarched_clouds.vsh");
            fragmentShader = compile(GL_FRAGMENT_SHADER, "raymarched_clouds.fsh");
            program = glCreateProgram();
            glAttachShader(program, vertexShader);
            glAttachShader(program, fragmentShader);
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            glUseProgram(program);

            vao = glGenVertexArrays();
            vbo = glGenBuffers();
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, new float[] {
                    -64, -16, -64, 1, 1, 1, 0.0625f,
                     64, -16, -64, 1, 1, 1, 0.0625f,
                     64, -16,  64, 1, 1, 1, 0.0625f,
                    -64, -16, -64, 1, 1, 1, 0.0625f,
                     64, -16,  64, 1, 1, 1, 0.0625f,
                    -64, -16,  64, 1, 1, 1, 0.0625f
            }, GL_STATIC_DRAW);
            int position = glGetAttribLocation(program, "Position");
            int color = glGetAttribLocation(program, "Color");
            assertTrue(position >= 0 && color >= 0, "Cloud carrier attributes must be active");
            glEnableVertexAttribArray(position);
            glVertexAttribPointer(position, 3, GL_FLOAT, false, 7 * Float.BYTES, 0L);
            glEnableVertexAttribArray(color);
            glVertexAttribPointer(color, 4, GL_FLOAT, false, 7 * Float.BYTES, 3L * Float.BYTES);

            framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            colorTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, colorTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, SIZE, SIZE, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            glViewport(0, 0, SIZE, SIZE);
            glDisable(GL_BLEND);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);

            atlasTexture = glGenTextures();
            previousAtlasTexture = glGenTextures();
            for (int unit = 0; unit < 2; unit++) {
                glActiveTexture(GL_TEXTURE0 + unit);
                glBindTexture(GL_TEXTURE_2D, unit == 0 ? previousAtlasTexture : atlasTexture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            }
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glUniform1i(uniform("CloudFieldPrevious"), 0);
            glUniform1i(uniform("CloudFieldCurrent"), 1);
            glUniform4f(uniform("ColorModulator"), 1, 1, 1, 1);
            glUniform2f(uniform("RenderOrigin"), 0, 0);
            glUniform3f(uniform("CameraPosition"), 0, -48, 0);
            glUniform4f(uniform("PreviousNearField"), -128, -128, 128, 4);
            glUniform4f(uniform("CurrentNearField"), -128, -128, 128, 4);
            glUniform4f(uniform("PreviousDistantField"), -1024, -1024, 1024, 4);
            glUniform4f(uniform("CurrentDistantField"), -1024, -1024, 1024, 4);
            glUniform2f(uniform("FieldTextureSize"), 4, 64);
            glUniform1f(uniform("FieldBlend"), 1);
            glUniform1f(uniform("NearRadius"), 384);
            glUniform1f(uniform("DistantRadius"), 1024);
            glUniform1i(uniform("RaymarchSteps"), 64);
            glUniform1i(uniform("LightingSteps"), 4);
            glUniform3f(uniform("CloudColor"), 0.8f, 0.85f, 0.9f);
            glUniform3f(uniform("SunDirection"), 0.3f, 1, 0.2f);
            glUniform3f(uniform("LightningPosition"), 0, 0, 0);
            glUniform1f(uniform("LightningIllumination"), 0);
            float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
            float[] projection = new float[16];
            projection[0] = projection[9] = 1.0f / 64;
            projection[15] = 1;
            glUniformMatrix4fv(uniform("ModelViewMat"), false, identity);
            glUniformMatrix4fv(uniform("ProjMat"), false, projection);
            assertTrue(uniform("DensityBoundsEnabled") >= 0);
        }

        void atlas(int genus, float coverage) {
            uploadAtlas(previousAtlasTexture, genus, coverage * 0.75f);
            uploadAtlas(atlasTexture, genus, coverage);
        }

        private void uploadAtlas(int texture, int genus, float coverage) {
            ByteBuffer data = ByteBuffer.allocateDirect(4 * 64 * 4);
            for (int row = 0; row < 64; row++) {
                for (int column = 0; column < 4; column++) {
                    if (row < 32) {
                        int bottom = new int[] {-16, 16, 56, -16}[(row / 4) % 4];
                        int base = Math.round((bottom + 32) / 176.0f * 255);
                        data.put((byte) Math.round(coverage * 255)).put((byte) base).put((byte) 64).put((byte) 128);
                    } else {
                        for (int channel = 0; channel < 4; channel++) data.put((byte) (channel == genus ? 255 : 0));
                    }
                }
            }
            data.flip();
            glActiveTexture(texture == previousAtlasTexture ? GL_TEXTURE0 : GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 4, 64, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        }

        float[] render(boolean bounded) {
            draw(bounded);
            float[] pixels = new float[size * size * 4];
            // Synchronous readback is confined to this explicit acceptance test.
            glReadPixels(0, 0, size, size, GL_RGBA, GL_FLOAT, pixels);
            return pixels;
        }

        void draw(boolean bounded) {
            glUniform1i(uniform("DensityBoundsEnabled"), bounded ? 1 : 0);
            glClearColor(0, 0, 0, 0);
            glClear(GL_COLOR_BUFFER_BIT);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }

        void resize(int nextSize) {
            size = nextSize;
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, colorTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, size, size, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
            glViewport(0, 0, size, size);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
        }

        void scenario(int layer, float blend, float lightning) {
            int bottom = new int[] {-16, 16, 56, -16}[layer % 4];
            float center = layer < 4 ? 0 : 512;
            float alpha = (layer + 0.5f) / 8;
            float[] vertices = new float[6 * 7];
            int vertex = 0;
            for (int corner : new int[] {0, 1, 2, 0, 2, 3}) {
                vertices[vertex++] = center + (corner == 0 || corner == 3 ? -64 : 64);
                vertices[vertex++] = bottom;
                vertices[vertex++] = corner < 2 ? -64 : 64;
                vertices[vertex++] = 1;
                vertices[vertex++] = 1;
                vertices[vertex++] = 1;
                vertices[vertex++] = alpha;
            }
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
            float[] projection = new float[16];
            projection[0] = projection[9] = 1.0f / 64;
            projection[12] = -center / 64;
            projection[15] = 1;
            glUniformMatrix4fv(uniform("ProjMat"), false, projection);
            glUniform3f(uniform("CameraPosition"), 0, bottom - 32, 0);
            glUniform1f(uniform("FieldBlend"), blend);
            glUniform1f(uniform("LightningIllumination"), lightning);
            glUniform3f(uniform("LightningPosition"), center, bottom + 16, 0);
        }

        int uniform(String name) {
            int location = glGetUniformLocation(program, name);
            assertTrue(location >= 0, "Required cloud uniform must be active: " + name);
            return location;
        }

        @Override
        public void close() {
            glUseProgram(0);
            glBindVertexArray(0);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glDeleteTextures(atlasTexture);
            glDeleteTextures(previousAtlasTexture);
            glDeleteTextures(colorTexture);
            glDeleteFramebuffers(framebuffer);
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
            glDeleteProgram(program);
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
        }
    }

    private static int compile(int type, String name) throws Exception {
        int shader = glCreateShader(type);
        try (var input = CloudShaderGpuTest.class.getResourceAsStream(
                "/assets/wildernessodysseyapi/shaders/core/" + name)) {
            assertNotNull(input, "Packaged shader must exist: " + name);
            glShaderSource(shader, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            glCompileShader(shader);
            assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), name + ": " + glGetShaderInfoLog(shader));
            return shader;
        } catch (Exception | AssertionError failure) {
            glDeleteShader(shader);
            throw failure;
        }
    }
}
