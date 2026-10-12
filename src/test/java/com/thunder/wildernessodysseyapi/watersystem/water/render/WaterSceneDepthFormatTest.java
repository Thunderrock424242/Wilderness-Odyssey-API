package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterSceneCapture.DepthFormat;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.*;

class WaterSceneDepthFormatTest {

    @Test
    void packedDepthStencilKeepsItsStorageAndUploadType() {
        DepthFormat format = DepthFormat.forInternalFormat(GL30.GL_DEPTH32F_STENCIL8);

        assertNotNull(format);
        assertEquals(GL30.GL_DEPTH32F_STENCIL8, format.internalFormat());
        assertEquals(GL30.GL_DEPTH_STENCIL, format.pixelFormat());
        assertEquals(GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV, format.pixelType());
    }

    @Test
    void normalizedDepthStencilDoesNotBecomeFloatingPointDepth() {
        DepthFormat format = DepthFormat.forInternalFormat(GL30.GL_DEPTH24_STENCIL8);

        assertNotNull(format);
        assertEquals(GL30.GL_DEPTH24_STENCIL8, format.internalFormat());
        assertEquals(GL30.GL_DEPTH_STENCIL, format.pixelFormat());
        assertEquals(GL30.GL_UNSIGNED_INT_24_8, format.pixelType());
    }

    @Test
    void ordinaryDepthKeepsTheSourcesPrecision() {
        DepthFormat format = DepthFormat.forInternalFormat(GL14.GL_DEPTH_COMPONENT24);

        assertNotNull(format);
        assertEquals(GL14.GL_DEPTH_COMPONENT24, format.internalFormat());
        assertEquals(GL11.GL_DEPTH_COMPONENT, format.pixelFormat());
        assertEquals(GL11.GL_UNSIGNED_INT, format.pixelType());
    }

    @Test
    void refusesTheOriginalPackedToPlainDepthCopy() {
        assertFalse(DepthFormat.canBlit(
                GL30.GL_DEPTH32F_STENCIL8, GL14.GL_DEPTH_COMPONENT24, 0));
    }

    @Test
    void refusesSamePrecisionWithDifferentStencilStorage() {
        assertFalse(DepthFormat.canBlit(
                GL30.GL_DEPTH24_STENCIL8, GL14.GL_DEPTH_COMPONENT24, 0));
    }

    @Test
    void refusesFixedPointToFloatingPointDepth() {
        assertFalse(DepthFormat.canBlit(
                GL14.GL_DEPTH_COMPONENT32, GL30.GL_DEPTH_COMPONENT32F, 0));
    }

    @Test
    void acceptsMatchingSingleSampleDepthFormats() {
        for (int format : new int[]{GL11.GL_DEPTH_COMPONENT, GL14.GL_DEPTH_COMPONENT16,
                GL14.GL_DEPTH_COMPONENT24, GL14.GL_DEPTH_COMPONENT32, GL30.GL_DEPTH_COMPONENT32F,
                GL30.GL_DEPTH24_STENCIL8, GL30.GL_DEPTH32F_STENCIL8}) {
            assertNotNull(DepthFormat.forInternalFormat(format));
            assertTrue(DepthFormat.canBlit(format, format, 0));
        }
    }

    @Test
    void unknownOrMissingDepthCannotBePublishedAsACapture() {
        assertNull(DepthFormat.forInternalFormat(GL11.GL_RGBA8));
        assertFalse(DepthFormat.canBlit(GL11.GL_RGBA8, GL11.GL_RGBA8, 0));
        assertFalse(DepthFormat.canBlit(0, 0, 0));
    }

    @Test
    void multisampledSourceUsesTheOpticalFallback() {
        assertFalse(DepthFormat.canBlit(
                GL30.GL_DEPTH32F_STENCIL8, GL30.GL_DEPTH32F_STENCIL8, 4));
    }
}
