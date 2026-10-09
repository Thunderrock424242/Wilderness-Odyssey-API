package com.thunder.wildernessodysseyapi.mixinconfig;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies optional integration mixins are gated independently by class resources. */
class WildernessMixinConfigPluginTest {

    private static final String PACKAGE = "com.thunder.wildernessodysseyapi.mixin.";

    @Test
    void performanceHooksCanBeRemovedAtStartupWithoutDisablingGameplayMixins() {
        String previous = System.getProperty("wilderness.perf.instrumentation");
        try {
            System.setProperty("wilderness.perf.instrumentation", "false");
            assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                    PACKAGE + "PerformanceSaveDiagnosticsMixin", ignored -> { throw new AssertionError("Target loaded"); }));
            assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                    PACKAGE + "PerformanceChunkWaitDiagnosticsMixin", ignored -> { throw new AssertionError("Target loaded"); }));
            assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(PACKAGE + "BoatRenderMixin", ignored -> false));
            System.clearProperty("wilderness.perf.instrumentation");
            assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(PACKAGE + "PerformanceSaveDiagnosticsMixin", ignored -> false));
            assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(PACKAGE + "PerformanceChunkWaitDiagnosticsMixin", ignored -> false));
        } finally {
            if (previous == null) System.clearProperty("wilderness.perf.instrumentation");
            else System.setProperty("wilderness.perf.instrumentation", previous);
        }
    }

    @Test
    void sableWaterBridgeIsSkippedWhenSableIsAbsent() {
        assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SableWaterShapeMixin", ignored -> false));
    }

    @Test
    void missingOptionalTargetsAreSkippedWithoutAClassLoad() {
        assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "ForwardExtentCopyMixin", ignored -> false));
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
        assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SodiumChunkMeshingHandoffMixin", ignored -> false));
        assertFalse(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SodiumSectionUploadHandoffMixin", ignored -> false));
    }

    @Test
    void presentOptionalAndAllNormalMixinsRemainEnabled() {
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "ForwardExtentCopyMixin",
                "com.sk89q.worldedit.function.operation.ForwardExtentCopy"::equals));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "IrisWaterMaterialBridgeMixin", ignored -> true));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "EmbeddiumWaterRenderMixin", ignored -> true));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SodiumFluidRenderMixin", ignored -> true));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SodiumBlockOcclusionCacheMixin", ignored -> true));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SodiumChunkMeshingHandoffMixin", ignored -> true));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "SodiumSectionUploadHandoffMixin", ignored -> true));
        assertTrue(WildernessMixinConfigPlugin.shouldApplyOptionalMixin(
                PACKAGE + "BoatRenderMixin", ignored -> false));
    }

    @Test
    void embeddiumMixinIsOnlyParsedWhenItsLegacyTargetExists() {
        assertEquals(List.of(), WildernessMixinConfigPlugin.discoverOptionalMixins(ignored -> false));
        assertEquals(
                List.of(
                        "EmbeddiumWaterRenderMixin",
                        "EmbeddiumRenderSectionCoordinatesMixin",
                        "EmbeddiumChunkBuildOutputHandoffMixin",
                        "EmbeddiumChunkMeshingHandoffMixin",
                        "EmbeddiumSectionUploadHandoffMixin"
                ),
                WildernessMixinConfigPlugin.discoverOptionalMixins(name -> name.equals(
                        "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.FluidRenderer"))
        );
    }
}
