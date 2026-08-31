package com.thunder.wildernessodysseyapi.rendering;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenGlDeprecationBoundaryTest {

    private static final Path SOURCE_ROOT = Path.of(System.getProperty(
            "wildernessodysseyapi.projectDir",
            System.getProperty("user.dir")
    )).resolve(Path.of("src", "main", "java"));
    private static final Set<String> DIRECT_OPENGL_OWNERS = Set.of(
            "com/thunder/wildernessodysseyapi/gpuprofiler/client/GpuDiagnostics.java",
            "com/thunder/wildernessodysseyapi/gpuprofiler/client/GpuMemoryProbe.java",
            "com/thunder/wildernessodysseyapi/gpuprofiler/client/GpuProfiler.java",
            "com/thunder/wildernessodysseyapi/mixin/StructureBlockRendererMixin.java",
            "com/thunder/wildernessodysseyapi/rendering/backend/opengl/OpenGlGpuTimer.java",
            "com/thunder/wildernessodysseyapi/rendering/backend/opengl/OpenGlRenderBackend.java",
            "com/thunder/wildernessodysseyapi/watersystem/water/render/WaterSceneCapture.java"
    );
    private static final Set<String> LEGACY_OPENGL_MIXINS = Set.of(
            "com/thunder/wildernessodysseyapi/mixin/GlStateManagerGpuProfilerMixin.java",
            "com/thunder/wildernessodysseyapi/mixin/RenderSystemGpuDiagnosticsMixin.java",
            "com/thunder/wildernessodysseyapi/mixin/SimpleTextureGpuProfilerMixin.java",
            "com/thunder/wildernessodysseyapi/mixin/StructureBlockRendererMixin.java",
            "com/thunder/wildernessodysseyapi/mixin/TextureAtlasGpuProfilerMixin.java"
    );

    @Test
    void directOpenGlUsageCannotEscapeTheDeprecatedCompatibilityBoundary() throws IOException {
        Set<String> discovered = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("org.lwjgl.opengl.") || source.contains("GlStateManager._gl")) {
                    discovered.add(relative(file));
                }
            }
        }

        assertEquals(DIRECT_OPENGL_OWNERS, discovered,
                "Direct OpenGL use must remain inside the documented removal boundary");
        assertDeprecatedPackage("com/thunder/wildernessodysseyapi/rendering/backend/opengl/package-info.java");
        assertDeprecatedPackage("com/thunder/wildernessodysseyapi/gpuprofiler/client/package-info.java");
        for (String mixin : LEGACY_OPENGL_MIXINS) {
            assertTrue(Files.readString(SOURCE_ROOT.resolve(mixin)).contains("@Deprecated(forRemoval = true)"),
                    () -> mixin + " must remain visibly deprecated until its Vulkan replacement lands");
        }
        assertTrue(Files.readString(SOURCE_ROOT.resolve(
                        "com/thunder/wildernessodysseyapi/watersystem/water/render/WaterSceneCapture.java"))
                        .contains("@Deprecated(forRemoval = true)"),
                "The raw water framebuffer copy must remain visibly deprecated");
    }

    private static void assertDeprecatedPackage(String relativePath) throws IOException {
        assertTrue(Files.readString(SOURCE_ROOT.resolve(relativePath)).contains("@Deprecated(forRemoval = true)"),
                () -> relativePath + " must remain deprecated for the Vulkan migration");
    }

    private static String relative(Path file) {
        return SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
    }
}
