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

/** Exercises whitewater breakup through real production fragment-shader pixels. */
@Tag("gpu")
class WaterFoamPatternGpuTest {

    private static final int SIZE = 128;
    private static long window;
    private static int program;
    private static int target;
    private static int targetTexture;
    private static int vertexArray;
    private static final int[] textures = new int[3];
    private static String fragment;

    @BeforeAll
    static void openRenderTarget() throws IOException {
        assumeTrue(glfwInit(), "A desktop OpenGL context is required for foam pixels");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(4, 4, "Water foam regression", 0L, 0L);
        assumeTrue(window != 0L, "OpenGL 3.2 is unavailable for foam pixels");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("Water foam regression: " + glGetString(GL_RENDERER)
                + " / " + glGetString(GL_VERSION));
        try (var input = WaterFoamPatternGpuTest.class.getClassLoader().getResourceAsStream(
                "assets/wildernessodysseyapi/shaders/core/gerstner_water.fsh")) {
            if (input == null) throw new IOException("Missing production water fragment shader");
            fragment = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        target = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, target);
        targetTexture = texture(new float[4], SIZE, SIZE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, targetTexture, 0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
        vertexArray = glGenVertexArrays();
        glBindVertexArray(vertexArray);
        glViewport(0, 0, SIZE, SIZE);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_DITHER);
        float[][] samples = {{0.1f, 0.5f, 0.7f, 1}, {0.05f, 0.16f, 0.24f, 1}, {1, 1, 1, 1}};
        for (int unit = 0; unit < textures.length; unit++) {
            glActiveTexture(GL_TEXTURE0 + unit);
            textures[unit] = texture(samples[unit], 1, 1);
        }
        useFragment(fragment);
    }

    @Test
    void resolvedWhitewaterDoesNotRepeatAnExactCheckerLattice() {
        Frame first = render(0, 0, 0, 0, 24, 1, 0);
        // One exact period of the old sin(k1.x) * cos(k2.x) lattice:
        // k1=(2.73,1.91), k2=(-1.37,3.17), k1.v=2pi and k2.v=0.
        double determinant = 2.73 * 3.17 + 1.91 * 1.37;
        Frame translated = render(0, 0, (float) (Math.PI * 2 * 3.17 / determinant),
                (float) (Math.PI * 2 * 1.37 / determinant), 24, 1, 0);
        double correlation = correlation(first, translated);
        System.out.println("Resolved foam range=" + first.range() + ", lattice correlation=" + correlation);
        assertTrue(first.range() > 0.04, "Eligible whitewater must retain visible irregular detail");
        assertTrue(correlation < 0.85, "Whitewater repeats the old exact checker lattice: " + correlation);
    }

    @Test
    void unresolvedWhitewaterConvergesWithoutAliasingOrDisappearing() {
        try {
            for (int quality = 0; quality <= 3; quality++) {
                glUniform4f(uniform("OpticalQuality"), quality, 0, 0, 0);
                Frame resolved = render(0, 0, 0, 0, 24, 1, 0);
                Frame distant = render(0, 0, 0, 0, 1536, 1, 0);
                Frame noCrest = render(0, 0, 0, 0, 1536, 0, 0);
                System.out.println("Quality " + quality + " distant foam deviation=" + distant.deviation()
                        + ", mean=" + distant.mean() + ", resolved mean=" + resolved.mean());
                assertTrue(distant.deviation() < 0.005,
                        "Subpixel foam detail must integrate instead of producing distant bands: " + distant.deviation());
                assertEquals(resolved.mean(), distant.mean(), 0.04, "Filtering must retain average whitewater coverage");
                assertTrue(distant.mean() - noCrest.mean() > 0.12,
                        "Removing all distant foam is not an antialiasing solution");
            }
        } finally {
            glUniform4f(uniform("OpticalQuality"), 2, 0, 0, 0);
        }
    }

    @Test
    void breakupSharesChunkEdgesAtNegativeAndWorldBorderOrigins() {
        for (float chunk : new float[]{0, 1023, -1025, 1_874_999, -1_875_000}) {
            Frame west = render(chunk, chunk, 16, 3, 2, 1, 0);
            Frame east = render(chunk + 1, chunk, 0, 3, 2, 1, 0);
            for (int i = 0; i < west.pixels.length; i++) {
                assertEquals(west.pixels[i], east.pixels[i], 0.00002,
                        "Chunk-origin disagreement at " + chunk + ", channel " + i);
            }
        }
    }

    @Test
    void reducedAnimationPhaseWrapDoesNotJumpThePattern() {
        try {
            for (int axis = 0; axis < 2; axis++) {
                float end = (float) (Math.PI * 2 - 0.00001);
                glUniform4f(uniform("SurfaceAnimationPhases1"), 0.3f, 0.7f,
                        axis == 0 ? end : 1.1f, axis == 1 ? end : 0.4f);
                Frame before = render(0, 0, 0, 0, 24, 1, 0);
                glUniform4f(uniform("SurfaceAnimationPhases1"), 0.3f, 0.7f,
                        axis == 0 ? 0.00001f : 1.1f, axis == 1 ? 0.00001f : 0.4f);
                Frame after = render(0, 0, 0, 0, 24, 1, 0);
                for (int i = 0; i < before.pixels.length; i++) {
                    assertEquals(before.pixels[i], after.pixels[i], 0.0001,
                            "Reduced animation phase wrapped discontinuously");
                }
            }
        } finally {
            glUniform4f(uniform("SurfaceAnimationPhases1"), 0.3f, 0.7f, 1.1f, 0.4f);
        }
    }

    @Test
    void frozenWaterSuppressesWhitewaterContrast() {
        Frame liquid = render(0, 0, 0, 0, 24, 1, 0);
        Frame frozen = render(0, 0, 0, 0, 24, 1, 1);
        System.out.println("Liquid range=" + liquid.range() + ", frozen range=" + frozen.range());
        // The existing material attenuates foam by 88% at full freeze. The
        // original shader retains about 10.3% pixel contrast after composition.
        assertTrue(frozen.range() < liquid.range() * 0.12,
                "Fully frozen water must not retain liquid whitewater contrast");
    }

    @Test
    void eligibleWhitewaterDriftsWithReducedAnimationPhases() {
        Frame before = render(0, 0, 0, 0, 24, 1, 0);
        try {
            glUniform4f(uniform("SurfaceAnimationPhases1"), 0.3f, 0.7f, 1.6f, 0.9f);
            Frame after = render(0, 0, 0, 0, 24, 1, 0);
            assertTrue(correlation(before, after) < 0.98, "The whitewater mask stopped animating");
        } finally {
            glUniform4f(uniform("SurfaceAnimationPhases1"), 0.3f, 0.7f, 1.1f, 0.4f);
        }
    }

    @Test
    void subBlockBreakupRemainsSmoothNearTheWorldBorder() {
        for (float chunk : new float[]{1_874_999, -1_875_000}) {
            Frame frame = render(chunk, chunk, 0, 0, 4, 1, 0);
            assertTrue(frame.range() > 0.04, "World-border breakup lost its sub-block detail");
            for (int y = 0; y < SIZE; y++) {
                for (int x = 1; x < SIZE; x++) {
                    int pixel = (x + y * SIZE) * 4;
                    assertTrue(Math.abs(frame.pixels[pixel] - frame.pixels[pixel - 4]) < 0.05,
                            "World-border float quantization introduced a hard pixel step");
                }
            }
        }
    }

    private static Frame render(float chunkX, float chunkZ, float localX, float localZ,
                                float span, float crest, float frozen) {
        glUniform2f(uniform("FixtureChunk"), chunkX, chunkZ);
        glUniform2f(uniform("FixtureLocal"), localX, localZ);
        glUniform1f(uniform("FixtureSpan"), span);
        glUniform1f(uniform("FixtureCrest"), crest);
        glUniform4f(uniform("Weather"), 0, 0, 0.5f, frozen);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        float[] pixels = new float[SIZE * SIZE * 4];
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_FLOAT, pixels);
        assertEquals(GL_NO_ERROR, glGetError(), "Foam fixture produced an OpenGL error");
        return new Frame(pixels);
    }

    private static void useFragment(String source) {
        if (program != 0) glDeleteProgram(program);
        int vertex = compile(GL_VERTEX_SHADER, fixtureVertex(source));
        int pixel = compile(GL_FRAGMENT_SHADER, source);
        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, pixel);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(pixel);
        assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
        glUseProgram(program);
        String[] samplers = {"Sampler0", "SceneColor", "SceneDepth"};
        for (int unit = 0; unit < samplers.length; unit++) glUniform1i(uniform(samplers[unit]), unit);
        float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        for (String matrix : new String[]{"ModelViewMat", "ProjMat", "InverseProjMat"}) {
            glUniformMatrix4fv(uniform(matrix), false, identity);
        }
        glUniform4f(uniform("ColorModulator"), 1, 1, 1, 1);
        glUniform4f(uniform("EnvironmentColor"), 0.35f, 0.55f, 0.75f, 1);
        glUniform4f(uniform("OpticalQuality"), 2, 0, 0, 0);
        glUniform2f(uniform("ScreenSize"), SIZE, SIZE);
        glUniform1f(uniform("FogEnd"), 1000);
        glUniform1f(uniform("SurfaceOpacityStrength"), 1);
        glUniform1f(uniform("SceneCaptureValid"), 1);
        glUniform3f(uniform("AbsorptionCoefficients"), 0.082f, 0.030f, 0.014f);
        glUniform4f(uniform("SurfaceAnimationPhases1"), 0.3f, 0.7f, 1.1f, 0.4f);
    }

    private static String fixtureVertex(String source) {
        Map<String, String> inputs = Map.ofEntries(
                Map.entry("vertexColor", "vec4(0.04,0.30,0.40,0.16)"),
                Map.entry("lightColor", "vec3(1.0)"),
                Map.entry("viewPosition", "vec3(0.0,-5.0,-7.0)"),
                Map.entry("worldNormal", "vec3(0.0,1.0,0.0)"),
                Map.entry("celestialDirection", "vec3(0.0,1.0,0.0)"),
                Map.entry("waterBodyBlend", "vec3(1.0,0.0,0.0)"),
                Map.entry("depthFactor", "1.0"),
                Map.entry("coastalCrest", "FixtureCrest"),
                Map.entry("phaseLocalXZ", "FixtureLocal + uv * FixtureSpan"),
                Map.entry("phaseChunkIndex", "FixtureChunk"),
                Map.entry("regionalSeaState", "0.5")
        );
        StringBuilder declarations = new StringBuilder("#version 150\n"
                + "uniform vec2 FixtureChunk;\nuniform vec2 FixtureLocal;\n"
                + "uniform float FixtureSpan;\nuniform float FixtureCrest;\n");
        StringBuilder assignments = new StringBuilder();
        var matcher = Pattern.compile("(?m)^(flat )?in (\\w+) (\\w+);").matcher(source);
        while (matcher.find()) {
            declarations.append(matcher.group(1) == null ? "" : matcher.group(1))
                    .append("out ").append(matcher.group(2)).append(' ').append(matcher.group(3)).append(";\n");
            assignments.append(matcher.group(3)).append(" = ")
                    .append(inputs.getOrDefault(matcher.group(3), matcher.group(2) + "(0.0)")).append(";\n");
        }
        return declarations + "void main() {\n"
                + "vec2 p[3] = vec2[3](vec2(-1,-1),vec2(3,-1),vec2(-1,3));\n"
                + "gl_Position = vec4(p[gl_VertexID],0,1);\n"
                + "vec2 uv = p[gl_VertexID] * 0.5 + 0.5;\n" + assignments + "}\n";
    }

    private static double correlation(Frame a, Frame b) {
        double covariance = 0;
        double meanA = a.mean();
        double meanB = b.mean();
        for (int i = 0; i < a.pixels.length; i += 4) covariance += (a.pixels[i] - meanA) * (b.pixels[i] - meanB);
        return covariance / (SIZE * SIZE * Math.max(1.0e-12, a.deviation() * b.deviation()));
    }

    private record Frame(float[] pixels) {
        double mean() {
            double sum = 0;
            for (int i = 0; i < pixels.length; i += 4) sum += pixels[i];
            return sum / (SIZE * SIZE);
        }
        double deviation() {
            double mean = mean();
            double sum = 0;
            for (int i = 0; i < pixels.length; i += 4) sum += Math.pow(pixels[i] - mean, 2);
            return Math.sqrt(sum / (SIZE * SIZE));
        }
        double range() {
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < pixels.length; i += 4) {
                minimum = Math.min(minimum, pixels[i]);
                maximum = Math.max(maximum, pixels[i]);
            }
            return maximum - minimum;
        }
    }

    private static int uniform(String name) { return glGetUniformLocation(program, name); }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }

    private static int texture(float[] pixels, int width, int height) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT,
                width == 1 && height == 1 ? pixels : null);
        return texture;
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
