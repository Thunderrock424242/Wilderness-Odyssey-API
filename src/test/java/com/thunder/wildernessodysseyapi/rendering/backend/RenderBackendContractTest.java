package com.thunder.wildernessodysseyapi.rendering.backend;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class RenderBackendContractTest {

    @Test
    void unavailableBackendKeepsOptionalScopesAndSamplesInert() {
        assertSame(RenderBackend.RenderStateScope.UNAVAILABLE,
                RenderBackend.UNAVAILABLE.captureRenderStateScope());
        assertFalse(RenderBackend.UNAVAILABLE.createGpuTimer(4).available());
        assertEquals(Optional.empty(), RenderBackend.UNAVAILABLE.createGpuTimer(4).poll());
        assertDoesNotThrow(RenderBackend.UNAVAILABLE::close);
    }

    @Test
    void timingSampleSanitizesInvalidExternalValues() {
        RenderBackend.GpuTimingSample sample = new RenderBackend.GpuTimingSample(-4L, -7L, -12L);

        assertEquals(0L, sample.sequence());
        assertEquals(-1L, sample.sourceFrame());
        assertEquals(0L, sample.durationNanos());
    }

    @Test
    void backendSelectionNormalizesDiagnosticMetadata() {
        RenderBackends.Selection selection = new RenderBackends.Selection(
                null,
                -3L,
                new RenderBackends.BackendStatus(null, -2, " ")
        );

        assertSame(RenderBackend.UNAVAILABLE, selection.backend());
        assertEquals(0L, selection.generation());
        assertEquals(RenderBackends.State.UNDISCOVERED, selection.status().state());
        assertEquals(0, selection.status().discoveryAttempts());
        assertEquals("No backend detail available", selection.status().detail());
    }
}
