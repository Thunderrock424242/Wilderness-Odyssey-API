package com.thunder.wildernessodysseyapi.mixinconfig;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the Minecraft 1.21.1 render-section hooks used by the upload mixin. */
class VanillaSectionUploadHandoffContractTest {

    /** Guards against accidentally authoring the mixin against a newer renderer API. */
    @Test
    void targetsNeoForge121RenderSectionContract() throws ReflectiveOperationException {
        Method origin = SectionRenderDispatcher.RenderSection.class
                .getDeclaredMethod("getOrigin");
        Method compiled = SectionRenderDispatcher.RenderSection.class
                .getDeclaredMethod(
                        "setCompiled",
                        SectionRenderDispatcher.CompiledSection.class);

        assertEquals(BlockPos.class, origin.getReturnType());
        assertEquals(void.class, compiled.getReturnType());
    }
}
