package com.thunder.wildernessodysseyapi.mixinconfig;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies optional renderer mixins are gated independently by class resources. */
class WildernessMixinConfigPluginTest {

    private static final String PACKAGE = "com.thunder.wildernessodysseyapi.mixin.";

    @Test
    void missingOptionalTargetsAreSkippedWithoutAClassLoad() {
        assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "IrisWaterMaterialBridgeMixin", ignored -> false));
        assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "LegacyIrisWaterMaterialBridgeMixin", ignored -> false));
        assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "EmbeddiumWaterRenderMixin", ignored -> false));
        assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SodiumFluidRenderMixin", ignored -> false));
        assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SodiumBlockOcclusionCacheMixin", ignored -> false));
    }

    @Test
    void presentOptionalAndAllNormalMixinsRemainEnabled() {
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "IrisWaterMaterialBridgeMixin", ignored -> true));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "EmbeddiumWaterRenderMixin", ignored -> true));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SodiumFluidRenderMixin", ignored -> true));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SodiumBlockOcclusionCacheMixin", ignored -> true));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "BoatRenderMixin", ignored -> false));
    }

    @Test
    void embeddiumMixinIsOnlyParsedWhenItsLegacyTargetExists() {
        assertEquals(List.of(), WildernessMixinConfigPlugin.discoverOptionalMixins(ignored -> false));
        assertEquals(
                List.of("EmbeddiumWaterRenderMixin"),
                WildernessMixinConfigPlugin.discoverOptionalMixins(name -> name.equals(
                        "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.FluidRenderer"))
        );
    }
}
